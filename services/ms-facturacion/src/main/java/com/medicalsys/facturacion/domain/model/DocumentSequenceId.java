package com.medicalsys.facturacion.domain.model;

import com.medicalsys.facturacion.domain.enums.TaxDocumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import java.util.Objects;

/**
 * Clave compuesta del correlativo: punto de venta y tipo de documento fiscal.
 *
 * @param pointOfSaleId  identificador tecnico local del punto de venta
 * @param taxDocumentType tipo de documento cuyo contador se lleva
 */
@Embeddable
public record DocumentSequenceId(
        @Column(name = "point_of_sale_id", nullable = false) Long pointOfSaleId,
        @Enumerated(EnumType.STRING)
        @Column(name = "tax_document_type", nullable = false, length = 30) TaxDocumentType taxDocumentType)
        implements Serializable {

    public DocumentSequenceId {
        Objects.requireNonNull(pointOfSaleId, "pointOfSaleId");
        Objects.requireNonNull(taxDocumentType, "taxDocumentType");
    }
}
