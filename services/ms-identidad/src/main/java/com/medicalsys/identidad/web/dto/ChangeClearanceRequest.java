package com.medicalsys.identidad.web.dto;

import com.medicalsys.identidad.domain.SecurityLevel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeClearanceRequest(
        @NotNull SecurityLevel clearance,
        @Size(max = 300) String reason) {
}
