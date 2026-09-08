package com.medicalsys.notificaciones.api.dto;

import com.medicalsys.notificaciones.domain.NotificationCategory;
import com.medicalsys.notificaciones.domain.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record CreateNotificationRequest(
        UUID originEventId,
        @NotNull UUID patientId,
        UUID destinationUserId,
        @NotNull NotificationChannel channel,
        @NotNull NotificationCategory category,
        @NotBlank String eventType,
        @NotBlank String entityType,
        UUID entityId,
        @NotBlank @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$", message = "must use E.164 format, for example +59170000000") String destination,
        Map<String, Object> variables,
        @NotBlank String renderedBody,
        LocalDateTime scheduledFor) {
}
