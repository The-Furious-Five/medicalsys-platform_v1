package com.medicalsys.identidad.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Cuenta de acceso, credenciales, MFA y estado de bloqueo. */
@Entity
@Table(name = "user_account")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId = UUID.randomUUID();

    /** Usuario del realm medicalsys que guarda la credencial (IAM-03). Nulo hasta enlazar la cuenta. */
    @Column(name = "keycloak_user_id", length = 36, unique = true)
    private String keycloakUserId;

    @Column(name = "organization_public_id")
    private UUID organizationPublicId;

    @Column(name = "email", nullable = false, length = 160)
    private String email;

    @Column(name = "phone_e164", length = 20)
    private String phoneE164;

    @Column(name = "full_name", nullable = false, length = 180)
    private String fullName;

    /**
     * Solo para cuentas aun no enlazadas a Keycloak: al enlazarlas, el hash se importa al realm y
     * se borra de aqui. La credencial vigente vive en Keycloak.
     */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "hash_algorithm", nullable = false, length = 20)
    private String hashAlgorithm = "argon2id";

    @Column(name = "password_updated_at")
    private Instant passwordUpdatedAt;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled;

    @Column(name = "encrypted_mfa_secret", length = 512)
    private byte[] encryptedMfaSecret;

    @Column(name = "encryption_key_public_id")
    private UUID encryptionKeyPublicId;

    @Column(name = "language", nullable = false, length = 5)
    private String language = "es-BO";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.PENDING_ACTIVATION;

    @Column(name = "failed_attempts", nullable = false)
    private Byte failedAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_access_at")
    private Instant lastAccessAt;

    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    @Column(name = "data_policy_accepted_at")
    private Instant dataPolicyAcceptedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "userAccount", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<UserRole> roleAssignments = new ArrayList<>();

    public boolean isCurrentlyLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * Refleja el bloqueo por fuerza bruta de Keycloak (permanente: solo lo levanta un administrador),
     * para que la gestion de usuarios muestre el estado sin consultar Keycloak por cada fila.
     */
    public void markLockedByIdentityProvider() {
        this.status = UserStatus.LOCKED;
        this.lockedUntil = null;
    }

    public void registerSuccessfulAccess(Instant now) {
        this.failedAttempts = 0;
        this.lockedUntil = null;
        this.lastAccessAt = now;
        if (this.status == UserStatus.LOCKED) {
            this.status = UserStatus.ACTIVE;
        }
    }

    public boolean isLinkedToIdentityProvider() {
        return keycloakUserId != null;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public void setPublicId(UUID publicId) {
        this.publicId = publicId;
    }

    public String getKeycloakUserId() {
        return keycloakUserId;
    }

    public void setKeycloakUserId(String keycloakUserId) {
        this.keycloakUserId = keycloakUserId;
    }

    public UUID getOrganizationPublicId() {
        return organizationPublicId;
    }

    public void setOrganizationPublicId(UUID organizationPublicId) {
        this.organizationPublicId = organizationPublicId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneE164() {
        return phoneE164;
    }

    public void setPhoneE164(String phoneE164) {
        this.phoneE164 = phoneE164;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getHashAlgorithm() {
        return hashAlgorithm;
    }

    public void setHashAlgorithm(String hashAlgorithm) {
        this.hashAlgorithm = hashAlgorithm;
    }

    public Instant getPasswordUpdatedAt() {
        return passwordUpdatedAt;
    }

    public void setPasswordUpdatedAt(Instant passwordUpdatedAt) {
        this.passwordUpdatedAt = passwordUpdatedAt;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public void setMfaEnabled(boolean mfaEnabled) {
        this.mfaEnabled = mfaEnabled;
    }

    public byte[] getEncryptedMfaSecret() {
        return encryptedMfaSecret;
    }

    public void setEncryptedMfaSecret(byte[] encryptedMfaSecret) {
        this.encryptedMfaSecret = encryptedMfaSecret;
    }

    public UUID getEncryptionKeyPublicId() {
        return encryptionKeyPublicId;
    }

    public void setEncryptionKeyPublicId(UUID encryptionKeyPublicId) {
        this.encryptionKeyPublicId = encryptionKeyPublicId;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public Byte getFailedAttempts() {
        return failedAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public Instant getLastAccessAt() {
        return lastAccessAt;
    }

    public Instant getTermsAcceptedAt() {
        return termsAcceptedAt;
    }

    public void setTermsAcceptedAt(Instant termsAcceptedAt) {
        this.termsAcceptedAt = termsAcceptedAt;
    }

    public Instant getDataPolicyAcceptedAt() {
        return dataPolicyAcceptedAt;
    }

    public void setDataPolicyAcceptedAt(Instant dataPolicyAcceptedAt) {
        this.dataPolicyAcceptedAt = dataPolicyAcceptedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<UserRole> getRoleAssignments() {
        return roleAssignments;
    }
}
