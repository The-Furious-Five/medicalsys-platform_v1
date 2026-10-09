package com.medicalsys.historial.web.dto;

import com.medicalsys.historial.domain.enums.DiagnosisStatus;
import com.medicalsys.historial.domain.enums.DiagnosisType;

/**
 * Evolucion de un diagnostico; los campos nulos no se modifican.
 *
 * @param type   nueva certeza
 * @param status nueva evolucion
 */
public record UpdateDiagnosisRequest(DiagnosisType type, DiagnosisStatus status) {
}
