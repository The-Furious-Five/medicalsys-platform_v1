package com.medicalsys.documental.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectInformedConsentRequest(@NotBlank @Size(max = 300) String reason) {
}
