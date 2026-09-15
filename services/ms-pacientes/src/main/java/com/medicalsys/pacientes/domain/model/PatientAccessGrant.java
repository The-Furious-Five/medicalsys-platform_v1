package com.medicalsys.pacientes.domain.model;

import com.medicalsys.pacientes.domain.enums.AccessGrantStatus;
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
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Permiso de un tenant distinto al que registró al paciente para ver su ficha completa y su
 * historial clínico. Solo lo otorga el propio paciente, confirmando un código OTP enviado a su
 * WhatsApp — nunca la clínica que lo pide (eso sería el mismo hueco que dejó sin usar
 * {@code medical_record.shares_with_network} en ms-historial: un interruptor que activa quien pide
 * el dato, no quien es dueño de él).
 */
@Entity
@Table(name = "patient_access_grant")
public class PatientAccessGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, updatable = false)
    private Patient patient;

    @Column(name = "requesting_organization_public_id", nullable = false, updatable = false,
            columnDefinition = "BINARY(16)")
    private UUID requestingOrganizationPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccessGrantStatus status;

    @Column(name = "requested_by_user_public_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID requestedByUserPublicId;

    @Generated(event = EventType.INSERT)
    @Column(name = "requested_at", insertable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "otp_code_hash", nullable = false, length = 64)
    private String otpCodeHash;

    @Column(name = "otp_expires_at", nullable = false)
    private Instant otpExpiresAt;

    @Column(name = "otp_attempts", nullable = false)
    private short otpAttempts;

    @Column(name = "authorized_at")
    private Instant authorizedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected PatientAccessGrant() {
        // Requerido por JPA.
    }

    /**
     * Genera su propio {@code publicId} (no lo recibe) porque el OTP se hashea salado con ese mismo
     * id: el llamador necesita leerlo de vuelta con {@link #getPublicId()} antes de poder calcular
     * el hash, asi que el codigo se fija despues con {@link #updateOtp} — nunca queda persistido sin
     * OTP, solo en memoria entre la construccion y el primer {@code save}.
     */
    public static PatientAccessGrant request(Patient patient, UUID requestingOrganizationPublicId,
            UUID requestedByUserPublicId) {
        PatientAccessGrant grant = new PatientAccessGrant();
        grant.publicId = UUID.randomUUID();
        grant.patient = Objects.requireNonNull(patient, "patient");
        grant.requestingOrganizationPublicId =
                Objects.requireNonNull(requestingOrganizationPublicId, "requestingOrganizationPublicId");
        grant.requestedByUserPublicId = requestedByUserPublicId;
        grant.otpAttempts = 0;
        grant.status = AccessGrantStatus.REQUESTED;
        return grant;
    }

    public void updateOtp(String otpCodeHash, Instant otpExpiresAt) {
        this.otpCodeHash = Objects.requireNonNull(otpCodeHash, "otpCodeHash");
        this.otpExpiresAt = Objects.requireNonNull(otpExpiresAt, "otpExpiresAt");
        this.otpAttempts = 0;
    }

    public boolean isOtpExpired(Instant now) {
        return now.isAfter(otpExpiresAt);
    }

    public boolean isOtpMatch(String candidateHash) {
        return otpCodeHash.equals(candidateHash);
    }

    public void recordFailedAttempt() {
        otpAttempts++;
    }

    public void authorize() {
        if (status != AccessGrantStatus.REQUESTED) {
            throw new IllegalStateException("El permiso no esta pendiente de confirmacion: " + status);
        }
        this.status = AccessGrantStatus.AUTHORIZED;
        this.authorizedAt = Instant.now();
    }

    public void revoke() {
        if (status != AccessGrantStatus.AUTHORIZED) {
            throw new IllegalStateException("Solo un permiso autorizado puede revocarse");
        }
        this.status = AccessGrantStatus.REVOKED;
        this.revokedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public Patient getPatient() {
        return patient;
    }

    public UUID getRequestingOrganizationPublicId() {
        return requestingOrganizationPublicId;
    }

    public AccessGrantStatus getStatus() {
        return status;
    }

    public UUID getRequestedByUserPublicId() {
        return requestedByUserPublicId;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public int getOtpAttempts() {
        return otpAttempts;
    }

    public Instant getAuthorizedAt() {
        return authorizedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
