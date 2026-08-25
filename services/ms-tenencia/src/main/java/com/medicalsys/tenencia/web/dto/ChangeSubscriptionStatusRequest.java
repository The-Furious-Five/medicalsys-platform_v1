package com.medicalsys.tenencia.web.dto;

import com.medicalsys.tenencia.domain.enums.SubscriptionStatus;
import jakarta.validation.constraints.NotNull;

/** Cambio de estado del contrato; al cancelar se cierra tambien su vigencia. */
public record ChangeSubscriptionStatusRequest(@NotNull SubscriptionStatus status) {
}
