package com.medicalsys.historial.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Autorizacion del paciente para compartir su historial con otras organizaciones.
 *
 * @param sharesWithNetwork true si el paciente autoriza compartir
 */
public record UpdateSharingRequest(
        @NotNull(message = "sharesWithNetwork es obligatorio") Boolean sharesWithNetwork) {
}
