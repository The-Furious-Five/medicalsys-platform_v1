package com.medicalsys.facturacion.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Contador de correlativos por punto de venta y tipo de documento ({@code document_sequence}).
 *
 * <p>Es una tabla y no un {@code AUTO_INCREMENT} porque el correlativo fiscal debe poder auditarse y
 * reiniciarse por punto de venta. El servicio la lee con bloqueo pesimista dentro de la transaccion
 * de emision: dos cajas simultaneas se serializan sobre esta fila y ninguna repite numero.
 */
@Entity
@Table(name = "document_sequence")
public class DocumentSequence {

    @EmbeddedId
    private DocumentSequenceId id;

    @Column(name = "last_number", nullable = false)
    private long lastNumber;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected DocumentSequence() {
        // Requerido por JPA.
    }

    /** Crea el contador con el primer numero ya consumido. */
    public static DocumentSequence startingAt(DocumentSequenceId id, long firstNumber) {
        DocumentSequence sequence = new DocumentSequence();
        sequence.id = Objects.requireNonNull(id, "id");
        sequence.lastNumber = firstNumber;
        return sequence;
    }

    /**
     * Consume y devuelve el siguiente numero.
     *
     * <p>Solo debe llamarse con la fila bloqueada; en caso contrario dos transacciones podrian leer
     * el mismo {@code lastNumber}.
     */
    public long nextNumber() {
        return ++lastNumber;
    }

    public DocumentSequenceId getId() {
        return id;
    }

    public long getLastNumber() {
        return lastNumber;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
