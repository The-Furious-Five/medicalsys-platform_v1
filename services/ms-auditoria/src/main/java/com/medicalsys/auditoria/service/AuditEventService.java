package com.medicalsys.auditoria.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.medicalsys.auditoria.common.correlation.CorrelationId;
import com.medicalsys.auditoria.common.error.BusinessRuleException;
import com.medicalsys.auditoria.common.error.ErrorCode;
import com.medicalsys.auditoria.common.error.NotFoundException;
import com.medicalsys.auditoria.common.error.UnauthenticatedException;
import com.medicalsys.auditoria.common.tenant.RequestContext;
import com.medicalsys.auditoria.common.tenant.RequestContextHolder;
import com.medicalsys.auditoria.domain.model.AuditChainHead;
import com.medicalsys.auditoria.domain.model.AuditEvent;
import com.medicalsys.auditoria.repository.AuditChainHeadRepository;
import com.medicalsys.auditoria.repository.AuditEventRepository;
import com.medicalsys.auditoria.repository.AuditEventSpecifications;
import com.medicalsys.auditoria.web.dto.AuditEventFilter;
import com.medicalsys.auditoria.web.dto.AuditEventReceipt;
import com.medicalsys.auditoria.web.dto.AuditEventResponse;
import com.medicalsys.auditoria.web.dto.RecordAuditEventRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asiento y lectura de la bitacora.
 *
 * <p>Es el unico punto por el que entra un evento. No ofrece modificacion ni borrado: no es una
 * omision pendiente, es el contrato. Una bitacora que se puede corregir no sirve para demostrar
 * quien vio la historia clinica de un paciente.
 */
@Service
public class AuditEventService {

    private static final Logger log = LoggerFactory.getLogger(AuditEventService.class);

    /**
     * Tolerancia al desfase de reloj del productor.
     *
     * <p>Un evento fechado en el futuro deja la bitacora en un orden que no corresponde con la
     * realidad. Se acepta un margen pequeno para relojes de contenedor y se rechaza el resto.
     */
    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

    private static final int MAX_METADATA_LENGTH = 2000;

