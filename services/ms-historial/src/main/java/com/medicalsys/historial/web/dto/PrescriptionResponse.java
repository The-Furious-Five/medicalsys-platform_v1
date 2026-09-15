package com.medicalsys.historial.web.dto;

import com.medicalsys.historial.domain.enums.PrescriptionStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Receta emitida durante un encuentro.
 *
 * @param id                       UUID publico de la receta
 * @param encounterId              encuentro que la origina
 * @param prescribedByProfessionalId profesional autor, heredado del encuentro que la origina
 * @param folio                    correlativo legible dentro del tenant
 * @param issuedAt                 instante de emision
 * @param validityDays             vigencia en dias
 * @param generalInstructions      indicaciones generales descifradas, sujetas a rol
 * @param status                   estado de la receta
 * @param documentId               PDF firmado en ms-documental
 * @param sha256Hash               hash del documento
 * @param signedAt                 instante de la firma
 * @param items                    detalles de la receta
 */
public record PrescriptionResponse(
        UUID id,
        UUID encounterId,
        UUID prescribedByProfessionalId,
        String folio,
        OffsetDateTime issuedAt,
        Integer validityDays,
        String generalInstructions,
        PrescriptionStatus status,
        UUID documentId,
        String sha256Hash,
        OffsetDateTime signedAt,
        List<PrescriptionItemResponse> items) {
}
