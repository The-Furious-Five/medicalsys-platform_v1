package com.medicalsys.tenencia.domain.enums;

/**
 * Tipo de organizacion ({@code organization.type}).
 *
 * <p>Valores controlados del diccionario, capitulo 3. La columna es VARCHAR con CHECK en
 * tenancy_db, de modo que el motor rechaza cualquier valor fuera de esta lista.
 */
public enum OrganizationType {
    PRIVATE_CLINIC,
    PUBLIC_CLINIC,
    OFFICE,
    INDEPENDENT_PHYSICIAN,
    DIAGNOSTIC_CENTER
}
