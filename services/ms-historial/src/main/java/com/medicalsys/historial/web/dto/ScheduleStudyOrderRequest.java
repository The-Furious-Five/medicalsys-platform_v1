package com.medicalsys.historial.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Vinculacion de la cita de toma creada por ms-agenda.
 *
 * @param collectionAppointmentId cita agendada para la toma del estudio
 */
public record ScheduleStudyOrderRequest(
        @NotNull(message = "collectionAppointmentId es obligatorio") UUID collectionAppointmentId) {
}
