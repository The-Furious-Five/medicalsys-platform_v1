package com.medicalsys.agenda.web.dto;

import com.medicalsys.agenda.domain.AppointmentStatus;
import com.medicalsys.agenda.domain.ChannelOrigin;
import com.medicalsys.agenda.domain.Modality;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AppointmentResponse(
        UUID publicId,
        UUID organizationPublicId,
        UUID branchPublicId,
        UUID patientPublicId,
        UUID healthProfessionalPublicId,
        UUID serviceOfferingPublicId,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        Modality modality,
        AppointmentStatus status,
        List<AppointmentStatus> allowedTransitions,
        ChannelOrigin channelOrigin,
        String reasonForVisit,
        boolean requiresConsent,
        BigDecimal estimatedPriceBob,
        String teleconsultationLink,
        UUID sourceAppointmentPublicId,
        UUID createdByUserPublicId,
        OffsetDateTime confirmedAt,
        OffsetDateTime arrivedAt,
        OffsetDateTime careStartedAt,
        OffsetDateTime careEndedAt,
        OffsetDateTime cancelledAt,
        UUID cancelledByUserPublicId,
        String cancellationReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
