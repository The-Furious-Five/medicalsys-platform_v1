package com.medicalsys.marketing.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Inbox de idempotencia ({@code processed_event}).
 *
 * <p>Anotar el UUID del evento ya procesado convierte un transporte at-least-once en un efecto
 * effectively-once. Importa mas de lo que parece: sin esta tabla, reprocesar los eventos de
 * facturacion de un dia duplicaria el gasto acumulado de cada paciente y, con el, la audiencia de
 * cualquier segmento que filtre por gasto.
 */
@Entity
@Table(name = "processed_event")
public class ProcessedEvent {

    @Id
    @Column(name = "event_public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID eventPublicId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 80)
    private String eventType;

    @Column(name = "processed_at", insertable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
        // Requerido por JPA.
    }

    /** Anota un evento como ya aplicado. */
    public static ProcessedEvent of(UUID eventPublicId, String eventType) {
        ProcessedEvent processed = new ProcessedEvent();
        processed.eventPublicId = Objects.requireNonNull(eventPublicId, "eventPublicId");
        processed.eventType = Objects.requireNonNull(eventType, "eventType");
        return processed;
    }

    public UUID getEventPublicId() {
        return eventPublicId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
