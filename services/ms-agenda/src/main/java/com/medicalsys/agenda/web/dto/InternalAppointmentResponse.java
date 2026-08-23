package com.medicalsys.agenda.web.dto;

import com.medicalsys.agenda.domain.AppointmentStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO minimo para ms-historial: una cita completada habilita abrir un encuentro clinico.
 * No expone precio, canal ni motivo de cancelacion.
 */
public record InternalAppointmentResponse(
        UUID publicId,
        UUID organizationPublicId,
        UUID branchPublicId,
        UUID patientPublicId,
        UUID healthProfessionalPublicId,
        UUID serviceOfferingPublicId,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        AppointmentStatus status) {
}
