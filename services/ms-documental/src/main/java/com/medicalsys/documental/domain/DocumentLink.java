package com.medicalsys.documental.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(
        name = "document_link",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_document_link_document_id_entity_type_linked_enti_key",
                columnNames = {"document_id", "entity_type", "linked_entity_public_id"}))
public class DocumentLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private StoredDocument document;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 30)
    private LinkedEntityType entityType;

    @Column(name = "linked_entity_public_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID linkedEntityPublicId;

    @Column(name = "description", length = 250)
    private String description;

    @Column(name = "linked_by_user_public_id", columnDefinition = "BINARY(16)")
    private UUID linkedByUserPublicId;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected DocumentLink() {
        // Requerido por JPA.
    }

    public DocumentLink(StoredDocument document, LinkedEntityType entityType, UUID linkedEntityPublicId,
            String description, UUID linkedByUserPublicId) {
        this.document = Objects.requireNonNull(document, "document");
        this.entityType = Objects.requireNonNull(entityType, "entityType");
        this.linkedEntityPublicId = Objects.requireNonNull(linkedEntityPublicId, "linkedEntityPublicId");
        this.description = description;
        this.linkedByUserPublicId = linkedByUserPublicId;
    }

    public Long getId() {
        return id;
    }

    public StoredDocument getDocument() {
        return document;
    }

    public LinkedEntityType getEntityType() {
        return entityType;
    }

    public UUID getLinkedEntityPublicId() {
        return linkedEntityPublicId;
    }

    public String getDescription() {
        return description;
    }

    public UUID getLinkedByUserPublicId() {
        return linkedByUserPublicId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
