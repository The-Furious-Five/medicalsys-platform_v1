package com.medicalsys.notificaciones.api.dto;

import com.medicalsys.notificaciones.domain.MessageTemplate;
import com.medicalsys.notificaciones.domain.NotificationCategory;
import com.medicalsys.notificaciones.domain.NotificationChannel;
import com.medicalsys.notificaciones.domain.TemplateApprovalStatus;
import java.time.LocalDateTime;
import java.util.List;

public record MessageTemplateResponse(
        String id,
        String code,
        NotificationChannel channel,
        NotificationCategory category,
        String providerTemplateName,
        String languageCode,
        String subject,
        String body,
        List<String> variables,
        TemplateApprovalStatus approvalStatus,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static MessageTemplateResponse from(MessageTemplate value, List<String> variables) {
        return new MessageTemplateResponse(value.publicId(), value.code(), value.channel(), value.category(),
                value.providerTemplateName(), value.languageCode(), value.subject(), value.body(), variables,
                value.approvalStatus(), value.active(), value.createdAt(), value.updatedAt());
    }
}
