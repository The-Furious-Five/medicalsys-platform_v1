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
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

/**
 * Bitacora del consentimiento (quien vio, quien pidio un codigo, quien firmo). También es donde vive
 * el codigo OTP mientras esta pendiente: no hay columna propia para eso en {@code informed_consent}
 * a proposito (la migracion ya estaba cerrada), asi que el evento {@code OTP_REQUESTED} guarda su
 * hash, vencimiento e intentos en {@code detail} y se actualiza in-place en cada intento fallido —
 * la unica mutacion que sufre un evento de esta bitacora despues de creado.
 */
@Entity
@Table(name = "consent_event")
public class ConsentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "informed_consent_id", nullable = false)
    private InformedConsent informedConsent;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private ConsentEventType type;

    @Generated(event = EventType.INSERT)
    @Column(name = "occurred_at", insertable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "user_account_public_id", columnDefinition = "BINARY(16)")
    private UUID userAccountPublicId;

    @Column(name = "ip_origin")
    private byte[] ipOrigin;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail", columnDefinition = "json")
    private Map<String, Object> detail;

    protected ConsentEvent() {
        // Requerido por JPA.
    }

    public ConsentEvent(InformedConsent informedConsent, ConsentEventType type, UUID userAccountPublicId,
            byte[] ipOrigin, String userAgent, Map<String, Object> detail) {
        this.informedConsent = Objects.requireNonNull(informedConsent, "informedConsent");
        this.type = Objects.requireNonNull(type, "type");
        this.userAccountPublicId = userAccountPublicId;
        this.ipOrigin = ipOrigin;
        this.userAgent = userAgent;
        this.detail = detail == null || detail.isEmpty() ? null : Map.copyOf(detail);
    }

    /** Unica mutacion permitida: registrar un intento fallido de OTP sobre el mismo evento. */
    public void updateDetail(Map<String, Object> detail) {
        this.detail = detail == null || detail.isEmpty() ? null : Map.copyOf(detail);
    }

    public Long getId() {
        return id;
    }

    public InformedConsent getInformedConsent() {
        return informedConsent;
    }

    public ConsentEventType getType() {
        return type;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getUserAccountPublicId() {
        return userAccountPublicId;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Map<String, Object> getDetail() {
        return detail;
    }
}
