package com.medicalsys.facturacion.service;

import com.medicalsys.facturacion.client.BillableService;
import com.medicalsys.facturacion.client.FiscalCustomer;
import com.medicalsys.facturacion.client.PatientsClient;
import com.medicalsys.facturacion.client.ServiceCatalogClient;
import com.medicalsys.facturacion.client.TaxAuthorityGateway;
import com.medicalsys.facturacion.client.TaxAuthorityGateway.InvoiceReceipt;
import com.medicalsys.facturacion.client.TaxAuthorityGateway.InvoiceSubmission;
import com.medicalsys.facturacion.common.error.BusinessRuleException;
import com.medicalsys.facturacion.common.error.ConflictException;
import com.medicalsys.facturacion.common.error.ErrorCode;
import com.medicalsys.facturacion.common.error.NotFoundException;
import com.medicalsys.facturacion.common.tenant.RequestContext;
import com.medicalsys.facturacion.config.BillingProperties;
import com.medicalsys.facturacion.domain.enums.TaxDocumentType;
import com.medicalsys.facturacion.domain.model.Invoice;
import com.medicalsys.facturacion.domain.model.InvoiceItem;
import com.medicalsys.facturacion.mapper.InvoiceMapper;
import com.medicalsys.facturacion.repository.InvoiceRepository;
import com.medicalsys.facturacion.service.FiscalSetupService.FiscalContext;
import com.medicalsys.facturacion.service.FiscalSetupService.PointOfSaleSelector;
import com.medicalsys.facturacion.web.dto.CreateInvoiceRequest;
import com.medicalsys.facturacion.web.dto.InvoiceLineRequest;
import com.medicalsys.facturacion.web.dto.InvoiceResponse;
import com.medicalsys.facturacion.web.dto.InvoiceSummaryResponse;
import com.medicalsys.facturacion.web.dto.PointOfSaleRef;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de la factura.
 *
 * <p>Emitir una factura es una sola transaccion que hace, en este orden:
 * <ol>
 *   <li>Resolver el cliente en ms-pacientes y las prestaciones en ms-tenencia. Si algo no existe, la
 *       emision se detiene antes de tocar la base y sin consumir correlativo.</li>
 *   <li>Resolver el contexto fiscal: configuracion, punto de venta y CUFD vigente.</li>
 *   <li>Tomar el siguiente correlativo bloqueando la fila del contador.</li>
 *   <li>Construir el agregado, que calcula sus totales desde el detalle.</li>
 *   <li>Enviar el documento al adaptador fiscal y sellarlo con el CUF y el estado devueltos.</li>
 * </ol>
 *
 * <p>El total nunca llega desde fuera y siempre coincide con la suma de los subtotales; el
 * correlativo no se repite. Son las dos verificaciones comprometidas en el Excel del sprint.
 */
