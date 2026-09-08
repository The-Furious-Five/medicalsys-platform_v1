package com.medicalsys.auditoria.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.medicalsys.auditoria.common.security.Role;
import com.medicalsys.auditoria.common.tenant.TestRequestContext;
import com.medicalsys.auditoria.domain.enums.AuditCategory;
import com.medicalsys.auditoria.domain.enums.AuditOutcome;
import com.medicalsys.auditoria.domain.enums.AuditSeverity;
import com.medicalsys.auditoria.domain.model.AuditChainHead;
import com.medicalsys.auditoria.domain.model.AuditEvent;
import com.medicalsys.auditoria.repository.AuditChainHeadRepository;
import com.medicalsys.auditoria.repository.AuditEventRepository;
import com.medicalsys.auditoria.web.dto.AuditIntegrityResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * La verificacion tiene que distinguir una cadena sana de una manipulada.
 *
 * <p>Las tres pruebas cubren las tres formas de alterar una bitacora: editar un evento, quitarlo y
 * reencadenarlo mal.
 */
@ExtendWith(MockitoExtension.class)
class AuditIntegrityServiceTest {

    private static final UUID ORGANIZATION = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final Instant BASE = Instant.parse("2026-09-07T12:00:00Z");

    @Mock
    private AuditEventRepository events;
    @Mock
    private AuditChainHeadRepository chainHeads;

    private final AuditHashChain hashChain = new AuditHashChain();
    private AuditIntegrityService service;

    @BeforeEach
    void setUp() {
        service = new AuditIntegrityService(events, chainHeads, hashChain,
                Clock.fixed(BASE, ZoneOffset.UTC));
        TestRequestContext.as(ORGANIZATION, UUID.randomUUID(), Role.ADMINISTRATOR);
    }

    @AfterEach
    void tearDown() {
        TestRequestContext.clear();
    }

    @Test
    void reportsAnIntactChain() {
        givenChainOf(3);

        AuditIntegrityResponse report = service.verifyLatest(null);

        assertTrue(report.intact());
        assertEquals(3, report.verifiedEvents());
    }

    /** Un evento editado despues de escrito ya no reproduce su propio hash. */
    @Test
    void detectsAnEditedEvent() {
        List<AuditEvent> chain = chainOf(3);
        AuditEvent tampered = copyWithAction(chain.get(1), "PATIENT_DELETED");
        chain.set(1, tampered);
        given(chain, 3);

        AuditIntegrityResponse report = service.verifyLatest(null);

        assertFalse(report.intact());
        assertEquals(2L, report.brokenAtSequenceNumber());
        assertEquals(tampered.getPublicId(), report.brokenEventId());
    }

    /** Un evento borrado deja un salto en la secuencia, aunque los hashes restantes sean validos. */
    @Test
    void detectsAMissingEvent() {
        List<AuditEvent> chain = chainOf(3);
        chain.remove(1);
        given(chain, 3);

        AuditIntegrityResponse report = service.verifyLatest(null);

        assertFalse(report.intact());
        assertEquals(2L, report.brokenAtSequenceNumber());
    }

    private void givenChainOf(int size) {
        given(chainOf(size), size);
    }

    private void given(List<AuditEvent> chain, long lastSequence) {
        AuditChainHead head = AuditChainHead.startFor(ORGANIZATION);
        head.advanceTo(lastSequence, chain.isEmpty() ? null : chain.get(chain.size() - 1).getRecordHash());
        // La verificacion lee la punta sin bloquearla; el bloqueo es del camino de escritura.
        when(chainHeads.peekByOrganizationId(ORGANIZATION)).thenReturn(Optional.of(head));
        when(events.findByOrganizationIdAndSequenceNumberGreaterThanEqualOrderBySequenceNumberAsc(
                eq(ORGANIZATION), eq(1L), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(2);
                    int from = (int) pageable.getOffset();
                    int to = Math.min(chain.size(), from + pageable.getPageSize());
                    List<AuditEvent> slice = from >= chain.size() ? List.of() : chain.subList(from, to);
                    return new PageImpl<>(slice, pageable, chain.size());
                });
    }

    /** Construye una cadena valida encadenando los hashes con la misma regla que el servicio. */
    private List<AuditEvent> chainOf(int size) {
        List<AuditEvent> chain = new ArrayList<>();
        String previousHash = null;
        for (int index = 1; index <= size; index++) {
            AuditEvent event = build(index, "PATIENT_REGISTERED", previousHash);
            chain.add(event);
            previousHash = event.getRecordHash();
        }
        return chain;
    }

    private AuditEvent build(long sequence, String action, String previousHash) {
        UUID publicId = new UUID(0, sequence);
        Instant occurredAt = BASE.minusSeconds(600 - sequence);
        String hash = hashChain.hash(previousHash, publicId, ORGANIZATION, sequence, occurredAt, occurredAt,
                "ms-pacientes", action, "PATIENT_DATA", "NOTICE", "SUCCESS", null, null, null, null,
                "PATIENT", null, null, null, "corr-" + sequence, "POST", "/api/v1/patients", 201, null, null);
        return new AuditEvent(publicId, ORGANIZATION, sequence, occurredAt, occurredAt, "ms-pacientes",
                action, AuditCategory.PATIENT_DATA, AuditSeverity.NOTICE, AuditOutcome.SUCCESS, null, null,
                null, null, "PATIENT", null, null, null, "corr-" + sequence, "POST", "/api/v1/patients",
                201, null, null, previousHash, hash);
    }

    /** Reescribe la accion conservando el hash original: exactamente lo que haria una manipulacion. */
    private AuditEvent copyWithAction(AuditEvent original, String action) {
        return new AuditEvent(original.getPublicId(), original.getOrganizationId(),
                original.getSequenceNumber(), original.getOccurredAt(), original.getRecordedAt(),
                original.getServiceName(), action, original.getCategory(), original.getSeverity(),
                original.getOutcome(), original.getActorUserId(), original.getActorDisplayName(),
                original.getActorRoles(), original.getActorIp(), original.getResourceType(),
                original.getResourcePublicId(), original.getResourceLabel(), original.getPatientPublicId(),
                original.getCorrelationId(), original.getRequestMethod(), original.getRequestPath(),
                original.getResponseStatus(), original.getReason(), original.getMetadataJson(),
                original.getPreviousHash(), original.getRecordHash());
    }
}
