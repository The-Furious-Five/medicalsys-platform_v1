package com.medicalsys.agenda.service;

import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Traduce las violaciones de restricciones de PostgreSQL a errores HTTP con significado.
 *
 * <p>El antisolapamiento no se resuelve leyendo antes de escribir: dos transacciones concurrentes
 * pasarian ambas esa lectura. La verdad la impone el motor con EXCLUDE USING gist, y aqui se
 * convierte esa violacion en un 409.
 */
public final class OverlapTranslator {

    private OverlapTranslator() {
    }

    public static <T> T translate(Supplier<T> action) {
        try {
            return action.get();
        } catch (DataIntegrityViolationException ex) {
            String message = rootMessage(ex);
            if (message.contains("no_professional_overlap")) {
                throw ApiException.conflict(
                        "El profesional ya tiene una cita activa que se cruza con ese horario");
            }
            if (message.contains("no_resource_overlap")) {
                throw ApiException.conflict("El recurso ya esta reservado en ese horario");
            }
            if (message.contains("uq_physical_resource_organization_public_id_code")) {
                throw ApiException.conflict("Ya existe un recurso con ese codigo en la organizacion");
            }
            if (message.contains("uq_schedule_availability")) {
                throw ApiException.conflict(
                        "Ya existe una disponibilidad para ese profesional, dia, hora de inicio y vigencia");
            }
            throw ex;
        }
    }

    private static String rootMessage(Throwable ex) {
        StringBuilder text = new StringBuilder();
        Throwable current = ex;
        while (current != null) {
            if (current.getMessage() != null) {
                text.append(current.getMessage()).append(' ');
            }
            current = current.getCause();
        }
        return text.toString();
    }
}
