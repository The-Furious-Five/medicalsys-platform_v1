package com.medicalsys.notificaciones.domain;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("message_template")
public record MessageTemplate(
        @Id Long id,
        String publicId,
        String organizationId,
        String code,
        NotificationChannel channel,
        NotificationCategory category,
        String providerTemplateName,
        String languageCode,
        String subject,
        String body,
        String variables,
        TemplateApprovalStatus approvalStatus,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public MessageTemplate update(String code, NotificationChannel channel, NotificationCategory category,
            String providerTemplateName, String languageCode, String subject, String body, String variables,
            TemplateApprovalStatus approvalStatus, boolean active, LocalDateTime now) {
        return new MessageTemplate(id, publicId, organizationId, code, channel, category, providerTemplateName,
                languageCode, subject, body, variables, approvalStatus, active, createdAt, now);
    }
}
