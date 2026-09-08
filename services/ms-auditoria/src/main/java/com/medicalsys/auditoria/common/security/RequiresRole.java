package com.medicalsys.auditoria.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declara los roles autorizados para un endpoint.
 *
 * <p>El gateway valida la firma del JWT; cada servicio sigue aplicando la autorizacion de rol y de
 * organizacion (guia Sprint 2, seccion 5.3). Sin la anotacion, el endpoint solo exige identidad.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRole {

    /** Roles aceptados; basta con poseer uno. */
    Role[] value();
}
