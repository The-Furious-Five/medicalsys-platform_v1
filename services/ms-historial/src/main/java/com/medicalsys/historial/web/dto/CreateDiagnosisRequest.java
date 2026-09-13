package com.medicalsys.historial.web.dto;

import com.medicalsys.historial.domain.enums.DiagnosisType;
import com.medicalsys.historial.domain.enums.Laterality;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Diagnostico codificado del encuentro.
 *
 * <p>El codigo CIE-10 se valida contra ms-catalogos antes de persistir: un codigo inexistente se
 * rechaza con 422 ICD10_CODE_NOT_ALLOWED.
 *
 * @param icd10Code   codigo CIE-10
 * @param type        certeza del diagnostico; por defecto PROVISIONAL
 * @param primary     true si es el diagnostico principal del encuentro
 * @param chronic     true si es una condicion cronica
 * @param laterality  lateralidad; por defecto NOT_APPLICABLE
 * @param observation observacion en claro; se persiste cifrada
 */
public record CreateDiagnosisRequest(
        @NotBlank(message = "icd10Code es obligatorio") @Size(max = 10) String icd10Code,
        DiagnosisType type,
        Boolean primary,
        Boolean chronic,
        Laterality laterality,
        @Size(max = 1000) String observation) {
}
