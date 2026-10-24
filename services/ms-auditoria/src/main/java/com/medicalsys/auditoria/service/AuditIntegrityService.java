package com.medicalsys.auditoria.service;

import com.medicalsys.auditoria.common.error.BusinessRuleException;
import com.medicalsys.auditoria.common.error.ErrorCode;
import com.medicalsys.auditoria.domain.model.AuditChainHead;
import com.medicalsys.auditoria.domain.model.AuditEvent;
import com.medicalsys.auditoria.repository.AuditChainHeadRepository;
import com.medicalsys.auditoria.repository.AuditEventRepository;
import com.medicalsys.auditoria.web.dto.AuditIntegrityResponse;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verificacion de la cadena de hashes.
 *
 * <p>Recorre un tramo de la bitacora en orden de asiento y comprueba tres cosas en cada paso: que
 * la secuencia no salte, que el {@code previousHash} apunte al hash real del evento anterior y que
 * el {@code recordHash} guardado coincida con el que se recalcula a partir del contenido. La
 * primera de las tres detecta borrados; la segunda, reordenamientos; la tercera, ediciones.
 *
 * <p>Se verifica por tramos y no la bitacora entera: en produccion la tabla crece sin limite y una
 * pantalla no puede quedarse esperando un recorrido completo. El tramo por defecto son los ultimos
 * eventos, que es donde una manipulacion reciente aparecera.
 */
@Service
public class AuditIntegrityService {

    /** Cuantos eventos se recalculan si el auditor no pide un tramo explicito. */
    public static final int DEFAULT_WINDOW = 500;

    /** Techo por peticion: recalcular es barato por evento, pero no gratis. */
    public static final int MAX_WINDOW = 5_000;

    private static final int PAGE_SIZE = 200;

    private final AuditEventRepository events;
    private final AuditChainHeadRepository chainHeads;
    private final AuditHashChain hashChain;
    private final Clock clock;

    public AuditIntegrityService(AuditEventRepository events, AuditChainHeadRepository chainHeads,
            AuditHashChain hashChain, Clock clock) {
        this.events = events;
        this.chainHeads = chainHeads;
        this.hashChain = hashChain;
        this.clock = clock;
    }

    /**
     * Verifica los ultimos {@code window} eventos de la organizacion en curso.
     *
     * @param window numero de eventos a recalcular; null aplica {@link #DEFAULT_WINDOW}
     */
    @Transactional(readOnly = true)
    public AuditIntegrityResponse verifyLatest(UUID requestedOrganizationId, Integer window) {
        UUID organizationId = AuditScope.resolveSingle(requestedOrganizationId);
        int size = resolveWindow(window);

        long lastSequence = chainHeads.peekByOrganizationId(organizationId)
                .map(AuditChainHead::getLastSequenceNumber)
                .orElse(0L);
        if (lastSequence == 0) {
            return new AuditIntegrityResponse(0, 0, 0, true, null, null,
                    "La organizacion todavia no tiene eventos asentados", clock.instant());
        }

        long fromSequence = Math.max(1, lastSequence - size + 1);
        return verifyRange(organizationId, fromSequence, lastSequence);
    }

    private AuditIntegrityResponse verifyRange(UUID organizationId, long fromSequence, long lastSequence) {
        // El eslabon anterior al tramo se necesita para comprobar el primer previousHash; si el
        // tramo arranca en 1 no hay predecesor y el previousHash correcto es null.
        String expectedPreviousHash = fromSequence == 1
                ? null
                : previousHashOf(organizationId, fromSequence - 1);
        long expectedSequence = fromSequence;
        long verified = 0;

        int pageNumber = 0;
        Page<AuditEvent> page;
        do {
            page = events.findByOrganizationIdAndSequenceNumberGreaterThanEqualOrderBySequenceNumberAsc(
                    organizationId, fromSequence, PageRequest.of(pageNumber, PAGE_SIZE));
            List<AuditEvent> chunk = page.getContent();
            for (AuditEvent event : chunk) {
                if (event.getSequenceNumber() != expectedSequence) {
                    return broken(fromSequence, lastSequence, verified, expectedSequence, event.getPublicId(),
                            "Falta el evento numero " + expectedSequence + " en la cadena");
                }
                if (!Objects.equals(event.getPreviousHash(), expectedPreviousHash)) {
                    return broken(fromSequence, lastSequence, verified, expectedSequence, event.getPublicId(),
                            "El evento no encadena con el asiento anterior");
                }
                if (!hashChain.hashOf(event).equals(event.getRecordHash())) {
                    return broken(fromSequence, lastSequence, verified, expectedSequence, event.getPublicId(),
                            "El contenido del evento no reproduce su propio hash");
                }
                expectedPreviousHash = event.getRecordHash();
                expectedSequence++;
                verified++;
            }
            pageNumber++;
        } while (page.hasNext());

        return new AuditIntegrityResponse(fromSequence, lastSequence, verified, true, null, null,
                "El tramo verificado encadena correctamente", clock.instant());
    }

    /**
     * Hash del eslabon inmediatamente anterior al tramo.
     *
     * <p>Se lee con la misma consulta paginada, pidiendo una sola fila desde esa posicion.
     */
    private String previousHashOf(UUID organizationId, long sequenceNumber) {
        return events.findByOrganizationIdAndSequenceNumberGreaterThanEqualOrderBySequenceNumberAsc(
                        organizationId, sequenceNumber, PageRequest.of(0, 1))
                .getContent().stream()
                .findFirst()
                .map(AuditEvent::getRecordHash)
                .orElse(null);
    }

    private AuditIntegrityResponse broken(long fromSequence, long lastSequence, long verified,
            long brokenAt, UUID brokenEventId, String detail) {
        return new AuditIntegrityResponse(fromSequence, lastSequence, verified, false, brokenAt,
                brokenEventId, detail, clock.instant());
    }

    private int resolveWindow(Integer window) {
        if (window == null) {
            return DEFAULT_WINDOW;
        }
        if (window < 1 || window > MAX_WINDOW) {
            throw new BusinessRuleException(ErrorCode.INVALID_AUDIT_RANGE,
                    "El tramo a verificar debe estar entre 1 y " + MAX_WINDOW + " eventos");
        }
        return window;
    }
}
