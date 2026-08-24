package com.medicalsys.identidad.web.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(max = 180) String fullName,
        @Pattern(regexp = "^\\+[1-9][0-9]{6,18}$", message = "debe tener formato E.164") String phoneE164,
        @Size(max = 5) String language) {
}
