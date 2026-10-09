package com.medicalsys.tenencia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ms-tenencia: organizacion, sucursal, prestaciones y precios.
 *
 * <p>Unico propietario de escritura de {@code tenancy_db}. No guarda citas, facturas ni datos
 * clinicos, y no declara ninguna clave foranea hacia otra base.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class TenenciaApplication {

	public static void main(String[] args) {
		SpringApplication.run(TenenciaApplication.class, args);
	}

}
