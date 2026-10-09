package com.medicalsys.pacientes.web.dto;

import com.medicalsys.pacientes.domain.enums.ConsentSource;
import com.medicalsys.pacientes.domain.enums.NotificationCategory;
import com.medicalsys.pacientes.domain.enums.NotificationChannel;
import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationPreferenceResponse(
        UUID patientId,
        NotificationChannel channel,
        NotificationCategory category,
        boolean optIn,
        ConsentSource consentSource,
        OffsetDateTime optInAt,
        OffsetDateTime optOutAt,
        OffsetDateTime updatedAt) {
}
