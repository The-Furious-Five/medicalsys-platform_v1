package com.medicalsys.historial.web.dto;

import com.medicalsys.historial.domain.enums.EncounterStatus;
import com.medicalsys.historial.domain.enums.EncounterType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Encuentro clinico completo.
 *
 * @param id                         UUID publico del encuentro
 * @param medicalRecordId            expediente al que pertenece
 * @param patientId                  paciente atendido
 * @param appointmentId              cita que origino la atencion
 * @param professionalId             profesional que atendio
 * @param branchId                   sucursal
 * @param type                       tipo de encuentro
 * @param status                     estado del ciclo de vida
 * @param startsAt                   inicio de la atencion
 * @param endsAt                     fin de la atencion
 * @param suggestedNextAppointmentAt fecha sugerida de seguimiento
 * @param narrative                  narrativa descifrada, sujeta a rol
 * @param encrypted                  true si la base conserva ciphertext para este encuentro
 * @param encryptionKeyId            clave usada para cifrar la narrativa
 * @param contentHash                SHA-256 del payload firmado
 * @param signedAt                   instante de la firma
 * @param voidedAt                   instante de la anulacion
 * @param cancellationReason         motivo de la anulacion
 * @param diagnoses                  diagnosticos registrados
 * @param createdAt                  alta del registro
 * @param updatedAt                  ultima modificacion
 */
public record ClinicalEncounterResponse(
        UUID id,
        UUID medicalRecordId,
        UUID patientId,
        UUID appointmentId,
        UUID professionalId,
        UUID branchId,
        EncounterType type,
        EncounterStatus status,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        LocalDate suggestedNextAppointmentAt,
        EncounterNarrativeResponse narrative,
        boolean encrypted,
        UUID encryptionKeyId,
        String contentHash,
        OffsetDateTime signedAt,
        OffsetDateTime voidedAt,
        String cancellationReason,
        List<DiagnosisResponse> diagnoses,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
