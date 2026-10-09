package com.medicalsys.facturacion.service;

import com.medicalsys.facturacion.common.error.BusinessRuleException;
import com.medicalsys.facturacion.common.error.ErrorCode;
import com.medicalsys.facturacion.domain.enums.TaxDocumentType;
import com.medicalsys.facturacion.domain.model.DocumentSequence;
import com.medicalsys.facturacion.domain.model.DocumentSequenceId;
import com.medicalsys.facturacion.repository.DocumentSequenceRepository;
import java.util.EnumSet;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asigna el correlativo fiscal de un punto de venta.
 *
 * <p>El numero no puede repetirse. La garantia tiene dos capas:
 * <ol>
 *   <li>Esta clase bloquea la fila de {@code document_sequence} con {@code SELECT ... FOR UPDATE},
 *       de modo que dos emisiones simultaneas sobre la misma caja se serializan y la segunda lee el
 *       numero ya incrementado por la primera.</li>
 *   <li>La restriccion {@code uq_invoice_point_of_sale_id_tax_document_type_invoice_number} lo
 *       impone en la base aunque alguien intente escribir por fuera del servicio.</li>
 * </ol>
 *
 * <p>El contador vive en una tabla y no en un {@code AUTO_INCREMENT} porque el correlativo fiscal
 * pertenece al punto de venta, no a la tabla de facturas: dos cajas numeran en paralelo desde uno.
 */
@Component
public class InvoiceNumberGenerator {

    /** Primer correlativo emitido por un punto de venta nuevo. */
    public static final long FIRST_NUMBER = 1L;

    private final DocumentSequenceRepository sequences;

    public InvoiceNumberGenerator(DocumentSequenceRepository sequences) {
        this.sequences = sequences;
    }

    /**
     * Inicializa los contadores de un punto de venta recien creado, uno por tipo de documento.
     *
     * <p>Se ejecuta junto con el alta del punto de venta para que la emision solo tenga que bloquear
     * una fila existente y nunca tenga que crearla bajo concurrencia.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void initializeCounters(Long pointOfSaleId) {
        List<DocumentSequence> counters = EnumSet.allOf(TaxDocumentType.class).stream()
                .map(type -> DocumentSequence.startingAt(new DocumentSequenceId(pointOfSaleId, type), 0L))
                .toList();
        sequences.saveAll(counters);
    }

    /**
     * Devuelve el siguiente correlativo del punto de venta para ese tipo de documento.
     *
     * <p>Debe ejecutarse dentro de la transaccion de emision ({@link Propagation#MANDATORY}): fuera
     * de ella el bloqueo se liberaria de inmediato y dejaria de proteger nada.
     *
     * @throws BusinessRuleException si el punto de venta no tiene contador inicializado
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public long next(Long pointOfSaleId, TaxDocumentType taxDocumentType) {
        DocumentSequenceId id = new DocumentSequenceId(pointOfSaleId, taxDocumentType);
        DocumentSequence sequence = sequences.lockById(id)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.BILLING_CONFIGURATION_NOT_READY,
                        "El punto de venta no tiene inicializado el correlativo de " + taxDocumentType));
        return sequence.nextNumber();
    }
}