@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    /** Tipo de documento que emite el MVP. */
    private static final TaxDocumentType TAX_DOCUMENT_TYPE = TaxDocumentType.PURCHASE_SALE_INVOICE;

    private final InvoiceRepository invoices;
    private final FiscalSetupService fiscalSetup;
    private final InvoiceNumberGenerator invoiceNumbers;
    private final PatientsClient patients;
    private final ServiceCatalogClient serviceCatalog;
    private final TaxAuthorityGateway taxAuthority;
    private final BillingProperties properties;
    private final InvoiceMapper mapper;
    private final Clock clock;

    public InvoiceService(InvoiceRepository invoices, FiscalSetupService fiscalSetup,
            InvoiceNumberGenerator invoiceNumbers, PatientsClient patients, ServiceCatalogClient serviceCatalog,
            TaxAuthorityGateway taxAuthority, BillingProperties properties, InvoiceMapper mapper, Clock clock) {
        this.invoices = invoices;
        this.fiscalSetup = fiscalSetup;
        this.invoiceNumbers = invoiceNumbers;
        this.patients = patients;
        this.serviceCatalog = serviceCatalog;
        this.taxAuthority = taxAuthority;
        this.properties = properties;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Emite una factura a partir de un paciente y sus prestaciones.
     *
     * @throws BusinessRuleException si el paciente, una prestacion o la configuracion fiscal no permiten emitir
     * @throws ConflictException     si el correlativo colisiona pese al bloqueo
     */
    @Transactional
    public InvoiceResponse issue(RequestContext context, CreateInvoiceRequest request) {
        // TIMESTAMP de MySQL guarda segundos: truncar aqui evita que el instante que firma el CUF
        // difiera del que queda persistido.
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);

        FiscalCustomer customer = patients.fiscalCustomer(context.organizationId(), request.patientId());
        List<BillableService> services = resolveServices(context.organizationId(), request.items());
        FiscalContext fiscal = fiscalSetup.resolve(context, selectorOf(request.pointOfSale()), issuedAt);

        long invoiceNumber = invoiceNumbers.next(fiscal.pointOfSale().getId(), TAX_DOCUMENT_TYPE);
        UUID branchId = request.branchId() != null
                ? request.branchId()
                : fiscal.pointOfSale().getBranchPublicId();

        Invoice invoice = Invoice.open(context.organizationId(), branchId, request.patientId(),
                fiscal.pointOfSale(), fiscal.cufd(), TAX_DOCUMENT_TYPE, invoiceNumber,
                new Invoice.Customer(customer.identityDocumentTypeCode(), customer.documentNumber(),
                        customer.normalizedComplement(), customer.legalName(), customer.email()),
                request.paymentMethodCode(), context.userId(), issuedAt);

        addItems(invoice, request.items(), services);
        verifyTotals(invoice);

        InvoiceReceipt receipt = taxAuthority.submit(new InvoiceSubmission(fiscal.identity(), TAX_DOCUMENT_TYPE,
                invoiceNumber, fiscal.cufd().getCode(), customer.documentNumber(), invoice.getTotalAmount(),
                issuedAt));
        invoice.seal(receipt.cuf(), receipt.status(), receipt.receiptCode(), receipt.caption(), receipt.qrUrl(),
                receipt.validatedAt() == null ? null : receipt.validatedAt().truncatedTo(ChronoUnit.SECONDS));

        Invoice saved = persist(invoice);
        log.info("Factura emitida invoiceId={} numero={} punto_venta={} items={} total={} estado={}",
                saved.getPublicId(), saved.getInvoiceNumber(), fiscal.pointOfSale().getId(),
                saved.getItems().size(), saved.getTotalAmount(), saved.getStatus());
        return mapper.toResponse(saved);
    }

    /** Devuelve la factura completa dentro del tenant. */
    @Transactional(readOnly = true)
    public InvoiceResponse get(RequestContext context, UUID invoiceId) {
        return mapper.toResponse(requireByPublicId(context, invoiceId));
    }

    @Transactional
    public InvoiceResponse voidInvoice(RequestContext context, UUID invoiceId, int reasonCode) {
        Invoice invoice = invoices.findForCancellation(context.organizationId(), invoiceId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INVOICE_NOT_FOUND,
                        "La factura solicitada no existe o no pertenece a esta organizacion"));
        // El proyecto opera con un adaptador fiscal simulado: una factura real requiere
        // confirmacion del sistema fiscal, no basta cambiar su estado local.
        if (!taxAuthority.isSimulated() || !invoice.isDemo()) {
            throw new ConflictException(ErrorCode.INVOICE_CANCELLATION_NOT_ALLOWED,
                    "La anulacion requiere una factura del adaptador fiscal simulado");
        }
        try {
            invoice.voidInvoice(reasonCode, context.userId(), clock.instant().truncatedTo(ChronoUnit.SECONDS));
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new ConflictException(ErrorCode.INVOICE_CANCELLATION_NOT_ALLOWED, exception.getMessage());
        }
        return mapper.toResponse(invoices.saveAndFlush(invoice));
    }

    /** Proyeccion minima para otros microservicios. */
    @Transactional(readOnly = true)
    public InvoiceSummaryResponse getSummary(RequestContext context, UUID invoiceId) {
        return mapper.toSummary(requireByPublicId(context, invoiceId));
    }

    /** Lista las facturas del tenant, opcionalmente filtradas por paciente. */
    @Transactional(readOnly = true)
    public Page<InvoiceSummaryResponse> list(RequestContext context, UUID patientId, Pageable pageable) {
        Page<Invoice> page = patientId == null
                ? invoices.findByOrganizationPublicIdOrderByIssuedAtDesc(context.organizationId(), pageable)
                : invoices.findByOrganizationPublicIdAndPatientPublicIdOrderByIssuedAtDesc(
                        context.organizationId(), patientId, pageable);
        return page.map(mapper::toSummary);
    }

    /** Resuelve todas las prestaciones antes de tocar la base: un codigo invalido no consume correlativo. */
    private List<BillableService> resolveServices(UUID organizationId, List<InvoiceLineRequest> lines) {
        return lines.stream()
                .map(line -> serviceCatalog.billableService(organizationId, line.serviceOfferingId()))
                .toList();
    }

    private void addItems(Invoice invoice, List<InvoiceLineRequest> lines, List<BillableService> services) {
        String economicActivityCode = properties.billing().economicActivityCode();
        String fallbackTaxProductCode = properties.billing().fallbackTaxProductCode();
        for (int index = 0; index < lines.size(); index++) {
            InvoiceLineRequest line = lines.get(index);
            BillableService service = services.get(index);
            String taxProductCode = service.taxProductCode() == null || service.taxProductCode().isBlank()
                    ? fallbackTaxProductCode
                    : service.taxProductCode();
            try {
                invoice.addItem(InvoiceItem.of(index + 1, service.id(), line.appointmentId(),
                        line.clinicalEncounterId(), economicActivityCode, taxProductCode, service.code(),
                        service.name(), line.quantity(), service.taxUnitCode(), service.basePriceBob(),
                        line.discountAmount()));
            } catch (IllegalArgumentException e) {
                // El agregado rechaza cantidades o descuentos imposibles; para el cliente es un 422.
                throw new BusinessRuleException(ErrorCode.INVOICE_TOTALS_NOT_CONSISTENT,
                        "La linea " + (index + 1) + " no es facturable: " + e.getMessage());
            }
        }
    }

    /** Red de seguridad de la invariante monetaria antes de persistir. */
    private void verifyTotals(Invoice invoice) {
        if (!invoice.totalsMatchItems()) {
            throw new BusinessRuleException(ErrorCode.INVOICE_TOTALS_NOT_CONSISTENT,
                    "El total de la factura no coincide con la suma de su detalle");
        }
    }

    /**
     * Persiste la factura traduciendo una colision de correlativo a 409.
     *
     * <p>El bloqueo del contador ya lo evita; esta rama cubre el caso de una escritura hecha por
     * fuera del servicio, donde la restriccion UNIQUE de la base es la ultima palabra.
     */
    private Invoice persist(Invoice invoice) {
        try {
            return invoices.saveAndFlush(invoice);
        } catch (DataIntegrityViolationException e) {
            log.warn("Colision al persistir la factura numero={}", invoice.getInvoiceNumber(), e);
            throw new ConflictException(ErrorCode.INVOICE_NUMBERING_CONFLICT,
                    "El correlativo de la factura ya fue utilizado; reintente la operacion");
        }
    }

    private Invoice requireByPublicId(RequestContext context, UUID invoiceId) {
        return invoices.findByOrganizationPublicIdAndPublicId(context.organizationId(), invoiceId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INVOICE_NOT_FOUND,
                        "La factura solicitada no existe o no pertenece a esta organizacion"));
    }

    private PointOfSaleSelector selectorOf(PointOfSaleRef ref) {
        return ref == null ? null : new PointOfSaleSelector(ref.taxBranchCode(), ref.pointOfSaleCode());
    }
}
