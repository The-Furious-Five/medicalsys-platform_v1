package com.medicalsys.historial.web.dto;

import com.medicalsys.historial.domain.enums.EncounterType;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Narrativa clinica del encuentro en formato SOAP.
 *
 * <p>Los textos llegan en claro y se persisten cifrados por ms-criptografia. Solo se acepta sobre
 * un encuentro en borrador.
 *
 * @param chiefComplaint            motivo de consulta
 * @param subjective                S: relato del paciente
 * @param objective                 O: examen fisico
 * @param assessment                A: analisis e impresion diagnostica
 * @param plan                      P: plan de tratamiento
 * @param endsAt                    fin de la atencion, opcional
 * @param suggestedNextAppointmentAt fecha sugerida de seguimiento, opcional
 * @param type                      correccion del tipo de encuentro, opcional
 */
public record UpdateNarrativeRequest(
        @Size(max = 4000) String chiefComplaint,
        @Size(max = 4000) String subjective,
        @Size(max = 4000) String objective,
        @Size(max = 4000) String assessment,
        @Size(max = 4000) String plan,
        OffsetDateTime endsAt,
        LocalDate suggestedNextAppointmentAt,
        EncounterType type) {
}
