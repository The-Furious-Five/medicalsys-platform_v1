package com.medicalsys.tenencia.domain.model;

import com.medicalsys.tenencia.common.Money;
import com.medicalsys.tenencia.domain.enums.RequiredResourceType;
import com.medicalsys.tenencia.domain.enums.ServiceOfferingType;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Prestacion que una organizacion ofrece y puede agendar o facturar ({@code service_offering}).
 *
 * <p>Es la pieza que ms-tenencia publica a los demas servicios: ms-agenda toma
 * {@link #getDurationMinutes()} y {@link #getRequiredResourceType()} para reservar el hueco y el
 * recurso, y ms-facturacion toma {@link #getBasePriceBob()} para el detalle de la factura.
 *
 * <p>El precio se publica desde aqui y nunca llega en el cuerpo de quien factura: si el emisor
 * pudiera enviarlo, podria elegir cuanto cobra.
 *
 * <p>{@code specialty_code} apunta a {@code specialty.code} de catalogs_db y
 * {@code consent_template_public_id} a {@code consent_template.public_id} de documents_db. Ambas
 * son referencias externas sin FK, tal como fija la matriz del capitulo 12 del diccionario.
 */
@Entity
@Table(name = "service_offering")
public class ServiceOffering {

    /** Duracion minima aceptada por el CHECK {@code ck_service_offering_duration_minutes}. */
    public static final int MIN_DURATION_MINUTES = 5;

    /** Duracion maxima aceptada por el CHECK {@code ck_service_offering_duration_minutes}. */
    public static final int MAX_DURATION_MINUTES = 720;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @Column(name = "code", nullable = false, updatable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private ServiceOfferingType type;

    /** Referencia externa a {@code specialty.code} de catalogs_db; sin FK. */
    @Column(name = "specialty_code", length = 20)
    private String specialtyCode;

    @Column(name = "duration_minutes", nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    private Integer durationMinutes;

    @Column(name = "base_price_bob", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePriceBob;

    @Column(name = "requires_consent", nullable = false)
    private boolean requiresConsent;

    /** Referencia externa a {@code consent_template.public_id} de documents_db; sin FK. */
    @Column(name = "consent_template_public_id", columnDefinition = "BINARY(16)")
    private UUID consentTemplatePublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_resource_type", nullable = false, length = 30)
    private RequiredResourceType requiredResourceType;

    /** Codigo de producto/servicio homologado del SIN; lo consume ms-facturacion. */
    @Column(name = "tax_product_code", length = 30)
    private String taxProductCode;

    /** Unidad de medida del catalogo del SIN; 58 corresponde a unidad. */
    @Column(name = "tax_unit_code", length = 10)
    private String taxUnitCode;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected ServiceOffering() {
        // Requerido por JPA.
    }

    /**
     * Publica una prestacion activa en el catalogo de la organizacion.
     *
     * @param organization    tenant propietario del catalogo
     * @param code            codigo de la prestacion, unico dentro de la organizacion
     * @param name            descripcion que se imprime en el detalle de la factura
     * @param type            tipo de prestacion
     * @param durationMinutes duracion que ms-agenda reserva
     * @param basePriceBob    precio unitario en bolivianos; DECIMAL, nunca coma flotante
     */
    public static ServiceOffering publish(Organization organization, String code, String name,
            ServiceOfferingType type, Integer durationMinutes, BigDecimal basePriceBob) {
        ServiceOffering offering = new ServiceOffering();
        offering.publicId = UUID.randomUUID();
        offering.organization = Objects.requireNonNull(organization, "organization");
        offering.code = normalizeCode(code);
        offering.name = Objects.requireNonNull(name, "name");
        offering.type = Objects.requireNonNull(type, "type");
        offering.durationMinutes = durationMinutes == null ? 30 : durationMinutes;
        offering.basePriceBob = Money.amount(basePriceBob);
        offering.requiresConsent = false;
        offering.requiredResourceType = RequiredResourceType.OFFICE;
        offering.active = true;
        return offering;
    }

    /** Normaliza el codigo de prestacion: se compara y se guarda siempre en mayusculas. */
    public static String normalizeCode(String code) {
        return Objects.requireNonNull(code, "code").trim().toUpperCase(Locale.ROOT);
    }

    /** Actualiza la ficha comercial de la prestacion; code, publicId y organizacion no cambian. */
    public void updateDetails(String name, String description, ServiceOfferingType type, String specialtyCode,
            Integer durationMinutes, BigDecimal basePriceBob, RequiredResourceType requiredResourceType,
            String taxProductCode, String taxUnitCode) {
        this.name = Objects.requireNonNull(name, "name");
        this.description = description;
        this.type = Objects.requireNonNull(type, "type");
        this.specialtyCode = specialtyCode;
        this.durationMinutes = durationMinutes == null ? this.durationMinutes : durationMinutes;
        this.basePriceBob = Money.amount(basePriceBob);
        this.requiredResourceType = requiredResourceType == null ? this.requiredResourceType : requiredResourceType;
        this.taxProductCode = taxProductCode;
        this.taxUnitCode = taxUnitCode;
    }

    /**
     * Declara si la prestacion exige consentimiento informado y con que plantilla.
     *
     * <p>La plantilla vive en documents_db; aqui solo se guarda su UUID publico.
     */
    public void requireConsent(boolean required, UUID consentTemplatePublicId) {
        this.requiresConsent = required;
        this.consentTemplatePublicId = required ? consentTemplatePublicId : null;
    }

    /** Activa o desactiva la prestacion; desactivar no la borra, para no romper facturas emitidas. */
    public void changeActive(boolean value) {
        this.active = value;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ServiceOfferingType getType() {
        return type;
    }

    public String getSpecialtyCode() {
        return specialtyCode;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public BigDecimal getBasePriceBob() {
        return basePriceBob;
    }

    public boolean isRequiresConsent() {
        return requiresConsent;
    }

    public UUID getConsentTemplatePublicId() {
        return consentTemplatePublicId;
    }

    public RequiredResourceType getRequiredResourceType() {
        return requiredResourceType;
    }

    public String getTaxProductCode() {
        return taxProductCode;
    }

    public String getTaxUnitCode() {
        return taxUnitCode;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
