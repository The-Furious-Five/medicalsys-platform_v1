package com.medicalsys.marketing.web.dto;

import com.medicalsys.marketing.domain.enums.RecipientStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Destinatario materializado de una campana.
 *
 * @param patientId              UUID publico del paciente
 * @param fullName               nombre completo replicado
 * @param status                 resultado individual
 * @param exclusionReason        motivo de exclusion o de fallo
 * @param notificationId         mensaje de ms-notificaciones, cuando ya se entrego
 * @param generatedAppointmentId cita atribuida, cuando hubo conversion
 * @param evaluatedAt            instante en que se materializo
 */
public record CampaignRecipientResponse(
        UUID patientId,
        String fullName,
        RecipientStatus status,
        String exclusionReason,
        UUID notificationId,
        UUID generatedAppointmentId,
        OffsetDateTime evaluatedAt) {
}
