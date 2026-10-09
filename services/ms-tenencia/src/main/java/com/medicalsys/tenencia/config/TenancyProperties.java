package com.medicalsys.tenencia.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuracion propia de ms-tenencia.
 *
 * <p>Ningun valor de este archivo es un secreto. Los defaults corresponden a los del diccionario
 * para {@code organization.time_zone} y {@code organization.currency}.
 *
 * @param defaultTimeZone    zona horaria de una organizacion nueva
 * @param defaultCurrency    moneda ISO-4217 de una organizacion nueva
 * @param defaultPlanCode    plan que recibe una organizacion recien creada si no se indica otro
 * @param trialPeriod        duracion del periodo de prueba de la suscripcion inicial
 * @param enforcePlanLimits  aplica los limites del plan antes de consumir una cuota
 */
@Validated
@ConfigurationProperties(prefix = "medicalsys.tenancy")
public record TenancyProperties(
        @NotBlank @DefaultValue("America/La_Paz") String defaultTimeZone,
        @NotBlank @DefaultValue("BOB") String defaultCurrency,
        @NotBlank @DefaultValue("DEMO") String defaultPlanCode,
        @NotNull @DefaultValue("30d") Duration trialPeriod,
        @DefaultValue("true") boolean enforcePlanLimits) {
}
