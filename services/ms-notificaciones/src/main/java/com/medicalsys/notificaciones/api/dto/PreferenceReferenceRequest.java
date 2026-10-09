package com.medicalsys.notificaciones.api.dto;

import com.medicalsys.notificaciones.domain.NotificationCategory;
import com.medicalsys.notificaciones.domain.NotificationChannel;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;

public record PreferenceReferenceRequest(
        @NotNull UUID patientId,
        @NotNull NotificationChannel channel,
        @NotNull NotificationCategory category,
        boolean optedIn,
        LocalDateTime optedInAt,
        LocalDateTime optedOutAt) {
}
