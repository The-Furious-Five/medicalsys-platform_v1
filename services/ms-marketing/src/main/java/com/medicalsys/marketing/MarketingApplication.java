package com.medicalsys.marketing;

import com.medicalsys.marketing.config.MarketingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ms-marketing: campanas, segmentacion de audiencias, cupones y fidelizacion.
 *
 * <p>Unico propietario de escritura de {@code marketing_db}. Es el servicio que <b>menos</b> debe
 * saber de la plataforma: su base no contiene ni una tabla clinica, no tiene credenciales de
 * medical_records_db y no existe ningun cliente HTTP hacia ms-historial. La segmentacion se evalua
 * solo sobre datos administrativos y demograficos -- edad, sexo, ciudad, ultima visita, servicios
 * facturados -- porque cruzar patologia con publicidad seria una filtracion de dato sensible.
 *
 * <p>Criterios de descomposicion que justifica: C5 (opcionalidad, {@code incluye_marketing} admite
 * 0) y C3 (frontera de privacidad).
 */
@SpringBootApplication
@EnableConfigurationProperties(MarketingProperties.class)
@EnableScheduling
public class MarketingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketingApplication.class, args);
    }
}
