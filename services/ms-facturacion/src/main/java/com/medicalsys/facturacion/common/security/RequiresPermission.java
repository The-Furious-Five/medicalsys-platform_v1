package com.medicalsys.facturacion.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declara el permiso del catalogo de ms-identidad que exige un endpoint (RBAC-03).
 *
 * <p>El gateway valida el JWT y propaga sus permisos en X-Permissions; este servicio solo comprueba
 * que el permiso este entre ellos. Que rol lo tiene se decide en ms-identidad y se cambia desde su
 * API, sin redesplegar. Sin la anotacion, el endpoint se deniega por defecto (RBAC-04), salvo
 * que se declare {@link PublicEndpoint} o {@link InternalEndpoint}.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /** Codigo modulo.recurso.accion, tal como figura en la hoja endpoint -> permiso de RBAC-01. */
    String value();
}
