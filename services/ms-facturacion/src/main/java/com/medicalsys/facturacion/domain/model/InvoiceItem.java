package com.medicalsys.facturacion.domain.model;

import com.medicalsys.facturacion.common.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Item facturado con descripcion y precio congelados ({@code invoice_item}).
 *
 * <p>El precio unitario es un <b>snapshot</b> del catalogo de prestaciones de ms-tenencia en el
 * momento de emitir: si manana cambia la tarifa, la factura ya emitida no cambia.
 *
 * <p>El subtotal nunca llega desde fuera. Lo calcula {@link #of} a partir de cantidad, precio y
 * descuento, de modo que no puedan existir dos verdades sobre el mismo importe.
 *
 * <p>{@code appointmentPublicId} y {@code clinicalEncounterPublicId} son la unica trazabilidad
 * clinica de esta base: identificadores sueltos, sin FK y sin ningun dato de la atencion.
 */
@Entity
@Table(name = "invoice_item")
public class InvoiceItem {

    /** Unidad de medida del catalogo del SIN: 58 = unidad. */
    public static final int UNIT_OF_MEASURE_UNIT = 58;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    @Column(name = "item_number", nullable = false, updatable = false, columnDefinition = "SMALLINT UNSIGNED")
    private Integer itemNumber;

    @Column(name = "service_offering_public_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID serviceOfferingPublicId;

    @Column(name = "appointment_public_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID appointmentPublicId;

    @Column(name = "clinical_encounter_public_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID clinicalEncounterPublicId;

    @Column(name = "economic_activity_code", nullable = false, updatable = false, length = 20)
    private String economicActivityCode;

    @Column(name = "tax_product_code", nullable = false, updatable = false, length = 30)
    private String taxProductCode;

    @Column(name = "internal_product_code", updatable = false, length = 30)
    private String internalProductCode;

    @Column(name = "description", nullable = false, updatable = false, length = 500)
    private String description;

    @Column(name = "quantity", nullable = false, updatable = false, precision = 12, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_of_measure", nullable = false, updatable = false, columnDefinition = "SMALLINT UNSIGNED")
    private Integer unitOfMeasure;

    @Column(name = "unit_price", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "discount_amount", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "subtotal", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    protected InvoiceItem() {
        // Requerido por JPA.
    }

    /**
     * Crea un item calculando su subtotal.
     *
     * @param itemNumber               posicion dentro de la factura, empezando en 1
     * @param serviceOfferingPublicId  prestacion de ms-tenencia; referencia externa sin FK
     * @param appointmentPublicId      cita que origina el cobro, opcional
     * @param clinicalEncounterPublicId encuentro que origina el cobro, opcional
     * @param economicActivityCode     actividad economica (CAEB) declarada
     * @param taxProductCode           codigo de producto/servicio homologado
     * @param internalProductCode      codigo interno de la prestacion, opcional
     * @param description              descripcion congelada de la prestacion
     * @param quantity                 cantidad facturada; debe ser mayor que cero
     * @param unitOfMeasure            unidad de medida del catalogo del SIN
     * @param unitPrice                precio unitario congelado
     * @param discountAmount           descuento aplicado al item
     * @throws IllegalArgumentException si la cantidad no es positiva o el subtotal resulta negativo
     */
    public static InvoiceItem of(int itemNumber, UUID serviceOfferingPublicId, UUID appointmentPublicId,
            UUID clinicalEncounterPublicId, String economicActivityCode, String taxProductCode,
            String internalProductCode, String description, BigDecimal quantity, Integer unitOfMeasure,
            BigDecimal unitPrice, BigDecimal discountAmount) {
        InvoiceItem item = new InvoiceItem();
        item.itemNumber = itemNumber;
        item.serviceOfferingPublicId = serviceOfferingPublicId;
        item.appointmentPublicId = appointmentPublicId;
        item.clinicalEncounterPublicId = clinicalEncounterPublicId;
        item.economicActivityCode = Objects.requireNonNull(economicActivityCode, "economicActivityCode");
        item.taxProductCode = Objects.requireNonNull(taxProductCode, "taxProductCode");
        item.internalProductCode = internalProductCode;
        item.description = Objects.requireNonNull(description, "description");
        item.quantity = Money.quantity(quantity);
        item.unitOfMeasure = unitOfMeasure == null ? UNIT_OF_MEASURE_UNIT : unitOfMeasure;
        item.unitPrice = Money.amount(unitPrice);
        item.discountAmount = Money.amount(discountAmount);

        if (item.quantity.signum() <= 0) {
            throw new IllegalArgumentException("La cantidad facturada debe ser mayor que cero");
        }
        item.subtotal = Money.subtract(Money.multiply(item.quantity, item.unitPrice), item.discountAmount);
        if (Money.isNegative(item.subtotal)) {
            throw new IllegalArgumentException("El descuento no puede superar el importe del item");
        }
        return item;
    }

    /** Vincula el item a su factura. Lo invoca {@link Invoice#addItem}. */
    void attachTo(Invoice invoice) {
        this.invoice = Objects.requireNonNull(invoice, "invoice");
    }

    public Long getId() {
        return id;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public Integer getItemNumber() {
        return itemNumber;
    }

    public UUID getServiceOfferingPublicId() {
        return serviceOfferingPublicId;
    }

    public UUID getAppointmentPublicId() {
        return appointmentPublicId;
    }

    public UUID getClinicalEncounterPublicId() {
        return clinicalEncounterPublicId;
    }

    public String getEconomicActivityCode() {
        return economicActivityCode;
    }

    public String getTaxProductCode() {
        return taxProductCode;
    }

    public String getInternalProductCode() {
        return internalProductCode;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public Integer getUnitOfMeasure() {
        return unitOfMeasure;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }
}