    private final AuditEventRepository events;
    private final AuditChainHeadRepository chainHeads;
    private final AuditHashChain hashChain;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditEventService(AuditEventRepository events, AuditChainHeadRepository chainHeads,
            AuditHashChain hashChain, ObjectMapper objectMapper, Clock clock) {
        this.events = events;
        this.chainHeads = chainHeads;
        this.hashChain = hashChain;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Asienta un evento y devuelve su acuse.
     *
     * <p>Es idempotente respecto de {@code eventId}: los productores envian la auditoria de forma
     * asincrona y con reintentos, asi que un segundo intento del mismo evento devuelve el acuse ya
     * emitido en lugar de duplicar la entrada. Solo se rechaza cuando el mismo identificador llega
     * describiendo un hecho distinto, que si es un error del productor y conviene que lo vea.
     */
    @Transactional
    public AuditEventReceipt record(RecordAuditEventRequest request) {
        UUID organizationId = resolveOrganizationId(request);
        UUID eventId = request.eventId() == null ? UUID.randomUUID() : request.eventId();

        Optional<AuditEvent> alreadyRecorded = events.findByOrganizationIdAndPublicId(organizationId, eventId);
        if (alreadyRecorded.isPresent()) {
            return acknowledgeRetry(alreadyRecorded.get(), request);
        }

        Instant recordedAt = truncate(clock.instant());
        Instant occurredAt = resolveOccurredAt(request, recordedAt);
        String metadataJson = serializeMetadata(request.metadata());
        String actorRoles = joinRoles(request.actorRoles());

        // El bloqueo serializa los asientos de esta organizacion. La primera vez no hay fila que
        // bloquear: si dos eventos inauguran la misma cadena a la vez, la clave primaria deja pasar
        // uno y el otro recibe 409 y reintenta, que es justo lo que hace el cliente de auditoria.
        AuditChainHead head = chainHeads.findByOrganizationId(organizationId)
                .orElseGet(() -> chainHeads.save(AuditChainHead.startFor(organizationId)));
        long sequenceNumber = head.getLastSequenceNumber() + 1;
        String previousHash = head.getLastRecordHash();

        String action = request.action().trim().toUpperCase(Locale.ROOT);
        String resourceType = request.resourceType().trim().toUpperCase(Locale.ROOT);
        String category = request.category().name();
        String severity = request.severityOrDefault().name();
        String outcome = request.outcomeOrDefault().name();
        String correlationId = CorrelationId.current();

        String recordHash = hashChain.hash(previousHash, eventId, organizationId, sequenceNumber,
                occurredAt, recordedAt, request.serviceName().trim(), action, category, severity, outcome,
                request.actorUserId(), trimToNull(request.actorDisplayName()), actorRoles,
                trimToNull(request.actorIp()), resourceType, request.resourcePublicId(),
                trimToNull(request.resourceLabel()), request.patientPublicId(), correlationId,
                trimToNull(request.requestMethod()), trimToNull(request.requestPath()),
                request.responseStatus(), trimToNull(request.reason()), metadataJson);

        AuditEvent event = new AuditEvent(eventId, organizationId, sequenceNumber, occurredAt, recordedAt,
                request.serviceName().trim(), action, request.category(), request.severityOrDefault(),
                request.outcomeOrDefault(), request.actorUserId(), trimToNull(request.actorDisplayName()),
                actorRoles, trimToNull(request.actorIp()), resourceType, request.resourcePublicId(),
                trimToNull(request.resourceLabel()), request.patientPublicId(), correlationId,
                trimToNull(request.requestMethod()), trimToNull(request.requestPath()),
                request.responseStatus(), trimToNull(request.reason()), metadataJson, previousHash,
                recordHash);

        AuditEvent saved = events.save(event);
        head.advanceTo(sequenceNumber, recordHash);
        return AuditEventReceipt.from(saved);
    }

    /**
     * Asienta un lote en una sola transaccion.
     *
     * <p>Los productores acumulan eventos cuando la bitacora estuvo inaccesible; enviarlos uno a uno
     * multiplicaria los bloqueos sobre la punta de la cadena de la organizacion.
     */
    @Transactional
    public List<AuditEventReceipt> recordAll(List<RecordAuditEventRequest> requests) {
        return requests.stream().map(this::record).toList();
    }

    @Transactional(readOnly = true)
    public AuditEventResponse get(UUID eventId) {
        UUID organizationId = AuditScope.resolve(null);
        return (organizationId == null ? events.findByPublicId(eventId)
                : events.findByOrganizationIdAndPublicId(organizationId, eventId))
                .map(AuditEventResponse::from)
                .orElseThrow(() -> new NotFoundException(ErrorCode.AUDIT_EVENT_NOT_FOUND,
                        "El evento de auditoria solicitado no existe en esta organizacion"));
    }

    @Transactional(readOnly = true)
    public Page<AuditEvent> search(AuditEventFilter filter, Pageable pageable) {
        UUID organizationId = AuditScope.resolve(filter.organizationId());
        validateRange(filter.from(), filter.to());
        return events.findAll(AuditEventSpecifications.of(organizationId, filter), pageable);
    }

    /** Reintento del mismo evento: se acepta si describe el mismo hecho, se rechaza si no. */
    private AuditEventReceipt acknowledgeRetry(AuditEvent existing, RecordAuditEventRequest request) {
        boolean sameFact = existing.getServiceName().equals(request.serviceName().trim())
                && existing.getAction().equals(request.action().trim().toUpperCase(Locale.ROOT))
                && existing.getResourceType().equals(request.resourceType().trim().toUpperCase(Locale.ROOT))
                && existing.getCategory() == request.category();
        if (!sameFact) {
            throw new BusinessRuleException(ErrorCode.AUDIT_EVENT_ALREADY_RECORDED,
                    "Ya existe un evento asentado con ese identificador y describe un hecho distinto");
        }
        log.debug("Reintento idempotente del evento {} de {}", existing.getPublicId(),
                existing.getServiceName());
        return AuditEventReceipt.from(existing);
    }

    /**
     * Tenant del evento.
     *
     * <p>Manda el contexto propagado por el gateway. El cuerpo solo se usa cuando no hay contexto,
     * que es el caso real de un intento de login fallido: ahi todavia no hay sesion, pero el evento
     * pertenece igual a una organizacion y perderlo dejaria ciego justo el ataque que interesa ver.
     */
    private UUID resolveOrganizationId(RecordAuditEventRequest request) {
        RequestContext context = RequestContextHolder.currentOrNull();
        if (context != null) {
            return context.organizationId();
        }
        if (request.organizationId() != null) {
            return request.organizationId();
        }
        throw new UnauthenticatedException(
                "El evento no indica organizacion ni llego con contexto de tenant propagado");
    }

    private Instant resolveOccurredAt(RecordAuditEventRequest request, Instant recordedAt) {
        if (request.occurredAt() == null) {
            return recordedAt;
        }
        Instant occurredAt = truncate(request.occurredAt());
        if (occurredAt.isAfter(recordedAt.plus(MAX_CLOCK_SKEW))) {
            throw new BusinessRuleException(ErrorCode.INVALID_AUDIT_PAYLOAD,
                    "La fecha declarada del evento esta demasiado adelantada respecto del servicio");
        }
        return occurredAt;
    }

    /**
     * Serializa los metadatos con claves ordenadas.
     *
     * <p>El orden importa porque el JSON forma parte del hash: si dos ejecuciones serializan el
     * mismo mapa en distinto orden, la verificacion de integridad denunciaria una manipulacion que
     * nunca ocurrio.
     */
    private String serializeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(new TreeMap<>(metadata));
            if (json.length() > MAX_METADATA_LENGTH) {
                throw new BusinessRuleException(ErrorCode.INVALID_AUDIT_PAYLOAD,
                        "El contexto adicional del evento supera el tamano admitido por la bitacora");
            }
            return json;
            // Jackson 3 (tools.jackson) es el que trae Spring Boot 4 en esta plataforma.
            // El servicio venia escrito contra Jackson 2 (com.fasterxml) y no compilaba.
            // Su excepcion base pasa a ser no comprobada, pero se sigue capturando: un
            // contexto que no serializa es entrada invalida del emisor, no un fallo de
            // la bitacora.
        } catch (JacksonException ex) {
            throw new BusinessRuleException(ErrorCode.INVALID_AUDIT_PAYLOAD,
                    "El contexto adicional del evento no se pudo serializar");
        }
    }

    private String joinRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return null;
        }
        return roles.stream()
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse(null);
    }

    /** Recorta a microsegundos, la precision real de {@code DATETIME(6)}. */
    private Instant truncate(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validateRange(Instant from, Instant to) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new BusinessRuleException(ErrorCode.INVALID_AUDIT_RANGE,
                    "El inicio del rango debe ser anterior a su fin");
        }
    }
}
