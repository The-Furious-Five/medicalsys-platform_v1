package com.medicalsys.facturacion.web.controller;

import com.medicalsys.facturacion.common.security.RequiresPermission;
import com.medicalsys.facturacion.common.tenant.RequestContextHolder;
import com.medicalsys.facturacion.service.InvoiceService;
import com.medicalsys.facturacion.web.dto.CreateInvoiceRequest;
import com.medicalsys.facturacion.web.dto.InvoiceResponse;
import com.medicalsys.facturacion.web.dto.InvoiceSummaryResponse;
import com.medicalsys.facturacion.web.dto.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Facturas de la organizacion.
 *
 * <p>Emitir no es idempotente: cada llamada consume un correlativo y produce una factura nueva. Por
 * eso el gateway y los clientes no deben reintentar automaticamente un POST fallido sin una clave de
 * idempotencia, que queda en el backlog posterior.
 */
@RestController
@RequestMapping("/api/v1/facturas")
public class InvoiceController {

    private final InvoiceService service;

    public InvoiceController(InvoiceService service) {
        this.service = service;
    }

    /** Emite una factura a partir del paciente y las prestaciones indicadas. */
    @PostMapping
    @RequiresPermission("billing.invoice.issue")
    public ResponseEntity<InvoiceResponse> issue(@Valid @RequestBody CreateInvoiceRequest request) {
        InvoiceResponse invoice = service.issue(RequestContextHolder.require(), request);
        return ResponseEntity.created(URI.create("/api/v1/facturas/" + invoice.id())).body(invoice);
    }

    /** Devuelve la factura con su detalle, su correlativo y su etiqueta de demostracion. */
    @GetMapping("/{invoiceId}")
    @RequiresPermission("billing.invoice.read")
    public InvoiceResponse get(@PathVariable UUID invoiceId) {
        return service.get(RequestContextHolder.require(), invoiceId);
    }

    /**
     * Lista las facturas del tenant; con {@code patientId} devuelve solo las de ese paciente.
     *
     * <p>El orden por defecto lo fija el repositorio (emision descendente); el parametro {@code sort}
     * solo hace falta para pedir otro.
     */
    @GetMapping
    @RequiresPermission("billing.invoice.read")
    public PageResponse<InvoiceSummaryResponse> list(@RequestParam(required = false) UUID patientId,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(service.list(RequestContextHolder.require(), patientId, pageable));
    }
}
