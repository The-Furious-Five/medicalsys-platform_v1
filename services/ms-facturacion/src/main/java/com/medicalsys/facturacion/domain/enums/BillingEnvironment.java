package com.medicalsys.facturacion.domain.enums;

/**
 * Ambiente fiscal de la configuracion ({@code billing_configuration.environment}).
 *
 * <p>El MVP local trabaja siempre en {@link #PILOT}. {@link #PRODUCTION} existe en el modelo, pero
 * exige credenciales reales, certificado de firma y validacion normativa: queda fuera del sprint.
 */
public enum BillingEnvironment {
    PILOT,
    PRODUCTION
}
