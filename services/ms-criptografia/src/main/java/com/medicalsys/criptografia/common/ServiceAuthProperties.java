package com.medicalsys.criptografia.common;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Secreto compartido que deben presentar los servicios internos para cifrar/descifrar.
 *
 * <p>Antes de esto, {@code requestingService} era un campo de texto libre en el body (o, en el
 * contrato con ms-historial, un literal fijo en el propio controlador): no probaba nada, solo
 * quedaba escrito en la bitacora de uso. Un secreto por servicio (con PKI/mTLS entre microservicios)
 * seria mas fino, pero no existe esa infraestructura en ningun otro lugar de la plataforma; un
 * secreto unico es el paso minimo que deja de aceptar cualquier texto como identidad.
 */
@Validated
@ConfigurationProperties(prefix = "medicalsys.service-auth")
public record ServiceAuthProperties(@NotBlank String sharedSecret) {
}
