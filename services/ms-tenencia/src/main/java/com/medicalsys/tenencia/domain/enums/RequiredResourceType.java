package com.medicalsys.tenencia.domain.enums;

/**
 * Recurso fisico que la prestacion necesita para agendarse
 * ({@code service_offering.required_resource_type}).
 *
 * <p>ms-agenda lee este valor para elegir que {@code physical_resource} reservar. ms-tenencia
 * declara la necesidad; no conoce los recursos concretos, que pertenecen a scheduling_db.
 */
public enum RequiredResourceType {
    NONE,
    OFFICE,
    OPERATING_ROOM,
    PROCEDURE_ROOM,
    EQUIPMENT
}
