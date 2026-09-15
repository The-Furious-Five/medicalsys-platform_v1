package com.medicalsys.catalogos.tax;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** Entrada de la réplica local de catálogos tributarios del SIN. */
@Entity
@Table(name = "tax_catalog")
public class TaxCatalog {

    public enum CatalogType {
        IDENTITY_DOCUMENT_TYPE,
        PAYMENT_METHOD_TYPE,
        UNIT_OF_MEASURE,
        ECONOMIC_ACTIVITY,
        PRODUCT_SERVICE,
        INVOICE_CAPTION,
        CANCELLATION_REASON,
        SIGNIFICANT_EVENT_TYPE,
        TAX_DOCUMENT_TYPE,
        EMISSION_TYPE,
        POINT_OF_SALE_TYPE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "catalog_type", nullable = false, length = 50)
    private CatalogType catalogType;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "parent_code", length = 30)
    private String parentCode;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "synced_at")
    private OffsetDateTime syncedAt;

    protected TaxCatalog() {
    }

    public TaxCatalog(CatalogType catalogType, String code, String description, boolean active) {
        this.catalogType = catalogType;
        this.code = code;
        this.description = description;
        this.active = active;
    }

    public Long getId() { return id; }
    public CatalogType getCatalogType() { return catalogType; }
    public String getCode() { return code; }
    public String getDescription() { return description; }
    public String getParentCode() { return parentCode; }
    public boolean isActive() { return active; }
    public OffsetDateTime getSyncedAt() { return syncedAt; }
}
