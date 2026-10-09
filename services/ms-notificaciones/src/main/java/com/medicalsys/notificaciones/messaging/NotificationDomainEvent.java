package com.medicalsys.notificaciones.messaging;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** Contrato común consumido desde agenda, historial, documental y facturación. */
public record NotificationDomainEvent(
        @NotNull UUID eventId,
        @NotNull UUID organizationId,
        @NotNull UUID patientId,
        @NotBlank String eventType,
        @NotBlank String entityType,
        UUID entityId,
        @NotBlank String destination,
        Map<String, Object> variables,
        LocalDateTime occurredAt,
        LocalDateTime targetAt) {
}
