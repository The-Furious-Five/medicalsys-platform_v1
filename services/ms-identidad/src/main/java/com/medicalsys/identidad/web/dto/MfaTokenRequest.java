package com.medicalsys.identidad.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MfaTokenRequest(@NotBlank @Size(max = 64) String mfaToken) {
}
