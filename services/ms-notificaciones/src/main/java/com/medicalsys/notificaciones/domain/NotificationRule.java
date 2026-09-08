package com.medicalsys.notificaciones.domain;

import java.time.LocalDateTime;
import java.time.LocalTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("notification_rule")
public record NotificationRule(
        @Id Long id,
        String publicId,
        String organizationId,
        String eventType,
        NotificationChannel channel,
        Long templateId,
        int offsetMinutes,
        LocalTime sendingWindowFrom,
        LocalTime sendingWindowTo,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public NotificationRule update(String eventType, NotificationChannel channel, Long templateId,
            int offsetMinutes, LocalTime from, LocalTime to, boolean active, LocalDateTime now) {
        return new NotificationRule(id, publicId, organizationId, eventType, channel, templateId, offsetMinutes,
                from, to, active, createdAt, now);
    }
}
