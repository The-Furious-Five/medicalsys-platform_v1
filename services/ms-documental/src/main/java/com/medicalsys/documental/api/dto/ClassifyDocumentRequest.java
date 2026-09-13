package com.medicalsys.documental.api.dto;

import com.medicalsys.documental.domain.DocumentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record ClassifyDocumentRequest(
        @NotNull(message = "el tipo de documento es obligatorio")
        DocumentType type,

        Boolean dicom,

        @Size(max = 20, message = "los metadatos no pueden contener mas de 20 propiedades")
        Map<String, Object> metadata) {
}
