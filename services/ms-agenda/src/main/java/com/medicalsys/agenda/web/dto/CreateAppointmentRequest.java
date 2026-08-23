package com.medicalsys.agenda.web.dto;

import com.medicalsys.agenda.domain.ChannelOrigin;
import com.medicalsys.agenda.domain.Modality;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateAppointmentRequest(
        @NotNull UUID organizationPublicId,
        @NotNull UUID branchPublicId,
        @NotNull UUID patientPublicId,
        @NotNull UUID healthProfessionalPublicId,
        @NotNull UUID serviceOfferingPublicId,
        @NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime endsAt,
        Modality modality,
        ChannelOrigin channelOrigin,
        @Size(max = 300) String reasonForVisit,
        Boolean requiresConsent,
        @DecimalMin("0.0") BigDecimal estimatedPriceBob,
        @Size(max = 400) String teleconsultationLink,
        /** Si viene, se reserva el consultorio en la misma transaccion que la cita. */
        UUID physicalResourcePublicId,
        UUID createdByUserPublicId) {
}
