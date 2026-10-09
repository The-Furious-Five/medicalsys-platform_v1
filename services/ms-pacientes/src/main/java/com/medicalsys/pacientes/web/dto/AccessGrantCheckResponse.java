package com.medicalsys.pacientes.web.dto;

/** Contrato interno que consulta ms-historial antes de dejar leer un expediente entre tenants. */
public record AccessGrantCheckResponse(boolean granted) {
}
