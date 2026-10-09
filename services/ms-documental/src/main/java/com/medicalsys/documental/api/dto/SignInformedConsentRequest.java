package com.medicalsys.documental.api.dto;

import com.medicalsys.documental.domain.SignerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignInformedConsentRequest(
        @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "el codigo debe tener 6 digitos") String otpCode,
        @NotBlank @Size(max = 25) String signerDocumentNumber,
        @NotNull SignerType signerType) {
}
