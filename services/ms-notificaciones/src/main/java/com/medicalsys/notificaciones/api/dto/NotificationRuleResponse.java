package com.medicalsys.notificaciones.api.dto;

import com.medicalsys.notificaciones.domain.NotificationChannel;
import com.medicalsys.notificaciones.domain.NotificationRule;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record NotificationRuleResponse(
        String id,
        String eventType,
        NotificationChannel channel,
        String templateId,
        int offsetMinutes,
        LocalTime sendingWindowFrom,
        LocalTime sendingWindowTo,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static NotificationRuleResponse from(NotificationRule value, String templatePublicId) {
        return new NotificationRuleResponse(value.publicId(), value.eventType(), value.channel(), templatePublicId,
                value.offsetMinutes(), value.sendingWindowFrom(), value.sendingWindowTo(), value.active(),
                value.createdAt(), value.updatedAt());
    }
}
