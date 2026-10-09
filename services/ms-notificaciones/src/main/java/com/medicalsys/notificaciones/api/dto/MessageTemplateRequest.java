package com.medicalsys.notificaciones.api.dto;

import com.medicalsys.notificaciones.domain.NotificationCategory;
import com.medicalsys.notificaciones.domain.NotificationChannel;
import com.medicalsys.notificaciones.domain.TemplateApprovalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record MessageTemplateRequest(
        @NotBlank @Size(max = 60) @Pattern(regexp = "^[A-Z0-9_]+$") String code,
        @NotNull NotificationChannel channel,
        @NotNull NotificationCategory category,
        @Size(max = 120) String providerTemplateName,
        @NotBlank @Size(max = 10) String languageCode,
        @Size(max = 200) String subject,
        @NotBlank String body,
        List<String> variables,
        @NotNull TemplateApprovalStatus approvalStatus,
        boolean active) {
}
