package com.medicalsys.identidad.audit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion del envio de auditoria hacia ms-auditoria.
 *
 * <p>Si {@code baseUrl} queda vacio, el envio se desactiva por completo. Esa es la situacion de
 * una prueba o de un arranque suelto del servicio: no tiene sentido que ms-identidad falle porque
 * no encuentra una bitacora que nadie levanto.
 *
 * @param baseUrl        raiz de ms-auditoria dentro de la red interna
 * @param connectTimeout tiempo maximo para establecer la conexion
 * @param readTimeout    tiempo maximo de espera de la respuesta
 * @param queueCapacity  eventos que pueden quedar en cola antes de aplicar contrapresion
 */
@ConfigurationProperties(prefix = "medicalsys.audit")
public record AuditTrailProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Integer queueCapacity) {

    public AuditTrailProperties {
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
        queueCapacity = queueCapacity == null ? 500 : queueCapacity;
    }

    public boolean isEnabled() {
        return baseUrl != null && !baseUrl.isBlank();
    }
}
