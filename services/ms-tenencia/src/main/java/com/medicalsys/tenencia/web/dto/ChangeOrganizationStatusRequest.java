package com.medicalsys.tenencia.web.dto;

import com.medicalsys.tenencia.domain.enums.OrganizationStatus;
import jakarta.validation.constraints.NotNull;

/** Cambio de estado del tenant, por ejemplo cerrar el onboarding y activarlo. */
public record ChangeOrganizationStatusRequest(@NotNull OrganizationStatus status) {
}
