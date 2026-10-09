package com.medicalsys.historial.web.dto;

import com.medicalsys.historial.domain.enums.EncounterStatus;
import com.medicalsys.historial.domain.enums.EncounterType;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Proyeccion de encuentro para listados y para otros microservicios.
 *
 * <p>No incluye narrativa: un listado nunca descifra contenido clinico.
 *
 * @param id             UUID publico del encuentro
 * @param medicalRecordId expediente al que pertenece
 * @param patientId      paciente atendido
 * @param appointmentId  cita que origino la atencion
 * @param professionalId profesional que atendio
 * @param type           tipo de encuentro
 * @param status         estado del ciclo de vida
 * @param startsAt       inicio de la atencion
 * @param endsAt         fin de la atencion
 */
public record EncounterSummaryResponse(
        UUID id,
        UUID medicalRecordId,
        UUID patientId,
        UUID appointmentId,
        UUID professionalId,
        EncounterType type,
        EncounterStatus status,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt) {
}
