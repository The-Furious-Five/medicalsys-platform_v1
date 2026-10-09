package com.medicalsys.historial.web.dto;

import com.medicalsys.historial.domain.enums.RecordStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Expediente clinico.
 *
 * @param id                  UUID publico del expediente
 * @param patientId           paciente propietario
 * @param recordNumber        correlativo legible dentro del tenant
 * @param openedOn            fecha de apertura
 * @param openingBranchId     sucursal de apertura
 * @param status              estado administrativo
 * @param sharesWithNetwork   autorizacion de compartir con otras organizaciones
 * @param sharingAuthorizedAt instante de la autorizacion
 * @param retentionUntil      plazo de conservacion legal
 * @param createdAt           alta del registro
 * @param updatedAt           ultima modificacion
 */
public record MedicalRecordResponse(
        UUID id,
        UUID patientId,
        String recordNumber,
        LocalDate openedOn,
        UUID openingBranchId,
        RecordStatus status,
        boolean sharesWithNetwork,
        OffsetDateTime sharingAuthorizedAt,
        LocalDate retentionUntil,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
