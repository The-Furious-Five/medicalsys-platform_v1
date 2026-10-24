package com.medicalsys.facturacion.domain.model;

import com.medicalsys.facturacion.common.Money;
import com.medicalsys.facturacion.domain.enums.EmissionType;
import com.medicalsys.facturacion.domain.enums.InvoiceStatus;
import com.medicalsys.facturacion.domain.enums.TaxDocumentType;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Cabecera fiscal y monetaria de una factura ({@code invoice}).
 *
 * <p>Es la raiz del agregado: los items solo se agregan a traves de {@link #addItem} y los totales
 * se recalculan siempre desde ellos ({@link #recalculateTotals}). Por eso el total de la factura no
 * puede dejar de coincidir con la suma de su detalle, que es la verificacion comprometida en el
 * Excel del sprint.
 *
 * <p>Los datos del cliente son un snapshot del DTO fiscal minimo de ms-pacientes: la factura no
 * cambia si el paciente se edita despues. Esta entidad <b>no</b> guarda ningun dato clinico.
 */
@Entity
@Table(name = "invoice")
public class Invoice {

    /** Codigo del catalogo del SIN para la cedula de identidad. */
    public static final int IDENTITY_DOCUMENT_TYPE_CI = 1;

    /** Codigo del catalogo del SIN para el pago en efectivo. */
    public static final int PAYMENT_METHOD_CASH = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @Column(name = "organization_public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID organizationPublicId;

    @Column(name = "branch_public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID branchPublicId;

    @Column(name = "patient_public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID patientPublicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "point_of_sale_id", nullable = false, updatable = false)
    private PointOfSale pointOfSale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cufd_id", nullable = false, updatable = false)
    private Cufd cufd;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_document_type", nullable = false, updatable = false)
    private TaxDocumentType taxDocumentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "emission_type", nullable = false, updatable = false)
    private EmissionType emissionType;

    @Column(name = "invoice_number", nullable = false, updatable = false)
    private Long invoiceNumber;

    @Column(name = "cuf", nullable = false, updatable = false, length = 80)
    private String cuf;

    @Column(name = "cafc", updatable = false, length = 30)
    private String cafc;

    @Column(name = "identity_document_type_code", nullable = false, updatable = false,
            columnDefinition = "TINYINT UNSIGNED")
    private Integer identityDocumentTypeCode;

    @Column(name = "customer_document_number", nullable = false, updatable = false, length = 20)
    private String customerDocumentNumber;

    @Column(name = "customer_document_complement", updatable = false, length = 5)
    private String customerDocumentComplement;

    @Column(name = "customer_legal_name", nullable = false, updatable = false, length = 200)
    private String customerLegalName;

    @Column(name = "customer_email", updatable = false, length = 160)
    private String customerEmail;

    @Column(name = "exchange_rate", nullable = false, updatable = false, precision = 12, scale = 5)
    private BigDecimal exchangeRate;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "vat_taxable_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal vatTaxableAmount;

    @Column(name = "total_discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalDiscountAmount;

    @Column(name = "gift_card_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal giftCardAmount;

    @Column(name = "currency_total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal currencyTotalAmount;

    @Column(name = "payment_method_code", nullable = false, updatable = false, columnDefinition = "TINYINT UNSIGNED")
    private Integer paymentMethodCode;

    @Column(name = "card_number_suffix", updatable = false, length = 4)
    private String cardNumberSuffix;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "tax_receipt_code", length = 50)
    private String taxReceiptCode;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "caption", length = 500)
    private String caption;

    @Column(name = "qr_url", length = 500)
    private String qrUrl;

    @Column(name = "xml_hash", length = 64)
    private String xmlHash;

    @Column(name = "xml_document_public_id", columnDefinition = "BINARY(16)")
    private UUID xmlDocumentPublicId;

    @Column(name = "pdf_document_public_id", columnDefinition = "BINARY(16)")
    private UUID pdfDocumentPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InvoiceStatus status;

    @Column(name = "cancellation_reason_code", columnDefinition = "TINYINT UNSIGNED")
    private Integer cancellationReasonCode;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "voided_by_user_public_id", columnDefinition = "BINARY(16)")
    private UUID voidedByUserPublicId;

    @Column(name = "demo_emission", nullable = false)
    private boolean demoEmission;

    @Column(name = "issued_by_user_public_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID issuedByUserPublicId;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("itemNumber ASC")
    private List<InvoiceItem> items = new ArrayList<>();

    protected Invoice() {
        // Requerido por JPA.
    }

    /** Datos del cliente congelados en la factura, tomados del DTO fiscal de ms-pacientes. */
    public record Customer(
            int identityDocumentTypeCode,
            String documentNumber,
            String documentComplement,
            String legalName,
            String email) {

        public Customer {
            Objects.requireNonNull(documentNumber, "documentNumber");
            Objects.requireNonNull(legalName, "legalName");
        }
    }

    /**
     * Abre una factura en borrador de emision, sin items ni CUF.
     *
     * <p>Queda incompleta a proposito: el flujo agrega los items, recalcula los totales y solo
     * entonces la sella con la respuesta del adaptador fiscal ({@link #seal}). Antes de ese sello no
     * debe persistirse: {@code cuf} es {@code NOT NULL} en la base.
     */
    public static Invoice open(UUID organizationPublicId, UUID branchPublicId, UUID patientPublicId,
            PointOfSale pointOfSale, Cufd cufd, TaxDocumentType taxDocumentType, long invoiceNumber,
            Customer customer, Integer paymentMethodCode, UUID issuedByUserPublicId, Instant issuedAt) {
        Invoice invoice = new Invoice();
        invoice.publicId = UUID.randomUUID();
        invoice.organizationPublicId = Objects.requireNonNull(organizationPublicId, "organizationPublicId");
        invoice.branchPublicId = Objects.requireNonNull(branchPublicId, "branchPublicId");
        invoice.patientPublicId = Objects.requireNonNull(patientPublicId, "patientPublicId");
        invoice.pointOfSale = Objects.requireNonNull(pointOfSale, "pointOfSale");
        invoice.cufd = Objects.requireNonNull(cufd, "cufd");
        invoice.taxDocumentType = Objects.requireNonNull(taxDocumentType, "taxDocumentType");
        invoice.emissionType = EmissionType.ONLINE;
        invoice.invoiceNumber = invoiceNumber;
        Objects.requireNonNull(customer, "customer");
        invoice.identityDocumentTypeCode = customer.identityDocumentTypeCode();
        invoice.customerDocumentNumber = customer.documentNumber();
        invoice.customerDocumentComplement = customer.documentComplement();
        invoice.customerLegalName = customer.legalName();
        invoice.customerEmail = customer.email();
        invoice.exchangeRate = Money.exchangeRate(null);
        invoice.giftCardAmount = Money.ZERO;
        invoice.paymentMethodCode = paymentMethodCode == null ? PAYMENT_METHOD_CASH : paymentMethodCode;
        invoice.issuedByUserPublicId = issuedByUserPublicId;
        invoice.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        invoice.status = InvoiceStatus.ISSUED;
        invoice.recalculateTotals();
        return invoice;
    }

    /** Agrega un item al agregado y actualiza los totales. */
    public InvoiceItem addItem(InvoiceItem item) {
        item.attachTo(this);
        items.add(item);
        recalculateTotals();
        return item;
    }

    /**
     * Recalcula los totales a partir del detalle.
     *
     * <p>Es la unica fuente de los importes de la cabecera: nadie puede fijar un total distinto de
     * la suma de los subtotales.
     */
    public void recalculateTotals() {
        BigDecimal total = Money.ZERO;
        BigDecimal discounts = Money.ZERO;
        for (InvoiceItem item : items) {
            total = Money.add(total, item.getSubtotal());
            discounts = Money.add(discounts, item.getDiscountAmount());
        }
        this.totalAmount = total;
        this.totalDiscountAmount = discounts;
        // El MVP no emite gift cards ni exenciones parciales: todo el importe esta sujeto a IVA.
        this.vatTaxableAmount = Money.subtract(total, giftCardAmount);
        this.currencyTotalAmount = total;
    }

    /**
     * Sella la factura con la respuesta del adaptador fiscal.
     *
     * @param cuf            codigo unico del documento
     * @param status         estado resultante; en el entorno local es {@code DEMO_ACCEPTED}
     * @param taxReceiptCode codigo de recepcion devuelto, opcional
     * @param caption        leyenda impresa; en el MVP es la leyenda no tributaria
     * @param qrUrl          direccion del QR de verificacion, nula mientras no exista emision real
     * @param validatedAt    instante de la respuesta, opcional
     * @throws IllegalStateException si la factura ya fue sellada
     */
    public void seal(String cuf, InvoiceStatus status, String taxReceiptCode, String caption, String qrUrl,
            Instant validatedAt) {
        if (this.cuf != null) {
            throw new IllegalStateException("La factura ya fue sellada con un CUF");
        }
        this.cuf = Objects.requireNonNull(cuf, "cuf");
        this.status = Objects.requireNonNull(status, "status");
        this.demoEmission = status.isDemo();
        this.taxReceiptCode = taxReceiptCode;
        this.caption = caption;
        this.qrUrl = qrUrl;
        this.validatedAt = validatedAt;
    }

    /**
     * Comprueba la invariante monetaria del sprint: el total coincide con la suma del detalle.
     *
     * <p>{@link #recalculateTotals} ya la garantiza; esta comprobacion es la red de seguridad que el
     * servicio ejecuta antes de persistir, para que una factura descuadrada no llegue nunca a la
     * base ni a la coleccion de la demostracion.
     */
    public boolean totalsMatchItems() {
        BigDecimal sum = Money.ZERO;
        for (InvoiceItem item : items) {
            sum = Money.add(sum, item.getSubtotal());
        }
        return Money.equalValue(sum, totalAmount);
    }

    /** Indica si el documento proviene del adaptador simulado y no de una autoridad fiscal real. */
    public boolean isDemo() {
        return demoEmission || status.isDemo();
    }

    /** Conserva el documento y sus importes; registra quien lo anulo y por que. */
    public void voidInvoice(int reasonCode, UUID actorId, Instant at) {
        if (status == InvoiceStatus.VOIDED || status == InvoiceStatus.REJECTED) {
            throw new IllegalStateException("La factura no admite anulacion en su estado actual");
        }
        if (Objects.equals(actorId, issuedByUserPublicId)) {
            throw new IllegalStateException("Quien emitio la factura no puede anularla");
        }
        if (reasonCode < 1 || reasonCode > 4) {
            throw new IllegalArgumentException("Motivo de anulacion invalido");
        }
        this.cancellationReasonCode = reasonCode;
        this.voidedAt = Objects.requireNonNull(at, "at");
        this.voidedByUserPublicId = Objects.requireNonNull(actorId, "actorId");
        this.status = InvoiceStatus.VOIDED;
    }

    public UUID getVoidedByUserPublicId() {
        return voidedByUserPublicId;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public UUID getOrganizationPublicId() {
        return organizationPublicId;
    }

    public UUID getBranchPublicId() {
        return branchPublicId;
    }

    public UUID getPatientPublicId() {
        return patientPublicId;
    }

    public PointOfSale getPointOfSale() {
        return pointOfSale;
    }

    public Cufd getCufd() {
        return cufd;
    }

    public TaxDocumentType getTaxDocumentType() {
        return taxDocumentType;
    }

    public EmissionType getEmissionType() {
        return emissionType;
    }

    public Long getInvoiceNumber() {
        return invoiceNumber;
    }

    public String getCuf() {
        return cuf;
    }

    public String getCafc() {
        return cafc;
    }

    public Integer getIdentityDocumentTypeCode() {
        return identityDocumentTypeCode;
    }

    public String getCustomerDocumentNumber() {
        return customerDocumentNumber;
    }

    public String getCustomerDocumentComplement() {
        return customerDocumentComplement;
    }

    public String getCustomerLegalName() {
        return customerLegalName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public BigDecimal getExchangeRate() {
        return exchangeRate;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getVatTaxableAmount() {
        return vatTaxableAmount;
    }

    public BigDecimal getTotalDiscountAmount() {
        return totalDiscountAmount;
    }

    public BigDecimal getGiftCardAmount() {
        return giftCardAmount;
    }

    public BigDecimal getCurrencyTotalAmount() {
        return currencyTotalAmount;
    }

    public Integer getPaymentMethodCode() {
        return paymentMethodCode;
    }

    public String getCardNumberSuffix() {
        return cardNumberSuffix;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public String getTaxReceiptCode() {
        return taxReceiptCode;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }

    public String getCaption() {
        return caption;
    }

    public String getQrUrl() {
        return qrUrl;
    }

    public String getXmlHash() {
        return xmlHash;
    }

    public UUID getXmlDocumentPublicId() {
        return xmlDocumentPublicId;
    }

    public UUID getPdfDocumentPublicId() {
        return pdfDocumentPublicId;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public Integer getCancellationReasonCode() {
        return cancellationReasonCode;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    public UUID getIssuedByUserPublicId() {
        return issuedByUserPublicId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<InvoiceItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
