package com.medicalsys.marketing.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Proyeccion comercial de un paciente.
 *
 * <p>Es util leer este record entero y notar lo que falta. Hay fechas, contadores e importes; no hay
 * un solo campo que diga por que el paciente vino. {@code suggestedNextVisitOn} es una fecha, y solo
 * una fecha: marketing sabe cuando volver a llamar, no de que padece el paciente.
 *
 * @param patientId               paciente
 * @param fullName                nombre completo replicado
 * @param firstVisitOn            primera visita registrada
 * @param lastVisitOn             ultima visita registrada
 * @param totalAppointments       citas atendidas
 * @param totalNoShows            inasistencias
 * @param totalBilledBob          gasto acumulado en bolivianos
 * @param lastServiceOfferingId   ultima prestacion comprada
 * @param lastServiceOfferingName nombre de esa prestacion
 * @param suggestedNextVisitOn    fecha de control sugerida, sin su motivo
 * @param marketingOptOut         si pidio no recibir comunicaciones comerciales
 * @param updatedAt               instante de la ultima proyeccion
 */
public record PatientActivityResponse(
        UUID patientId,
        String fullName,
        LocalDate firstVisitOn,
        LocalDate lastVisitOn,
        long totalAppointments,
        long totalNoShows,
        BigDecimal totalBilledBob,
        UUID lastServiceOfferingId,
        String lastServiceOfferingName,
        LocalDate suggestedNextVisitOn,
        boolean marketingOptOut,
        OffsetDateTime updatedAt) {
}
