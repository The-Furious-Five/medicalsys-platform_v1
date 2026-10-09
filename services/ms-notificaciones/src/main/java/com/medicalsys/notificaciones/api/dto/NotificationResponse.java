package com.medicalsys.notificaciones.api.dto;

import com.medicalsys.notificaciones.domain.Notification;
import com.medicalsys.notificaciones.domain.NotificationCategory;
import com.medicalsys.notificaciones.domain.NotificationChannel;
import com.medicalsys.notificaciones.domain.NotificationStatus;
import java.time.LocalDateTime;

public record NotificationResponse(
        String id,
        String originEventId,
        String organizationId,
        String patientId,
        NotificationChannel channel,
        NotificationCategory category,
        String eventType,
        String entityType,
        String entityId,
        String destination,
        String renderedBody,
        NotificationStatus status,
        LocalDateTime scheduledFor,
        int attempts,
        LocalDateTime nextAttemptAt,
        LocalDateTime sentAt,
        String providerMessageId,
        String errorCode,
        String errorDetail,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static NotificationResponse from(Notification value) {
        return new NotificationResponse(value.publicId(), value.originEventId(), value.organizationId(),
                value.patientId(), value.channel(), value.category(), value.eventType(), value.entityType(),
                value.entityId(), value.destination(), value.renderedBody(), value.status(), value.scheduledFor(),
                value.attempts(), value.nextAttemptAt(), value.sentAt(), value.providerMessageId(),
                value.errorCode(), value.errorDetail(), value.createdAt(), value.updatedAt());
    }
}
