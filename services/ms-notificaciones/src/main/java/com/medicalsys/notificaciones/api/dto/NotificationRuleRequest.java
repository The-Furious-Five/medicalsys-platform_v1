package com.medicalsys.notificaciones.api.dto;

import com.medicalsys.notificaciones.domain.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.UUID;

public record NotificationRuleRequest(
        @NotBlank @Size(max = 40) String eventType,
        @NotNull NotificationChannel channel,
        @NotNull UUID templateId,
        int offsetMinutes,
        LocalTime sendingWindowFrom,
        LocalTime sendingWindowTo,
        boolean active) {
}
