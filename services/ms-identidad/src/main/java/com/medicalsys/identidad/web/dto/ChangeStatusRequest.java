package com.medicalsys.identidad.web.dto;

import com.medicalsys.identidad.domain.UserStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeStatusRequest(
        @NotNull UserStatus status,
        @Size(max = 300) String reason) {
}
