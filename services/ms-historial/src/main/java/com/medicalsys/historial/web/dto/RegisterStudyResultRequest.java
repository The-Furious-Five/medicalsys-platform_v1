package com.medicalsys.historial.web.dto;

import com.medicalsys.historial.domain.enums.StudyResultStatus;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Registro del resultado de un estudio.
 *
 * <p>ms-historial guarda la referencia al documento, nunca el archivo. Al registrar el resultado la
 * orden pasa a REPORTED, que es el estado "disponible" del guion de demostracion.
 *
 * @param parameter      componente del panel; por defecto RESULT para estudios de un solo valor
 * @param numericValue   valor numerico, opcional
 * @param textValue      informe narrativo en claro; se persiste cifrado
 * @param unit           unidad del valor numerico
 * @param referenceMin   limite inferior de referencia
 * @param referenceMax   limite superior de referencia
 * @param status         estado del resultado; por defecto FINAL al informar
 * @param documentId     UUID del archivo en ms-documental
 * @param reportedById   profesional que informa el resultado
 */
public record RegisterStudyResultRequest(
        @Size(max = 120) String parameter,
        BigDecimal numericValue,
        @Size(max = 3000) String textValue,
        @Size(max = 30) String unit,
        BigDecimal referenceMin,
        BigDecimal referenceMax,
        StudyResultStatus status,
        UUID documentId,
        UUID reportedById) {
}
