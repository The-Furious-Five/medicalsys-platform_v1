package com.medicalsys.pacientes.web.dto;

import com.medicalsys.pacientes.domain.enums.ConsentSource;
import jakarta.validation.constraints.NotNull;

/** Cambio de consentimiento para una combinación canal/categoría. */
public record NotificationPreferenceRequest(@NotNull Boolean optIn, ConsentSource consentSource) {
}
