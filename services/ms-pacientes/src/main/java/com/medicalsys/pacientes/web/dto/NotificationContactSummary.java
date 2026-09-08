package com.medicalsys.pacientes.web.dto;

import java.util.UUID;

/**
 * Proyección exclusiva para el disparo de notificaciones (Agenda la usa para armar el mensaje de
 * WhatsApp de una cita). Deliberadamente separada de {@link SchedulingPatientSummary}, que no debe
 * exponer el teléfono del paciente.
 */
public record NotificationContactSummary(
        UUID patientId, String givenNames, String fullName, String whatsappNumber) {
}
