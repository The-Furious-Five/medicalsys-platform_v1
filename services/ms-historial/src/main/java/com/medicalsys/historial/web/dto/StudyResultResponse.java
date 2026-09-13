package com.medicalsys.historial.web.dto;

import com.medicalsys.historial.domain.enums.StudyResultStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resultado de un estudio.
 *
 * <p>{@code documentId} es la unica referencia al archivo: el binario vive en MinIO bajo propiedad
 * de ms-documental.
 *
 * @param id                  identificador local
 * @param parameter           componente del panel
 * @param numericValue        valor numerico
 * @param textValue           informe descifrado, sujeto a rol
 * @param unit                unidad del valor
 * @param referenceMin        limite inferior de referencia
 * @param referenceMax        limite superior de referencia
 * @param outOfRange          true si el valor esta fuera del rango de referencia
 * @param status              estado del resultado
 * @param documentId          referencia al archivo en ms-documental
 * @param reportedById        profesional que informo
 * @param reportedAt          instante del informe
 * @param deliveredToPatientAt instante de entrega al paciente
 */
public record StudyResultResponse(
        Long id,
        String parameter,
        BigDecimal numericValue,
        String textValue,
        String unit,
        BigDecimal referenceMin,
        BigDecimal referenceMax,
        boolean outOfRange,
        StudyResultStatus status,
        UUID documentId,
        UUID reportedById,
        OffsetDateTime reportedAt,
        OffsetDateTime deliveredToPatientAt) {
}
