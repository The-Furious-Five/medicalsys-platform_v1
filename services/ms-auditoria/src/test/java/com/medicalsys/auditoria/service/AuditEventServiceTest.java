package com.medicalsys.auditoria.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medicalsys.auditoria.common.error.ApiException;
import com.medicalsys.auditoria.common.error.ErrorCode;
import com.medicalsys.auditoria.domain.enums.AuditCategory;
import com.medicalsys.auditoria.domain.enums.AuditOutcome;
import com.medicalsys.auditoria.domain.enums.AuditSeverity;
import com.medicalsys.auditoria.domain.model.AuditChainHead;
import com.medicalsys.auditoria.domain.model.AuditEvent;
import com.medicalsys.auditoria.repository.AuditChainHeadRepository;
import com.medicalsys.auditoria.repository.AuditEventRepository;
import com.medicalsys.auditoria.web.dto.AuditEventReceipt;
import com.medicalsys.auditoria.web.dto.RecordAuditEventRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditEventServiceTest {

    private static final UUID ORGANIZATION = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");

    @Mock
    private AuditEventRepository events;
    @Mock
    private AuditChainHeadRepository chainHeads;

    private AuditEventService service;

    @BeforeEach
    void setUp() {
        service = new AuditEventService(events, chainHeads, new AuditHashChain(), new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** El primer evento de una organizacion abre la cadena: posicion 1 y sin eslabon previo. */
    @Test
    void opensTheChainForTheFirstEventOfAnOrganization() {
        when(chainHeads.findByOrganizationId(ORGANIZATION)).thenReturn(Optional.empty());
        when(chainHeads.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(events.findByOrganizationIdAndPublicId(any(), any())).thenReturn(Optional.empty());
        when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AuditEventReceipt receipt = service.record(request(UUID.randomUUID(), "PATIENT_REGISTERED", null));

        ArgumentCaptor<AuditEvent> saved = ArgumentCaptor.forClass(AuditEvent.class);
        verify(events).save(saved.capture());
        assertEquals(1L, saved.getValue().getSequenceNumber());
        assertNull(saved.getValue().getPreviousHash());
        assertNotNull(receipt.recordHash());
    }

    /** Un evento posterior encadena con el hash de la punta y avanza la secuencia. */
    @Test
    void chainsTheNextEventToTheCurrentHead() {
        AuditChainHead head = AuditChainHead.startFor(ORGANIZATION);
        head.advanceTo(7, "f".repeat(64));
        when(chainHeads.findByOrganizationId(ORGANIZATION)).thenReturn(Optional.of(head));
        when(events.findByOrganizationIdAndPublicId(any(), any())).thenReturn(Optional.empty());
        when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.record(request(UUID.randomUUID(), "PATIENT_UPDATED", null));

        ArgumentCaptor<AuditEvent> saved = ArgumentCaptor.forClass(AuditEvent.class);
        verify(events).save(saved.capture());
        assertEquals(8L, saved.getValue().getSequenceNumber());
        assertEquals("f".repeat(64), saved.getValue().getPreviousHash());
        assertEquals(8L, head.getLastSequenceNumber());
        assertEquals(saved.getValue().getRecordHash(), head.getLastRecordHash());
    }

    /**
     * Un productor que reintenta el mismo evento recibe el acuse original.
     *
     * <p>La auditoria se envia de forma asincrona y con reintentos; si un reintento duplicara la
     * entrada, la bitacora reportaria dos accesos donde hubo uno.
     */
    @Test
    void acknowledgesARetryWithoutDuplicatingTheEntry() {
        UUID eventId = UUID.randomUUID();
        AuditEvent existing = existingEvent(eventId, "PATIENT_REGISTERED");
        when(events.findByOrganizationIdAndPublicId(ORGANIZATION, eventId)).thenReturn(Optional.of(existing));

        AuditEventReceipt receipt = service.record(request(eventId, "PATIENT_REGISTERED", null));

        assertEquals(existing.getRecordHash(), receipt.recordHash());
        verify(events, never()).save(any());
    }

    /** El mismo identificador describiendo otro hecho es un error del productor, no un reintento. */
    @Test
    void rejectsAReusedIdentifierThatDescribesADifferentFact() {
        UUID eventId = UUID.randomUUID();
        when(events.findByOrganizationIdAndPublicId(ORGANIZATION, eventId))
                .thenReturn(Optional.of(existingEvent(eventId, "PATIENT_REGISTERED")));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.record(request(eventId, "PATIENT_DELETED", null)));

        assertEquals(ErrorCode.AUDIT_EVENT_ALREADY_RECORDED, exception.code());
    }

    /** Una fecha muy adelantada desordenaria la bitacora respecto de lo que realmente paso. */
    @Test
    void rejectsAnEventDatedTooFarInTheFuture() {
        when(events.findByOrganizationIdAndPublicId(any(), any())).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> service.record(
                request(UUID.randomUUID(), "PATIENT_REGISTERED", NOW.plusSeconds(3600))));

        assertEquals(ErrorCode.INVALID_AUDIT_PAYLOAD, exception.code());
        verify(events, never()).save(any());
    }

    /** Sin contexto de tenant ni organizacion declarada, el evento no tiene donde asentarse. */
    @Test
    void rejectsAnEventWithoutOrganization() {
        RecordAuditEventRequest orphan = new RecordAuditEventRequest(UUID.randomUUID(), null, null,
                "ms-pacientes", "PATIENT_REGISTERED", AuditCategory.PATIENT_DATA, AuditSeverity.INFO,
                AuditOutcome.SUCCESS, null, null, List.of(), null, "PATIENT", null, null, null, null,
                null, null, null, Map.of());

        ApiException exception = assertThrows(ApiException.class, () -> service.record(orphan));

        assertEquals(ErrorCode.UNAUTHENTICATED, exception.code());
    }

    private RecordAuditEventRequest request(UUID eventId, String action, Instant occurredAt) {
        return new RecordAuditEventRequest(eventId, ORGANIZATION, occurredAt, "ms-pacientes", action,
                AuditCategory.PATIENT_DATA, AuditSeverity.NOTICE, AuditOutcome.SUCCESS,
                UUID.fromString("20000000-0000-4000-8000-0000000000f3"), "Recepcion Demo",
                List.of("RECEPTIONIST"), "10.0.0.10", "PATIENT",
                UUID.fromString("10000000-0000-4000-8000-000000000001"), "Paciente demo",
                UUID.fromString("10000000-0000-4000-8000-000000000001"), "POST", "/api/v1/patients",
                201, null, Map.of("documentType", "CI"));
    }

    private AuditEvent existingEvent(UUID eventId, String action) {
        return new AuditEvent(eventId, ORGANIZATION, 3, NOW, NOW, "ms-pacientes", action,
                AuditCategory.PATIENT_DATA, AuditSeverity.NOTICE, AuditOutcome.SUCCESS, null, null, null,
                null, "PATIENT", null, null, null, "corr-1", "POST", "/api/v1/patients", 201, null, null,
                null, "e".repeat(64));
    }
}
