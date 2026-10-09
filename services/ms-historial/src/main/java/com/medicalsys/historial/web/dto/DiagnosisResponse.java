package com.medicalsys.historial.web.dto;

import com.medicalsys.historial.domain.enums.DiagnosisStatus;
import com.medicalsys.historial.domain.enums.DiagnosisType;
import com.medicalsys.historial.domain.enums.Laterality;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Diagnostico del encuentro.
 *
 * @param id                     identificador local
 * @param diagnosedByProfessionalId profesional autor, heredado del encuentro que lo contiene
 * @param icd10Code              codigo CIE-10 validado contra ms-catalogos
 * @param type                   certeza del diagnostico
 * @param primary                true si es el principal
 * @param chronic                true si es cronico
 * @param laterality             lateralidad
 * @param observation            observacion descifrada, sujeta a rol
 * @param status                 evolucion
 * @param createdAt              instante del registro
 */
public record DiagnosisResponse(
        Long id,
        UUID diagnosedByProfessionalId,
        String icd10Code,
        DiagnosisType type,
        boolean primary,
        boolean chronic,
        Laterality laterality,
        String observation,
        DiagnosisStatus status,
        OffsetDateTime createdAt) {
}
