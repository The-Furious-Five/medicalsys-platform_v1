package com.medicalsys.historial;

import com.medicalsys.historial.config.HistorialProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * ms-historial: expediente clinico, encuentro, diagnostico, receta, orden y resultado de estudio.
 *
 * <p>Unico propietario de escritura de {@code medical_records_db}. No guarda binarios de archivos
 * (viven en ms-documental) ni narrativa en claro (se cifra con ms-criptografia), y no crea claves
 * foraneas hacia otras bases: las referencias externas son UUID validados por contrato HTTP.
 */
@SpringBootApplication
@EnableConfigurationProperties(HistorialProperties.class)
public class HistorialApplication {

    public static void main(String[] args) {
        SpringApplication.run(HistorialApplication.class, args);
    }
}
