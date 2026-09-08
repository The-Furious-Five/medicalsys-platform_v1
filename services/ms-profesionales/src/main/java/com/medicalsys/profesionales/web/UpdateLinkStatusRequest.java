package com.medicalsys.profesionales.web;

import com.medicalsys.profesionales.domain.OrganizationStatus;
import jakarta.validation.constraints.NotNull;

/** Cambio de estado del vinculo profesional-organizacion. */
public record UpdateLinkStatusRequest(@NotNull OrganizationStatus status) { }
