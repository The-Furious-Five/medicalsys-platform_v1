package com.medicalsys.profesionales.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declara que un endpoint no exige identidad ni permiso (RBAC-04).
 *
 * <p>Los endpoints se deniegan por defecto. Solo llevan esta anotacion los que la hoja endpoint ->
 * permiso de RBAC-01 marca como PUBLICO: la comprobacion de estado y los webhooks firmados por el
 * proveedor. El CI rechaza cualquier otro uso.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicEndpoint {
}
