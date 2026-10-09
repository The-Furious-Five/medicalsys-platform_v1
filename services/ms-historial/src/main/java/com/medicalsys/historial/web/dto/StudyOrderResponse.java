package com.medicalsys.historial.web.dto;

import com.medicalsys.historial.domain.enums.StudyOrderStatus;
import com.medicalsys.historial.domain.enums.StudyPriority;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Orden de estudio.
 *
 * <p>El campo {@code id} es el orderId que consumen ms-agenda y ms-documental.
 *
 * @param id                      UUID publico de la orden
 * @param encounterId             encuentro que la origina
 * @param medicalRecordId         expediente al que pertenece
 * @param patientId               paciente del expediente
 * @param studyTypeCode           tipo de estudio, por ejemplo X_RAY
 * @param priority                prioridad
 * @param instructions            indicaciones para la toma
 * @param icd10Code               diagnostico presuntivo
 * @param status                  estado de la orden
 * @param requestedById           profesional solicitante
 * @param requestedAt             instante de la solicitud
 * @param collectionAppointmentId cita de toma agendada en ms-agenda
 * @param results                 resultados registrados
 */
public record StudyOrderResponse(
        UUID id,
        UUID encounterId,
        UUID medicalRecordId,
        UUID patientId,
        String studyTypeCode,
        StudyPriority priority,
        String instructions,
        String icd10Code,
        StudyOrderStatus status,
        UUID requestedById,
        OffsetDateTime requestedAt,
        UUID collectionAppointmentId,
        List<StudyResultResponse> results) {
}
