package com.medicalsys.tenencia.domain.model;

import com.medicalsys.tenencia.domain.enums.BranchStatus;
import com.medicalsys.tenencia.domain.enums.Department;
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
 * Sede fisica de una organizacion ({@code branch}).
 *
 * <p>Su {@code public_id} viaja a scheduling_db, professionals_db y billing_db como referencia
 * externa sin FK: alli identifica donde ocurre una cita, donde atiende un profesional y a que
 * sucursal fiscal pertenece un punto de venta.
 *
 * <p>{@code code} es unico dentro de la organizacion, no en toda la base: dos clinicas distintas
 * pueden tener su propia sucursal CENTRAL.
 */
@Entity
@Table(name = "branch")
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @Column(name = "code", nullable = false, updatable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "is_headquarters", nullable = false)
    private boolean headquarters;

    @Column(name = "address", nullable = false, length = 250)
    private String address;

    @Column(name = "city", nullable = false, length = 80)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(name = "department", nullable = false, length = 20)
    private Department department;

    @Column(name = "phone_e164", length = 20)
    private String phoneE164;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    /** Horario referencial mostrado al paciente, en JSON; el MVP lo conserva sin interpretarlo. */
    @Column(name = "opening_hours", columnDefinition = "JSON")
    private String openingHours;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BranchStatus status;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Branch() {
        // Requerido por JPA.
    }

    /**
     * Registra una sede activa de la organizacion.
     *
     * @param organization tenant propietario
     * @param code         codigo de la sucursal, unico dentro de la organizacion
     * @param name         nombre visible
     * @param headquarters indica si es la casa matriz
     * @param address      direccion fisica
     * @param city         ciudad
     * @param department   departamento de Bolivia
     */
    public static Branch open(Organization organization, String code, String name, boolean headquarters,
            String address, String city, Department department) {
        Branch branch = new Branch();
        branch.publicId = UUID.randomUUID();
        branch.organization = Objects.requireNonNull(organization, "organization");
        branch.code = normalizeCode(code);
        branch.name = Objects.requireNonNull(name, "name");
        branch.headquarters = headquarters;
        branch.address = Objects.requireNonNull(address, "address");
        branch.city = Objects.requireNonNull(city, "city");
        branch.department = Objects.requireNonNull(department, "department");
        branch.status = BranchStatus.ACTIVE;
        return branch;
    }

    /** Normaliza el codigo de sucursal: se compara y se guarda siempre en mayusculas. */
    public static String normalizeCode(String code) {
        return Objects.requireNonNull(code, "code").trim().toUpperCase(Locale.ROOT);
    }

    /** Actualiza los datos editables de la sede; code, publicId y organizacion no cambian. */
    public void updateDetails(String name, String address, String city, Department department, String phoneE164,
            BigDecimal latitude, BigDecimal longitude, String openingHours) {
        this.name = Objects.requireNonNull(name, "name");
        this.address = Objects.requireNonNull(address, "address");
        this.city = Objects.requireNonNull(city, "city");
        this.department = Objects.requireNonNull(department, "department");
        this.phoneE164 = phoneE164;
        this.latitude = latitude;
        this.longitude = longitude;
        this.openingHours = openingHours;
    }

    public void changeStatus(BranchStatus newStatus) {
        this.status = Objects.requireNonNull(newStatus, "status");
    }

    /** Marca o desmarca esta sede como casa matriz. */
    public void markAsHeadquarters(boolean value) {
        this.headquarters = value;
    }

    public boolean isActive() {
        return status.isActive();
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

    public boolean isHeadquarters() {
        return headquarters;
    }

    public String getAddress() {
        return address;
    }

    public String getCity() {
        return city;
    }

    public Department getDepartment() {
        return department;
    }

    public String getPhoneE164() {
        return phoneE164;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public String getOpeningHours() {
        return openingHours;
    }

    public BranchStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
