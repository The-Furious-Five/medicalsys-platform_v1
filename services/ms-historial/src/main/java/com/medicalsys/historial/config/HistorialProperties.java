package com.medicalsys.historial.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuracion propia de ms-historial. Toda dependencia externa se parametriza por variable de
 * entorno: el codigo nunca contiene una URL fija ni un secreto.
 *
 * @param crypto    integracion con ms-criptografia
 * @param catalogs  integracion con ms-catalogos
 * @param numbering correlativos legibles por tenant
 */
@Validated
@ConfigurationProperties(prefix = "medicalsys")
public record HistorialProperties(
        @Valid @NotNull Crypto crypto,
        @Valid @NotNull Catalogs catalogs,
        @Valid @NotNull Numbering numbering) {

    /** Estrategia de cifrado de la narrativa clinica. */
    public enum CryptoMode {
        /** AES-256-GCM en proceso con DEMO_MASTER_KEY. Solo para el entorno local del MVP. */
        LOCAL,
        /** Delega cifrado y descifrado en ms-criptografia por HTTP. */
        REMOTE
    }

    /** Estrategia de validacion de codigos de catalogo. */
    public enum CatalogValidationMode {
        /** Acepta cualquier codigo con formato valido. Solo para depuracion. */
        PERMISSIVE,
        /** Valida formato y pertenencia a la lista local de codigos de demostracion. */
        LOCAL,
        /** Consulta ms-catalogos por HTTP. */
        REMOTE
    }

    /**
     * @param mode           estrategia activa
     * @param baseUrl        direccion interna de ms-criptografia
     * @param demoMasterKey  clave local de demostracion; vacia genera una efimera por arranque
     * @param keyPublicId    UUID de la clave declarada en cryptography_db
     * @param connectTimeout timeout de conexion; una espera infinita convierte una caida en bloqueo
     * @param readTimeout    timeout de lectura
     */
    public record Crypto(
            @DefaultValue("LOCAL") @NotNull CryptoMode mode,
            @NotBlank String baseUrl,
            @DefaultValue("") String demoMasterKey,
            @NotNull UUID keyPublicId,
            @DefaultValue("") String sharedSecret,
            @DefaultValue("2s") @NotNull Duration connectTimeout,
            @DefaultValue("3s") @NotNull Duration readTimeout) {
    }

    /**
     * @param validationMode estrategia activa
     * @param baseUrl        direccion interna de ms-catalogos
     * @param connectTimeout timeout de conexion
     * @param readTimeout    timeout de lectura
     */
    public record Catalogs(
            @DefaultValue("LOCAL") @NotNull CatalogValidationMode validationMode,
            @NotBlank String baseUrl,
            @DefaultValue("2s") @NotNull Duration connectTimeout,
            @DefaultValue("3s") @NotNull Duration readTimeout,
            @Valid @DefaultValue @NotNull Local local) {

        /**
         * Codigos aceptados mientras ms-catalogos no publique su endpoint de consulta.
         *
         * <p>Son los mismos valores ficticios sembrados por ms-catalogos en el entorno local. Al
         * cambiar a REMOTE esta lista deja de usarse.
         *
         * @param icd10Codes      codigos CIE-10 de demostracion
         * @param studyTypeCodes  tipos de estudio consumidos por historial
         * @param medicationCodes medicamentos del vademecum de demostracion
         */
        public record Local(
                @DefaultValue({"J00", "M54.5", "R51", "S52.50"}) @NotEmpty Set<String> icd10Codes,
                @DefaultValue({"X_RAY", "LAB_HEMOGRAM"}) @NotEmpty Set<String> studyTypeCodes,
                @DefaultValue({"PARACETAMOL_500", "IBUPROFENO_400"}) @NotEmpty Set<String> medicationCodes) {
        }
    }

    /**
     * @param recordNumberPrefix       prefijo del correlativo de expediente
     * @param prescriptionFolioPrefix  prefijo del folio de receta
     * @param maxAttempts              reintentos ante colision de correlativo bajo concurrencia
     */
    public record Numbering(
            @DefaultValue("EXP") @NotBlank String recordNumberPrefix,
            @DefaultValue("REC") @NotBlank String prescriptionFolioPrefix,
            @DefaultValue("5") @Positive int maxAttempts) {
    }
}
