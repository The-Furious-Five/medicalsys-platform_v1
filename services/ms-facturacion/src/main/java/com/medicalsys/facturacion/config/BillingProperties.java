package com.medicalsys.facturacion.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuracion propia de ms-facturacion.
 *
 * <p>Toda dependencia externa se parametriza por variable de entorno: el codigo nunca contiene una
 * URL fija, un NIT real ni un secreto. Ningun campo de este archivo es una credencial del SIN,
 * porque el MVP no usa ninguna.
 *
 * @param patients     integracion con ms-pacientes (DTO fiscal minimo del cliente)
 * @param tenancy      integracion con ms-tenencia (prestaciones y precios)
 * @param taxAuthority adaptador hacia el sistema fiscal
 * @param billing      reglas propias de emision: correlativo, codigos por defecto y setup local
 */
@Validated
@ConfigurationProperties(prefix = "medicalsys")
public record BillingProperties(
        @Valid @NotNull Patients patients,
        @Valid @NotNull Tenancy tenancy,
        @Valid @NotNull TaxAuthority taxAuthority,
        @Valid @NotNull Billing billing) {

    /** Estrategia para obtener datos de un servicio vecino. */
    public enum IntegrationMode {
        /** Resuelve con datos deterministas de demostracion, sin salir del proceso. */
        LOCAL,
        /** Consulta el microservicio propietario por HTTP. */
        REMOTE
    }

    /** Estrategia del adaptador fiscal. */
    public enum TaxAuthorityMode {
        /**
         * Stub determinista sin red ni credenciales. Devuelve {@code DEMO_ACCEPTED} y la leyenda no
         * tributaria. Es el unico modo implementado en este sprint.
         */
        SIMULATED
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
     * @param local          prestaciones de demostracion usadas en modo LOCAL
     */
    public record Tenancy(
            @DefaultValue("LOCAL") @NotNull IntegrationMode mode,
            @NotBlank String baseUrl,
            @DefaultValue("2s") @NotNull Duration connectTimeout,
            @DefaultValue("3s") @NotNull Duration readTimeout,
            @Valid @DefaultValue @NotNull Local local) {

        /**
         * Catalogo de prestaciones mientras ms-tenencia no publique su endpoint interno.
         *
         * <p>Reproduce las dos prestaciones ficticias que ms-tenencia siembra en el entorno local.
         * Al pasar a {@code REMOTE} esta lista deja de usarse.
         *
         * @param offerings prestaciones facturables de demostracion
         */
        public record Local(@Valid @NotEmpty List<Offering> offerings) {
        }

        /**
         * Prestacion facturable de demostracion.
         *
         * @param id                 UUID publico de la prestacion en tenancy_db
         * @param code               codigo de la prestacion, por ejemplo CONSULTATION
         * @param name               descripcion que se congela en el detalle de la factura
         * @param basePriceBob       precio unitario en bolivianos
         * @param taxProductCode     codigo de producto/servicio homologado
         * @param taxUnitCode        unidad de medida del catalogo del SIN; 58 = unidad
         */
        public record Offering(
                @NotNull UUID id,
                @NotBlank String code,
                @NotBlank String name,
                @NotNull @PositiveOrZero BigDecimal basePriceBob,
                @NotBlank String taxProductCode,
                @DefaultValue("58") @Positive Integer taxUnitCode) {
        }
    }

    /**
     * Adaptador hacia el sistema fiscal.
     *
     * @param mode        estrategia activa
     * @param demoCaption leyenda impresa en toda factura del entorno local
     * @param demoPrefix  prefijo de los codigos generados, para que nadie los confunda con reales
     */
    public record TaxAuthority(
            @DefaultValue("SIMULATED") @NotNull TaxAuthorityMode mode,
            @DefaultValue("NO VALIDA TRIBUTARIAMENTE - DOCUMENTO DE DEMOSTRACION SIN VALOR FISCAL")
            @NotBlank String demoCaption,
            @DefaultValue("DEMO") @NotBlank String demoPrefix) {
    }

    /**
     * Reglas propias de emision.
     *
     * @param economicActivityCode    CAEB por defecto del detalle mientras no exista economic_activity
     * @param fallbackTaxProductCode  codigo de producto usado si la prestacion no declara el suyo
     * @param demoSetup               configuracion fiscal creada automaticamente en el entorno local
     */
    public record Billing(
            @DefaultValue("861020") @NotBlank String economicActivityCode,
            @DefaultValue("99100") @NotBlank String fallbackTaxProductCode,
            @Valid @DefaultValue @NotNull DemoSetup demoSetup) {

        /**
         * Configuracion fiscal de demostracion.
         *
         * <p>Con {@code enabled=true} el servicio crea por si mismo la configuracion, el punto de
         * venta y el CUIS/CUFD simulados de una organizacion que todavia no los tiene. Es lo que
         * permite que la rebanada vertical funcione desde una base vacia sin ningun paso manual.
         * En un entorno real se desactiva y la configuracion la carga un administrador.
         *
         * @param enabled          crea la configuracion faltante al emitir
         * @param nit              NIT ficticio del entorno local
         * @param legalName        razon social ficticia
         * @param pointOfSaleName  nombre de la caja creada
         * @param taxBranchCode    codigo de sucursal del SIN; 0 es casa matriz
         * @param pointOfSaleCode  codigo del punto de venta dentro de la sucursal
         * @param address          direccion impresa que acompana al CUFD
         * @param cuisValidity     vigencia del CUIS simulado
         * @param cufdValidity     vigencia del CUFD simulado
         */
        public record DemoSetup(
                @DefaultValue("true") boolean enabled,
                @DefaultValue("0000000000") @NotBlank String nit,
                @DefaultValue("CLINICA DEMO MEDICALSYS") @NotBlank String legalName,
                @DefaultValue("CAJA DEMO") @NotBlank String pointOfSaleName,
                @DefaultValue("0") @PositiveOrZero int taxBranchCode,
                @DefaultValue("1") @PositiveOrZero int pointOfSaleCode,
                @DefaultValue("DIRECCION DE DEMOSTRACION - ENTORNO LOCAL") @NotBlank String address,
                @DefaultValue("365d") @NotNull Duration cuisValidity,
                @DefaultValue("24h") @NotNull Duration cufdValidity) {
        }
    }
}
