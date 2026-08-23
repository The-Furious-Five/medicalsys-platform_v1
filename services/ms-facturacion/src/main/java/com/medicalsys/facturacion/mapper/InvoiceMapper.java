package com.medicalsys.facturacion.mapper;

import com.medicalsys.facturacion.common.DateTimes;
import com.medicalsys.facturacion.domain.model.Invoice;
import com.medicalsys.facturacion.domain.model.InvoiceItem;
import com.medicalsys.facturacion.domain.model.PointOfSale;
import com.medicalsys.facturacion.web.dto.InvoiceCustomerResponse;
import com.medicalsys.facturacion.web.dto.InvoiceItemResponse;
import com.medicalsys.facturacion.web.dto.InvoiceResponse;
import com.medicalsys.facturacion.web.dto.InvoiceSummaryResponse;
import com.medicalsys.facturacion.web.dto.PointOfSaleRef;
import java.util.List;
import org.springframework.stereotype.Component;

/** Traduce el agregado de la factura al contrato publico. */
@Component
public class InvoiceMapper {

    /** Factura completa con su detalle. */
    public InvoiceResponse toResponse(Invoice invoice) {
        PointOfSale pointOfSale = invoice.getPointOfSale();
        List<InvoiceItemResponse> items = invoice.getItems().stream().map(this::toResponse).toList();
        return new InvoiceResponse(
                invoice.getPublicId(),
                invoice.getInvoiceNumber(),
                invoice.getTaxDocumentType(),
                invoice.getEmissionType(),
                invoice.getStatus(),
                invoice.isDemo(),
                invoice.getCaption(),
                invoice.getCuf(),
                invoice.getTaxReceiptCode(),
                invoice.getQrUrl(),
                invoice.getBranchPublicId(),
                new PointOfSaleRef(pointOfSale.getTaxBranchCode(), pointOfSale.getPointOfSaleCode()),
                toCustomerResponse(invoice),
                invoice.getTotalAmount(),
                invoice.getVatTaxableAmount(),
                invoice.getTotalDiscountAmount(),
                invoice.getGiftCardAmount(),
                invoice.getCurrencyTotalAmount(),
                invoice.getExchangeRate(),
                invoice.getPaymentMethodCode(),
                DateTimes.toOffset(invoice.getIssuedAt()),
                DateTimes.toOffset(invoice.getValidatedAt()),
                items);
    }

    /** Proyeccion minima para listados y para otros microservicios. */
    public InvoiceSummaryResponse toSummary(Invoice invoice) {
        return new InvoiceSummaryResponse(
                invoice.getPublicId(),
                invoice.getInvoiceNumber(),
                invoice.getPatientPublicId(),
                invoice.getStatus(),
                invoice.isDemo(),
                invoice.getTotalAmount(),
                DateTimes.toOffset(invoice.getIssuedAt()));
    }

    private InvoiceCustomerResponse toCustomerResponse(Invoice invoice) {
        return new InvoiceCustomerResponse(
                invoice.getPatientPublicId(),
                invoice.getIdentityDocumentTypeCode(),
                invoice.getCustomerDocumentNumber(),
                invoice.getCustomerDocumentComplement(),
                invoice.getCustomerLegalName(),
                invoice.getCustomerEmail());
    }

    private InvoiceItemResponse toResponse(InvoiceItem item) {
        return new InvoiceItemResponse(
                item.getItemNumber(),
                item.getServiceOfferingPublicId(),
                item.getDescription(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getDiscountAmount(),
                item.getSubtotal(),
                item.getTaxProductCode(),
                item.getUnitOfMeasure(),
                item.getAppointmentPublicId(),
                item.getClinicalEncounterPublicId());
    }
}
