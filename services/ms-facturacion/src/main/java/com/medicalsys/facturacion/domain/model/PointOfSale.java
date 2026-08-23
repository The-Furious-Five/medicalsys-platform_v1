package com.medicalsys.facturacion.domain.model;

import com.medicalsys.facturacion.domain.enums.PointOfSaleStatus;
import com.medicalsys.facturacion.domain.enums.PointOfSaleType;
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
 * Punto de venta fiscal asociado a una sucursal ({@code point_of_sale}).
 *
 * <p>Es la unidad que ancla el correlativo: {@code document_sequence} y la restriccion
 * {@code uq_invoice_point_of_sale_id_tax_document_type_invoice_number} cuelgan de el. Por eso dos
 * cajas de la misma clinica pueden emitir en paralelo sin repetir numero.
 *
 * <p>{@code taxBranchCode} 0 identifica la casa matriz, segun el catalogo del SIN.
 */
@Entity
@Table(name = "point_of_sale")
public class PointOfSale {

    /** Codigo de sucursal fiscal de la casa matriz. */
    public static final int HEAD_OFFICE_BRANCH_CODE = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID organizationPublicId;

    @Column(name = "branch_public_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID branchPublicId;

    @Column(name = "tax_branch_code", nullable = false, updatable = false, columnDefinition = "SMALLINT UNSIGNED")
    private Integer taxBranchCode;

    @Column(name = "point_of_sale_code", nullable = false, updatable = false, columnDefinition = "SMALLINT UNSIGNED")
    private Integer pointOfSaleCode;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private PointOfSaleType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PointOfSaleStatus status;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected PointOfSale() {
        // Requerido por JPA.
    }

    /**
     * Abre un punto de venta activo.
     *
     * @param organizationPublicId tenant propietario
     * @param branchPublicId       sucursal de ms-tenencia; referencia externa sin FK
     * @param taxBranchCode        codigo de sucursal del SIN; 0 es casa matriz
     * @param pointOfSaleCode      codigo del punto de venta dentro de la sucursal
     * @param name                 nombre visible de la caja
     * @param type                 tipo de punto de venta
     */
    public static PointOfSale open(UUID organizationPublicId, UUID branchPublicId, int taxBranchCode,
            int pointOfSaleCode, String name, PointOfSaleType type) {
        PointOfSale pointOfSale = new PointOfSale();
        pointOfSale.organizationPublicId = Objects.requireNonNull(organizationPublicId, "organizationPublicId");
        pointOfSale.branchPublicId = Objects.requireNonNull(branchPublicId, "branchPublicId");
        pointOfSale.taxBranchCode = taxBranchCode;
        pointOfSale.pointOfSaleCode = pointOfSaleCode;
        pointOfSale.name = Objects.requireNonNull(name, "name");
        pointOfSale.type = type == null ? PointOfSaleType.COLLECTION_DESK : type;
        pointOfSale.status = PointOfSaleStatus.ACTIVE;
        return pointOfSale;
    }

    public boolean allowsIssuing() {
        return status.allowsIssuing();
    }

    public Long getId() {
        return id;
    }

    public UUID getOrganizationPublicId() {
        return organizationPublicId;
    }

    public UUID getBranchPublicId() {
        return branchPublicId;
    }

    public Integer getTaxBranchCode() {
        return taxBranchCode;
    }

    public Integer getPointOfSaleCode() {
        return pointOfSaleCode;
    }

    public String getName() {
        return name;
    }

    public PointOfSaleType getType() {
        return type;
    }

    public PointOfSaleStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
