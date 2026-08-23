package com.medicalsys.facturacion.domain.model;

import com.medicalsys.facturacion.domain.enums.BillingConfigurationStatus;
import com.medicalsys.facturacion.domain.enums.BillingEnvironment;
import com.medicalsys.facturacion.domain.enums.BillingModality;
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
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Configuracion fiscal de una organizacion ({@code billing_configuration}).
 *
 * <p>Es unica por tenant y decide si esa organizacion puede emitir. En el entorno local nace en
 * ambiente {@link BillingEnvironment#PILOT} y <b>sin token delegado</b>: el MVP no usa credenciales
 * del SIN, de modo que {@code encrypted_delegated_token} permanece nulo.
 */
@Entity
@Table(name = "billing_configuration")
public class BillingConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID organizationPublicId;

    @Column(name = "nit", nullable = false, length = 15)
    private String nit;

    @Column(name = "legal_name", nullable = false, length = 180)
    private String legalName;

    @Enumerated(EnumType.STRING)
    @Column(name = "modality", nullable = false)
    private BillingModality modality;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false)
    private BillingEnvironment environment;

    /**
     * Token delegado del SIN cifrado con la DEK de facturacion.
     *
     * <p>Se mapea para respetar el modelo, pero el MVP nunca lo escribe: no hay credenciales reales
     * en el entorno de capacitacion.
     */
    @Column(name = "encrypted_delegated_token")
    private byte[] encryptedDelegatedToken;

    @Column(name = "token_valid_until")
    private Instant tokenValidUntil;

    @Column(name = "encryption_key_public_id", columnDefinition = "BINARY(16)")
    private UUID encryptionKeyPublicId;

    @Column(name = "digital_certificate_public_id", columnDefinition = "BINARY(16)")
    private UUID digitalCertificatePublicId;

    @Column(name = "vat_caption", length = 300)
    private String vatCaption;

    @Column(name = "notification_email", length = 160)
    private String notificationEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BillingConfigurationStatus status;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected BillingConfiguration() {
        // Requerido por JPA.
    }

    /**
     * Crea la configuracion fiscal de demostracion de una organizacion.
     *
     * @param organizationPublicId tenant propietario
     * @param nit                  NIT ficticio del entorno local
     * @param legalName            razon social ficticia
     * @param vatCaption           leyenda impresa; en el MVP es la leyenda no tributaria
     */
    public static BillingConfiguration demo(UUID organizationPublicId, String nit, String legalName,
            String vatCaption) {
        BillingConfiguration configuration = new BillingConfiguration();
        configuration.organizationPublicId = Objects.requireNonNull(organizationPublicId, "organizationPublicId");
        configuration.nit = Objects.requireNonNull(nit, "nit");
        configuration.legalName = Objects.requireNonNull(legalName, "legalName");
        configuration.vatCaption = vatCaption;
        configuration.modality = BillingModality.ONLINE_COMPUTERIZED;
        configuration.environment = BillingEnvironment.PILOT;
        configuration.status = BillingConfigurationStatus.ACTIVE;
        return configuration;
    }

    public boolean allowsIssuing() {
        return status.allowsIssuing();
    }

    /** Indica si la configuracion pertenece al tenant indicado. */
    public boolean belongsTo(UUID organizationId) {
        return organizationPublicId.equals(organizationId);
    }

    public Long getId() {
        return id;
    }

    public UUID getOrganizationPublicId() {
        return organizationPublicId;
    }

    public String getNit() {
        return nit;
    }

    public String getLegalName() {
        return legalName;
    }

    public BillingModality getModality() {
        return modality;
    }

    public BillingEnvironment getEnvironment() {
        return environment;
    }

    public Instant getTokenValidUntil() {
        return tokenValidUntil;
    }

    public UUID getEncryptionKeyPublicId() {
        return encryptionKeyPublicId;
    }

    public UUID getDigitalCertificatePublicId() {
        return digitalCertificatePublicId;
    }

    public String getVatCaption() {
        return vatCaption;
    }

    public String getNotificationEmail() {
        return notificationEmail;
    }

    public BillingConfigurationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
