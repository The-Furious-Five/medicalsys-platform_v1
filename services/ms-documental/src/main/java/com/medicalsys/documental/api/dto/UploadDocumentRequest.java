package com.medicalsys.documental.api.dto;

import com.medicalsys.documental.domain.DocumentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record UploadDocumentRequest(
        @NotNull(message = "el tipo de documento es obligatorio")
        DocumentType type,

        UUID patientId,

        Boolean dicom,

        @Size(max = 20, message = "los metadatos no pueden contener mas de 20 propiedades")
        Map<String, Object> metadata,

        LocalDate retentionUntil,

        @Valid
        DocumentLinkRequest link) {
}
