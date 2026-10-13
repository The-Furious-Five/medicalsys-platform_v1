package com.medicalsys.facturacion.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declara una ruta entre microservicios que no exige permiso (RBAC-04).
 *
 * <p>El gateway no publica {@code /internal/**}: su proteccion es de red (HARD-01), no de rol. El
 * interceptor solo deja pasar la ruta si de verdad empieza con /internal/, asi que la anotacion no
 * abre por error un endpoint del contrato publico.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InternalEndpoint {
}
