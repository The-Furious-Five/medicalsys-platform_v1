package com.medicalsys.historial.web.dto;

/**
 * Narrativa clinica descifrada del encuentro.
 *
 * <p>Solo se incluye para roles clinicos. Nunca se escribe en logs.
 *
 * @param chiefComplaint motivo de consulta
 * @param subjective     S: relato del paciente
 * @param objective      O: examen fisico
 * @param assessment     A: analisis e impresion diagnostica
 * @param plan           P: plan de tratamiento
 */
public record EncounterNarrativeResponse(
        String chiefComplaint,
        String subjective,
        String objective,
        String assessment,
        String plan) {
}
