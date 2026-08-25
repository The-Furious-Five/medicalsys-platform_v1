package com.medicalsys.documental.api.dto;

import com.medicalsys.documental.domain.LinkedEntityType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record DocumentLinkRequest(
        @NotNull(message = "el tipo de entidad vinculada es obligatorio")
        LinkedEntityType entityType,

        @NotNull(message = "el identificador de la entidad vinculada es obligatorio")
        UUID entityId,

        @Size(max = 250, message = "la descripcion no puede superar 250 caracteres")
        String description) {
}
