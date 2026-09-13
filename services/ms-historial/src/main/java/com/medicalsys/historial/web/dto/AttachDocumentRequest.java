package com.medicalsys.historial.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Vinculacion del documento generado por ms-documental.
 *
 * <p>ms-historial guarda la referencia y el hash, nunca el binario.
 *
 * @param documentId    UUID del objeto en documents_db
 * @param sha256Hash    hash SHA-256 calculado por ms-documental
 * @param certificateId certificado con el que se firmo, opcional
 */
public record AttachDocumentRequest(
        @NotNull(message = "documentId es obligatorio") UUID documentId,
        @Size(min = 64, max = 64, message = "sha256Hash debe tener 64 caracteres") String sha256Hash,
        UUID certificateId) {
}
