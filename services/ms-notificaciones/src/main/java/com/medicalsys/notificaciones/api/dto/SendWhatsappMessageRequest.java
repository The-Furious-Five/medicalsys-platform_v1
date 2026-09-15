package com.medicalsys.notificaciones.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SendWhatsappMessageRequest(
        @NotBlank
        @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$", message = "must use E.164 format, for example +59170000000")
        String destination,
        @Size(max = 4096) String message,
        @Size(max = 512) @Pattern(regexp = "^[a-z0-9_]*$") String templateName,
        @Size(max = 10) String languageCode,
        List<@Size(max = 1024) String> templateParameters) {
}
