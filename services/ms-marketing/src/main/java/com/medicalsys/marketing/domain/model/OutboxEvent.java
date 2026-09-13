package com.medicalsys.marketing.domain.model;

import com.medicalsys.marketing.domain.enums.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Evento saliente pendiente de publicar ({@code outbox_event}).
 *
 * <p>Patron Transactional Outbox: el evento se guarda en la <b>misma transaccion</b> que el cambio
 * que lo produce. Sin eso, lanzar una campana y publicar {@code marketing.campana} serian dos
 * escrituras independientes y una caida entre ambas dejaria una campana en ejecucion de la que
 * ms-notificaciones nunca se entera.
 *
 * <p>El MVP de la plataforma todavia no tiene broker. La tabla existe igual y el relay la drena
 * segun {@code MARKETING_OUTBOX_MODE}: el dia que aparezca el broker, se cambia el relay y ningun
 * caso de uso se entera.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    /** Tope del texto de error, igual que la columna. */
    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID eventPublicId;

    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 60)
    private String aggregateType;

    @Column(name = "aggregate_public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID aggregatePublicId;

    /** Clave de particion: garantiza el orden por agregado cuando exista un broker. */
    @Column(name = "partition_key", nullable = false, updatable = false, length = 80)
    private String partitionKey;

    @Column(name = "event_type", nullable = false, updatable = false, length = 80)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "json")
    private String payload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "headers", updatable = false, columnDefinition = "json")
    private String headers;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxStatus status;

    @Column(name = "attempts", nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    private int attempts;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
        // Requerido por JPA.
    }

    /** Encola un evento pendiente. */
    public static OutboxEvent pending(String aggregateType, UUID aggregatePublicId, String partitionKey,
            String eventType, String payload, String headers) {
        OutboxEvent event = new OutboxEvent();
        event.eventPublicId = UUID.randomUUID();
        event.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType");
        event.aggregatePublicId = Objects.requireNonNull(aggregatePublicId, "aggregatePublicId");
        event.partitionKey = Objects.requireNonNull(partitionKey, "partitionKey");
        event.eventType = Objects.requireNonNull(eventType, "eventType");
        event.payload = Objects.requireNonNull(payload, "payload");
        event.headers = headers;
        event.status = OutboxStatus.PENDING;
        event.attempts = 0;
        return event;
    }

    /** Marca el evento como publicado. */
    public void markPublished(Instant at) {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = at;
        this.attempts++;
        this.lastError = null;
    }

    /** Marca un intento fallido; el relay volvera a tomarlo. */
    public void markFailed(String error) {
        this.status = OutboxStatus.ERROR;
        this.attempts++;
        this.lastError = error == null ? null
                : error.substring(0, Math.min(error.length(), MAX_ERROR_LENGTH));
    }

    public Long getId() {
        return id;
    }

    public UUID getEventPublicId() {
        return eventPublicId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregatePublicId() {
        return aggregatePublicId;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public String getHeaders() {
        return headers;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
