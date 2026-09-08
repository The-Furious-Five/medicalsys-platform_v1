package com.medicalsys.auditoria.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Resumen de una ventana de tiempo para el encabezado de la pantalla de auditoria.
 *
 * <p>Responde de un vistazo las preguntas con las que empieza toda revision: cuanto se hizo, cuanto
 * se rechazo, que servicios participaron y quien fue el usuario mas activo. El detalle se pide
 * despues, ya con un filtro.
 *
 * @param totalEvents  eventos asentados en la ventana
 * @param deniedEvents intentos rechazados; una subida repentina es la primera senal de un problema
 * @param failedEvents operaciones que terminaron en error
 * @param criticalEvents eventos marcados como criticos por su productor
 */
public record AuditSummaryResponse(
        Instant from,
        Instant to,
        long totalEvents,
        long deniedEvents,
        long failedEvents,
        long criticalEvents,
        List<CountByKey> byCategory,
        List<CountByKey> byOutcome,
        List<CountByKey> bySeverity,
        List<CountByKey> byService,
        List<ActorActivity> topActors) {

    /** Par etiqueta/conteo usado por los cortes del resumen. */
    public record CountByKey(String key, long total) {
    }

    /** Actividad de un usuario dentro de la ventana consultada. */
    public record ActorActivity(UUID actorUserId, String actorDisplayName, long total) {
    }
}
