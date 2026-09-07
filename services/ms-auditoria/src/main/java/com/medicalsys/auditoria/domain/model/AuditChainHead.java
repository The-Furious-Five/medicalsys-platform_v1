package com.medicalsys.auditoria.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Punta de la cadena de eventos de una organizacion.
 *
 * <p>Es la unica fila que se actualiza en toda la base: guarda el ultimo numero de secuencia y el
 * ultimo hash emitidos. Existe para que el asiento pueda serializarse con un bloqueo de fila corto
 * en vez de leer el evento mas reciente, que es una consulta que dos peticiones simultaneas pueden
 * responder con el mismo valor y romper la cadena.
 */
@Entity
@Table(name = "audit_chain_head")
public class AuditChainHead {

    @Id
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "last_sequence_number", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long lastSequenceNumber;

    @Column(name = "last_record_hash", length = 64)
    private String lastRecordHash;

    protected AuditChainHead() {
        // Requerido por JPA.
    }

    public static AuditChainHead startFor(UUID organizationId) {
        AuditChainHead head = new AuditChainHead();
        head.organizationId = organizationId;
        head.lastSequenceNumber = 0L;
        head.lastRecordHash = null;
        return head;
    }

    /** Avanza la cadena al evento recien asentado. */
    public void advanceTo(long sequenceNumber, String recordHash) {
        this.lastSequenceNumber = sequenceNumber;
        this.lastRecordHash = recordHash;
    }

    public UUID getOrganizationId() { return organizationId; }
    public Long getLastSequenceNumber() { return lastSequenceNumber; }
    public String getLastRecordHash() { return lastRecordHash; }
}
