package com.medicalsys.marketing.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuracion propia de ms-marketing.
 *
 * <p>Toda dependencia externa se parametriza por variable de entorno: el codigo no contiene ninguna
 * URL fija ni ningun secreto. Notese que no hay -- ni puede haber -- una propiedad
 * {@code medical-records}: ms-marketing no consulta ms-historial y no existe configuracion con la
 * que activarlo.
 *
 * @param patients   integracion con ms-pacientes, unica fuente de la demografia replicada
 * @param tenancy    integracion con ms-tenencia, fuente del catalogo y del plan del tenant
 * @param entitlement como se decide si un tenant tiene marketing contratado (criterio C5)
 * @param audience   limites de la materializacion de audiencias
 * @param outbox     comportamiento del relay de eventos salientes
 * @param demo       datos de demostracion que permiten arrancar desde una base vacia
 */
@Validated
@ConfigurationProperties(prefix = "medicalsys")
public record MarketingProperties(
        @Valid @NotNull Patients patients,
        @Valid @NotNull Tenancy tenancy,
        @Valid @NotNull Entitlement entitlement,
        @Valid @DefaultValue @NotNull Audience audience,
        @Valid @DefaultValue @NotNull Outbox outbox,
        @Valid @DefaultValue @NotNull Demo demo) {

    /** Estrategia para obtener datos de un servicio vecino. */
    public enum IntegrationMode {
        /** Resuelve con datos deterministas de demostracion, sin salir del proceso. */
        LOCAL,
        /** Consulta al microservicio propietario por HTTP. */
        REMOTE
    }

    /** Estrategia con la que se decide si el tenant tiene marketing contratado. */
    public enum EntitlementMode {
        /**
         * Usa {@code ref_organization.includes_marketing}, alimentado por eventos de ms-tenencia y
         * con {@code defaultIncluded} para los tenants que todavia no publicaron el suyo.
         */
        REPLICA,
        /**
         * Todos los tenants tienen marketing. Es el modo del entorno local, donde ms-tenencia aun no
         * publica el plan; deja constancia en el log al arrancar.
         */
        ALWAYS_INCLUDED
    }

    /** Comportamiento del relay de eventos salientes mientras no exista un broker. */
    public enum OutboxMode {
        /** El relay drena el outbox escribiendo cada evento en el log y marcandolo publicado. */
        LOG,
        /** El relay no toca el outbox: los eventos se acumulan para que otro proceso los lea. */
        RETAIN
    }

    /**
     * Integracion con ms-pacientes.
     *
     * @param mode           estrategia activa
     * @param baseUrl        direccion interna de ms-pacientes
     * @param connectTimeout timeout de conexion; una espera infinita convierte una caida en bloqueo
     * @param readTimeout    timeout de lectura
     */
    public record Patients(
            @DefaultValue("LOCAL") @NotNull IntegrationMode mode,
            @NotBlank String baseUrl,
            @DefaultValue("2s") @NotNull Duration connectTimeout,
            @DefaultValue("3s") @NotNull Duration readTimeout) {
    }

    /**
     * Integracion con ms-tenencia.
     *
     * @param mode           estrategia activa
     * @param baseUrl        direccion interna de ms-tenencia
     * @param connectTimeout timeout de conexion
     * @param readTimeout    timeout de lectura
     */
    public record Tenancy(
            @DefaultValue("LOCAL") @NotNull IntegrationMode mode,
            @NotBlank String baseUrl,
            @DefaultValue("2s") @NotNull Duration connectTimeout,
            @DefaultValue("3s") @NotNull Duration readTimeout) {
    }

    /**
     * Opcionalidad por contrato: el criterio C5.
     *
     * <p>{@code plan_subscription.includes_marketing} admite 0. Un tenant que no contrato el modulo
     * recibe 403 en todas las rutas de gestion, aunque el contenedor este desplegado y sano.
     *
     * @param mode            como se resuelve el derecho
     * @param defaultIncluded que asumir para un tenant del que todavia no se replico el plan
     * @param excludedTenants tenants a los que se niega el modulo aunque su replica diga otra cosa;
     *                        existe para poder demostrar el 403 sin tocar ms-tenencia
     */
    public record Entitlement(
            @DefaultValue("REPLICA") @NotNull EntitlementMode mode,
            @DefaultValue("true") boolean defaultIncluded,
            @DefaultValue List<UUID> excludedTenants) {
    }

    /**
     * Limites de la materializacion de audiencias.
     *
     * @param maxSize tope de destinatarios de una campana; evita que un criterio vacio o demasiado
     *                amplio materialice una audiencia sin fin y bloquee la transaccion
     */
    public record Audience(@DefaultValue("10000") @Min(1) @Max(1000000) int maxSize) {
    }

    /**
     * Relay de eventos salientes.
     *
     * @param mode         que hace el relay con los eventos pendientes
     * @param batchSize    cuantos eventos toma en cada pasada
     * @param intervalMs   cadencia del relay
     */
    public record Outbox(
            @DefaultValue("LOG") @NotNull OutboxMode mode,
            @DefaultValue("100") @Min(1) @Max(1000) int batchSize,
            @DefaultValue("5000") @Min(200) long intervalMs) {
    }

    /**
     * Datos de demostracion.
     *
     * <p>Con {@code seedOnStartup=true} el servicio crea, si faltan, la organizacion replicada, un
     * programa de fidelidad y un cupon de ejemplo del tenant indicado. Es lo que permite que el
     * recorrido de humo funcione desde una base vacia sin ningun paso manual. En un entorno real se
     * desactiva.
     *
     * @param seedOnStartup   siembra los datos minimos al arrancar
     * @param organizationId  tenant de demostracion, el mismo que siembra ms-tenencia
     * @param organizationName nombre comercial ficticio del tenant
     * @param loyaltyProgramName nombre del programa de fidelidad sembrado
     * @param couponCode      codigo del cupon de ejemplo
     * @param patients        cuantos pacientes ficticios se siembran con su actividad
     */
    public record Demo(
            @DefaultValue("true") boolean seedOnStartup,
            @DefaultValue("00000000-0000-4000-8000-0000000000a1") @NotNull UUID organizationId,
            @DefaultValue("CLINICA DEMO MEDICALSYS") @NotBlank String organizationName,
            @DefaultValue("PUNTOS DEMO MEDICALSYS") @NotBlank String loyaltyProgramName,
            @DefaultValue("DEMO10") @NotBlank String couponCode,
            @DefaultValue("12") @Min(0) @Max(500) int patients) {
    }
}
