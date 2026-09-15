package com.medicalsys.documental.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Direcciones de los servicios vecinos que necesita el flujo de firma por OTP. */
@Validated
@ConfigurationProperties(prefix = "medicalsys.consent")
public record ConsentIntegrationProperties(
        @NotBlank String patientsBaseUrl,
        @NotBlank String notificationsBaseUrl) {
}
