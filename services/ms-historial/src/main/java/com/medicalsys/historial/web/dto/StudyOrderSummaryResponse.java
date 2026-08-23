package com.medicalsys.historial.web.dto;

import com.medicalsys.historial.domain.enums.StudyOrderStatus;
import com.medicalsys.historial.domain.enums.StudyPriority;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Proyeccion minima de la orden para ms-agenda y ms-documental.
 *
 * @param id                      orderId publico
 * @param medicalRecordId         expediente al que pertenece
 * @param patientId               paciente del expediente
 * @param studyTypeCode           tipo de estudio
 * @param priority                prioridad
 * @param status                  estado de la orden
 * @param requestedById           profesional solicitante
 * @param requestedAt             instante de la solicitud
 * @param collectionAppointmentId cita de toma agendada
 */
public record StudyOrderSummaryResponse(
        UUID id,
        UUID medicalRecordId,
        UUID patientId,
        String studyTypeCode,
        StudyPriority priority,
        StudyOrderStatus status,
        UUID requestedById,
        OffsetDateTime requestedAt,
        UUID collectionAppointmentId) {
}
