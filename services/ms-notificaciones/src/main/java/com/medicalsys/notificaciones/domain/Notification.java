package com.medicalsys.notificaciones.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("notification")
public record Notification(
        @Id Long id,
        String publicId,
        String originEventId,
        String idempotencyKey,
        String organizationId,
        String patientId,
        String destinationUserId,
        NotificationChannel channel,
        NotificationCategory category,
        Long templateId,
        Long ruleId,
        String eventType,
        String entityType,
        String entityId,
        String destination,
        String variables,
        String renderedBody,
        NotificationStatus status,
        LocalDateTime scheduledFor,
        int attempts,
        LocalDateTime nextAttemptAt,
        LocalDateTime sentAt,
        LocalDateTime deliveredAt,
        LocalDateTime readAt,
        String providerMessageId,
        String errorCode,
        String errorDetail,
        BigDecimal estimatedCostUsd,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public Notification queuedAgain(LocalDateTime now) {
        return new Notification(id, publicId, originEventId, idempotencyKey, organizationId, patientId, destinationUserId,
                channel, category, templateId, ruleId, eventType, entityType, entityId, destination, variables,
                renderedBody, NotificationStatus.QUEUED, now, attempts, null, sentAt, deliveredAt, readAt,
                providerMessageId, null, null, estimatedCostUsd, createdAt, now);
    }

    public Notification sent(String providerId, LocalDateTime now) {
        return new Notification(id, publicId, originEventId, idempotencyKey, organizationId, patientId, destinationUserId,
                channel, category, templateId, ruleId, eventType, entityType, entityId, destination, variables,
                renderedBody, NotificationStatus.SENT, scheduledFor, attempts + 1, null, now, deliveredAt, readAt,
                providerId, null, null, estimatedCostUsd, createdAt, now);
    }

    public Notification failed(String code, String detail, LocalDateTime nextAttempt, LocalDateTime now) {
        return new Notification(id, publicId, originEventId, idempotencyKey, organizationId, patientId, destinationUserId,
                channel, category, templateId, ruleId, eventType, entityType, entityId, destination, variables,
                renderedBody, NotificationStatus.FAILED, scheduledFor, attempts + 1, nextAttempt, sentAt,
                deliveredAt, readAt, providerMessageId, code, detail, estimatedCostUsd, createdAt, now);
    }

    public Notification providerStatus(NotificationStatus newStatus, LocalDateTime occurredAt,
            String code, String detail, LocalDateTime now) {
        LocalDateTime delivered = newStatus == NotificationStatus.DELIVERED ? occurredAt : deliveredAt;
        LocalDateTime read = newStatus == NotificationStatus.READ ? occurredAt : readAt;
        return new Notification(id, publicId, originEventId, idempotencyKey, organizationId, patientId,
                destinationUserId, channel, category, templateId, ruleId, eventType, entityType, entityId,
                destination, variables, renderedBody, newStatus, scheduledFor, attempts, nextAttemptAt, sentAt,
                delivered, read, providerMessageId, code, detail, estimatedCostUsd, createdAt, now);
    }
}
