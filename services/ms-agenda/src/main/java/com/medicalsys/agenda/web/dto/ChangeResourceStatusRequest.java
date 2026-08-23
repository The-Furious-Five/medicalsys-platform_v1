package com.medicalsys.agenda.web.dto;

import com.medicalsys.agenda.domain.ResourceStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeResourceStatusRequest(@NotNull ResourceStatus status) {
}
