package com.medicalsys.facturacion;

import com.medicalsys.facturacion.config.BillingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * ms-facturacion: factura, detalle, correlativo y estado de demostracion.
 *
 * <p>Unico propietario de escritura de {@code billing_db}. No guarda narrativa clinica ni ninguna
 * tabla del expediente: la unica trazabilidad hacia la atencion son los UUID de cita y encuentro
 * dentro del detalle, sin clave foranea. Tampoco usa credenciales del SIN: el adaptador fiscal de
 * este sprint es un stub determinista sin red y toda factura lleva la leyenda no tributaria.
 */
@SpringBootApplication
@EnableConfigurationProperties(BillingProperties.class)
public class FacturacionApplication {

    public static void main(String[] args) {
        SpringApplication.run(FacturacionApplication.class, args);
    }
}
