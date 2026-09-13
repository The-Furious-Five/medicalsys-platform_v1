package com.medicalsys.marketing.domain.model;

import com.medicalsys.marketing.common.Money;
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
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Consumo de un cupon ({@code promotion_redemption}).
 *
 * <p>Es la evidencia de que el descuento se aplico y el mecanismo con el que se controlan
 * {@code max_redemptions} y {@code redemptions_per_patient}.
 *
 * <p>{@code invoicePublicId} hace de clave de idempotencia -- la restriccion
 * {@code uq_promotion_redemption_promotion_id_invoice_public_id} lo garantiza en el motor. Importa
 * porque ms-facturacion puede reintentar el canje si la respuesta se pierde en la red: sin la
 * restriccion, un reintento descontaria dos veces el cupo del cupon.
 */
@Entity
@Table(name = "promotion_redemption")
public class PromotionRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promotion_id", nullable = false, updatable = false)
    private Promotion promotion;

    @Column(name = "patient_public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID patientPublicId;

    @Column(name = "invoice_public_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID invoicePublicId;

    @Column(name = "appointment_public_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID appointmentPublicId;

    @Column(name = "discounted_amount_bob", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountedAmountBob;

    @Column(name = "redeemed_at", insertable = false, updatable = false)
    private Instant redeemedAt;

    protected PromotionRedemption() {
        // Requerido por JPA.
    }

    /** Registra el canje efectivo de un cupon. */
    public static PromotionRedemption record(Promotion promotion, UUID patientPublicId, UUID invoicePublicId,
            UUID appointmentPublicId, BigDecimal discountedAmountBob) {
        PromotionRedemption redemption = new PromotionRedemption();
        redemption.publicId = UUID.randomUUID();
        redemption.promotion = Objects.requireNonNull(promotion, "promotion");
        redemption.patientPublicId = Objects.requireNonNull(patientPublicId, "patientPublicId");
        redemption.invoicePublicId = invoicePublicId;
        redemption.appointmentPublicId = appointmentPublicId;
        redemption.discountedAmountBob = Money.amount(discountedAmountBob);
        return redemption;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public Promotion getPromotion() {
        return promotion;
    }

    public UUID getPatientPublicId() {
        return patientPublicId;
    }

    public UUID getInvoicePublicId() {
        return invoicePublicId;
    }

    public UUID getAppointmentPublicId() {
        return appointmentPublicId;
    }

    public BigDecimal getDiscountedAmountBob() {
        return discountedAmountBob;
    }

    public Instant getRedeemedAt() {
        return redeemedAt;
    }
}
