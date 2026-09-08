package com.medicalsys.marketing.domain.model;

import com.medicalsys.marketing.common.Money;
import com.medicalsys.marketing.domain.enums.DiscountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Cupon de descuento ({@code promotion}).
 *
 * <p>ms-facturacion nunca aplica un cupon por su cuenta: lo valida contra este servicio y despues
 * pide el canje. Es la unica llamada sincrona que entra a marketing desde el flujo de cobro, y el
 * documento la senala como el punto donde una caida degrada el servicio vecino -- una factura sin
 * cupon se emite igual, y por eso ms-facturacion trata el 503 como "sin descuento" y no como error.
 *
 * <p>El contador {@code currentRedemptions} se toca siempre bajo bloqueo pesimista de la fila (ver
 * {@code PromotionRepository#lockById}). Dos cajas cobrando a la vez con el mismo cupon de ultimo
 * uso son un caso real; sin el bloqueo, ambas leerian el mismo contador, ambas lo aplicarian y el
 * limite {@code max_redemptions} seria decorativo. Es el mismo patron con el que ms-facturacion
 * protege su correlativo.
 */
@Entity
@Table(name = "promotion")
public class Promotion {

    /** Maximo porcentaje admisible: un 100 % deja la prestacion en cero. */
    private static final BigDecimal MAX_PERCENTAGE = BigDecimal.valueOf(100);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @Column(name = "organization_public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID organizationPublicId;

    @Column(name = "coupon_code", nullable = false, updatable = false, length = 30)
    private String couponCode;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType;

    @Column(name = "value", nullable = false, precision = 12, scale = 2)
    private BigDecimal value;

    @Column(name = "service_offering_public_id", columnDefinition = "BINARY(16)")
    private UUID serviceOfferingPublicId;

    @Column(name = "max_redemptions", columnDefinition = "INT UNSIGNED")
    private Long maxRedemptions;

    @Column(name = "redemptions_per_patient", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private short redemptionsPerPatient;

    @Column(name = "current_redemptions", nullable = false, columnDefinition = "INT UNSIGNED")
    private long currentRedemptions;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_by_user_public_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID createdByUserPublicId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Promotion() {
        // Requerido por JPA.
    }

    /** Crea un cupon vigente. El codigo se normaliza a mayusculas para que no dependa del tecleo. */
    public static Promotion create(UUID organizationPublicId, String couponCode, String name, String description,
            DiscountType discountType, BigDecimal value, UUID serviceOfferingPublicId, Long maxRedemptions,
            Integer redemptionsPerPatient, LocalDate validFrom, LocalDate validUntil, UUID createdByUserPublicId) {
        Promotion promotion = new Promotion();
        promotion.publicId = UUID.randomUUID();
        promotion.organizationPublicId = Objects.requireNonNull(organizationPublicId, "organizationPublicId");
        promotion.couponCode = Objects.requireNonNull(couponCode, "couponCode").trim().toUpperCase(Locale.ROOT);
        promotion.name = Objects.requireNonNull(name, "name");
        promotion.description = description;
        promotion.discountType = Objects.requireNonNull(discountType, "discountType");
        promotion.value = Money.amount(value);
        promotion.serviceOfferingPublicId = serviceOfferingPublicId;
        promotion.maxRedemptions = maxRedemptions;
        promotion.redemptionsPerPatient = (short) (redemptionsPerPatient == null ? 1 : redemptionsPerPatient);
        promotion.currentRedemptions = 0L;
        promotion.validFrom = Objects.requireNonNull(validFrom, "validFrom");
        promotion.validUntil = Objects.requireNonNull(validUntil, "validUntil");
        promotion.active = true;
        promotion.createdByUserPublicId = createdByUserPublicId;
        return promotion;
    }

    /** Indica si el cupon esta activo y dentro de su ventana de vigencia. */
    public boolean isValidOn(LocalDate date) {
        return active && !date.isBefore(validFrom) && !date.isAfter(validUntil);
    }

    /** Indica si queda cupo global. Sin {@code maxRedemptions} el cupo es ilimitado. */
    public boolean hasRemainingRedemptions() {
        return maxRedemptions == null || currentRedemptions < maxRedemptions;
    }

    /** Indica si el cupon aplica a la prestacion indicada. Sin prestacion atada, aplica a todas. */
    public boolean appliesTo(UUID candidateServiceOfferingId) {
        return serviceOfferingPublicId == null || serviceOfferingPublicId.equals(candidateServiceOfferingId);
    }

    /**
     * Calcula el descuento sobre una base, sin superarla nunca.
     *
     * <p>Un descuento mayor que la base convertiria el total en negativo y ms-facturacion emitiria
     * una factura imposible; por eso el resultado se acota aqui y no en el consumidor.
     */
    public BigDecimal discountFor(BigDecimal baseAmountBob) {
        BigDecimal base = Money.amount(baseAmountBob);
        BigDecimal discount = switch (discountType) {
            case PERCENTAGE -> Money.percentageOf(base, Money.min(value, MAX_PERCENTAGE));
            case FIXED_AMOUNT -> value;
            case FREE_SERVICE -> base;
        };
        return Money.min(discount, base);
    }

    /** Consume un uso del cupon. */
    public void registerRedemption() {
        currentRedemptions++;
    }

    /** Devuelve un uso al revertirse el canje (por ejemplo, si la factura se anula). */
    public void releaseRedemption() {
        if (currentRedemptions > 0) {
            currentRedemptions--;
        }
    }

    public void deactivate() {
        this.active = false;
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

    public String getCouponCode() {
        return couponCode;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public BigDecimal getValue() {
        return value;
    }

    public UUID getServiceOfferingPublicId() {
        return serviceOfferingPublicId;
    }

    public Long getMaxRedemptions() {
        return maxRedemptions;
    }

    public short getRedemptionsPerPatient() {
        return redemptionsPerPatient;
    }

    public long getCurrentRedemptions() {
        return currentRedemptions;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public boolean isActive() {
        return active;
    }

    public UUID getCreatedByUserPublicId() {
        return createdByUserPublicId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
