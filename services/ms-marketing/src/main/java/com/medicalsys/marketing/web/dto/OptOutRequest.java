package com.medicalsys.marketing.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Alta o baja de la exclusion comercial de un paciente.
 *
 * <p>Existe como ruta propia, y no como un campo mas de la ficha del paciente, porque el opt-out se
 * ejerce contra marketing y debe poder atenderse aunque ms-pacientes este caido. Es tambien la ruta
 * que el portal del paciente usa para el "no quiero recibir promociones".
 *
 * @param optOut true para excluir al paciente de toda audiencia comercial
 */
public record OptOutRequest(@NotNull(message = "es obligatorio") Boolean optOut) {
}
