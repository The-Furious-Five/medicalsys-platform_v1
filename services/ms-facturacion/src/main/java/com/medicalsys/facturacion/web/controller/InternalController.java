package com.medicalsys.facturacion.web.controller;

import com.medicalsys.facturacion.common.tenant.RequestContextHolder;
import com.medicalsys.facturacion.service.InvoiceService;
import com.medicalsys.facturacion.web.dto.InvoiceSummaryResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rutas internas consumidas por otros microservicios dentro de la red medicalsys-interna.
 *
 * <p>Devuelven proyecciones minimas: nadie recibe el detalle ni los datos del cliente por esta via.
 * El aislamiento por organizacion se aplica igual que en las rutas publicas.
 */
@RestController
@RequestMapping("/internal/v1")
public class InternalController {

    private final InvoiceService invoices;

    public InternalController(InvoiceService invoices) {
        this.invoices = invoices;
    }

    /** Estado y total de una factura por su UUID publico. */
    @GetMapping("/facturas/{invoiceId}")
    public InvoiceSummaryResponse getInvoice(@PathVariable UUID invoiceId) {
        return invoices.getSummary(RequestContextHolder.require(), invoiceId);
    }
}
