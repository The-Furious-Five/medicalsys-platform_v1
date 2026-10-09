package com.medicalsys.tenencia.domain.model;

import com.medicalsys.tenencia.domain.enums.Department;
import com.medicalsys.tenencia.domain.enums.OrganizationStatus;
import com.medicalsys.tenencia.domain.enums.OrganizationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Tenant propietario de los datos operativos y de la configuracion ({@code organization}).
 *
 * <p>Es la raiz de todo este servicio: sucursales, prestaciones, suscripcion y consumo cuelgan de
 * ella mediante FK local. Hacia afuera la organizacion se identifica siempre por
 * {@link #getPublicId()}, que es el {@code organizationId} que el gateway deriva del JWT y que las
 * otras ocho bases guardan como referencia externa sin FK.
 *
 * <p>El {@code id} numerico no sale nunca del servicio: filtrarlo permitiria enumerar tenants.
 */
@Entity
@Table(name = "organization")
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @Column(name = "code", nullable = false, updatable = false, length = 30)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private OrganizationType type;

    @Column(name = "legal_name", nullable = false, length = 180)
    private String legalName;

    @Column(name = "trade_name", nullable = false, length = 180)
    private String tradeName;

    @Column(name = "nit", nullable = false, length = 15)
    private String nit;

    @Column(name = "trade_registry_number", length = 40)
    private String tradeRegistryNumber;

    @Column(name = "contact_email", nullable = false, length = 160)
    private String contactEmail;

    @Column(name = "phone_e164", length = 20)
    private String phoneE164;

    @Column(name = "city", length = 80)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(name = "department", length = 20)
    private Department department;

    @Column(name = "time_zone", nullable = false, length = 40)
    private String timeZone;

    @Column(name = "currency", nullable = false, columnDefinition = "CHAR(3)")
    private String currency;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrganizationStatus status;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Organization() {
        // Requerido por JPA.
    }

    /**
     * Da de alta un tenant en estado {@link OrganizationStatus#ONBOARDING}.
     *
     * <p>El {@code publicId} lo genera el servicio, no el cliente: es la identidad con la que el
     * resto de la plataforma referenciara a esta organizacion para siempre.
     *
     * @param code         slug del tenant, usado en el subdominio; se normaliza a mayusculas
     * @param type         tipo de organizacion
     * @param legalName    razon social
     * @param tradeName    nombre comercial
     * @param nit          NIT registrado en el SIN
     * @param contactEmail correo de contacto administrativo
     * @param timeZone     zona horaria de operacion, por ejemplo America/La_Paz
     * @param currency     moneda ISO-4217 de los precios, por ejemplo BOB
     */
    public static Organization onboard(String code, OrganizationType type, String legalName, String tradeName,
            String nit, String contactEmail, String timeZone, String currency) {
        Organization organization = new Organization();
        organization.publicId = UUID.randomUUID();
        organization.code = normalizeCode(code);
        organization.type = Objects.requireNonNull(type, "type");
        organization.legalName = Objects.requireNonNull(legalName, "legalName");
        organization.tradeName = Objects.requireNonNull(tradeName, "tradeName");
        organization.nit = Objects.requireNonNull(nit, "nit").trim();
        organization.contactEmail = Objects.requireNonNull(contactEmail, "contactEmail").trim();
        organization.timeZone = Objects.requireNonNull(timeZone, "timeZone");
        organization.currency = Objects.requireNonNull(currency, "currency").toUpperCase(Locale.ROOT);
        organization.status = OrganizationStatus.ONBOARDING;
        return organization;
    }

    /** Normaliza el slug del tenant: se compara y se guarda siempre en mayusculas. */
    public static String normalizeCode(String code) {
        return Objects.requireNonNull(code, "code").trim().toUpperCase(Locale.ROOT);
    }

    /** Actualiza los datos administrativos editables; code, nit y publicId no cambian. */
    public void updateProfile(String legalName, String tradeName, String contactEmail, String phoneE164, String city,
            Department department, String timeZone, String logoUrl, String tradeRegistryNumber) {
        this.legalName = Objects.requireNonNull(legalName, "legalName");
        this.tradeName = Objects.requireNonNull(tradeName, "tradeName");
        this.contactEmail = Objects.requireNonNull(contactEmail, "contactEmail").trim();
        this.phoneE164 = phoneE164;
        this.city = city;
        this.department = department;
        this.timeZone = Objects.requireNonNull(timeZone, "timeZone");
        this.logoUrl = logoUrl;
        this.tradeRegistryNumber = tradeRegistryNumber;
    }

    /** Ubicacion administrativa de la organizacion; ambos campos son opcionales. */
    public void locateAt(String city, Department department) {
        this.city = city;
        this.department = department;
    }

    public void changeStatus(OrganizationStatus newStatus) {
        this.status = Objects.requireNonNull(newStatus, "status");
    }

    public boolean isOperational() {
        return status.isOperational();
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public String getCode() {
        return code;
    }

    public OrganizationType getType() {
        return type;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getTradeName() {
        return tradeName;
    }

    public String getNit() {
        return nit;
    }

    public String getTradeRegistryNumber() {
        return tradeRegistryNumber;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getPhoneE164() {
        return phoneE164;
    }

    public String getCity() {
        return city;
    }

    public Department getDepartment() {
        return department;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public String getCurrency() {
        return currency;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public OrganizationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
