package com.medicalsys.marketing.service;

import com.medicalsys.marketing.common.Money;
import com.medicalsys.marketing.common.error.BusinessRuleException;
import com.medicalsys.marketing.common.error.ConflictException;
import com.medicalsys.marketing.common.error.ErrorCode;
import com.medicalsys.marketing.common.error.NotFoundException;
import com.medicalsys.marketing.common.tenant.RequestContext;
import com.medicalsys.marketing.domain.enums.DiscountType;
import com.medicalsys.marketing.domain.model.Promotion;
import com.medicalsys.marketing.domain.model.PromotionRedemption;
import com.medicalsys.marketing.mapper.MarketingMapper;
import com.medicalsys.marketing.repository.PromotionRedemptionRepository;
import com.medicalsys.marketing.repository.PromotionRepository;
import com.medicalsys.marketing.web.dto.CouponRedemptionResponse;
import com.medicalsys.marketing.web.dto.CouponValidationResponse;
import com.medicalsys.marketing.web.dto.CreatePromotionRequest;
import com.medicalsys.marketing.web.dto.PromotionResponse;
import com.medicalsys.marketing.web.dto.RedeemCouponRequest;
import com.medicalsys.marketing.web.dto.ValidateCouponRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso del cupon: definirlo, validarlo y canjearlo.
 *
 * <p>Validar y canjear estan separados a proposito. La caja necesita mostrar el descuento antes de
 * cobrar, y mostrar no puede gastar el cupon; el canje llega despues, cuando la factura ya existe y
 * hay un {@code invoiceId} con el que hacer idempotente la operacion.
 *
 * <p>Es la unica llamada sincrona que ms-facturacion hace hacia marketing. El documento la senala
 * como el punto donde una caida de este servicio degrada al vecino, y por eso el contrato esta
 * pensado para degradar bien: una factura sin cupon se emite igual, asi que ms-facturacion trata el
 * 503 como "sin descuento" y no como error de emision.
 */
@Service
public class PromotionService {

    private static final Logger log = LoggerFactory.getLogger(PromotionService.class);

    private final PromotionRepository promotions;
    private final PromotionRedemptionRepository redemptions;
    private final MarketingEntitlementService entitlement;
    private final ReferenceDataService referenceData;
    private final MarketingMapper mapper;
    private final Clock clock;

    public PromotionService(PromotionRepository promotions, PromotionRedemptionRepository redemptions,
            MarketingEntitlementService entitlement, ReferenceDataService referenceData, MarketingMapper mapper,
            Clock clock) {
        this.promotions = promotions;
        this.redemptions = redemptions;
        this.entitlement = entitlement;
        this.referenceData = referenceData;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Crea un cupon vigente. */
    @Transactional
    public PromotionResponse create(RequestContext context, CreatePromotionRequest request) {
        UUID organizationId = context.organizationId();
        entitlement.requireEntitled(organizationId);
        referenceData.requireOrganization(organizationId);

        String couponCode = normalize(request.couponCode());
        if (promotions.existsByOrganizationPublicIdAndCouponCode(organizationId, couponCode)) {
            throw new ConflictException(ErrorCode.PROMOTION_CODE_ALREADY_EXISTS,
                    "Ya existe un cupon con el codigo '" + couponCode + "' en esta organizacion");
        }
        if (request.validUntil().isBefore(request.validFrom())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR,
                    "La fecha de fin de vigencia no puede ser anterior a la de inicio");
        }
        if (request.discountType() == DiscountType.PERCENTAGE
                && request.value().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR,
                    "Un descuento porcentual no puede superar el 100 %");
        }
        if (request.discountType() == DiscountType.FREE_SERVICE && request.serviceOfferingId() == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR,
                    "Un cupon de servicio gratis debe indicar a que prestacion aplica");
        }

        // La prestacion se replica antes de atarla: la FK exige que exista en ref_service_offering.
        if (request.serviceOfferingId() != null) {
            referenceData.findOrHydrateServiceOffering(organizationId, request.serviceOfferingId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.RESOURCE_NOT_FOUND,
                            "La prestacion '" + request.serviceOfferingId() + "' no existe en esta organizacion"));
        }

        Promotion promotion = promotions.save(Promotion.create(organizationId, couponCode, request.name(),
                request.description(), request.discountType(), request.value(), request.serviceOfferingId(),
                request.maxRedemptions(), request.redemptionsPerPatient(), request.validFrom(),
                request.validUntil(), context.userId()));
        return mapper.toResponse(promotion);
    }

    /** Devuelve un cupon del tenant. */
    @Transactional(readOnly = true)
    public PromotionResponse get(RequestContext context, UUID promotionId) {
        entitlement.requireEntitled(context.organizationId());
        return mapper.toResponse(require(context.organizationId(), promotionId));
    }

    /** Lista los cupones del tenant; con {@code active} filtra por vigencia. */
    @Transactional(readOnly = true)
    public Page<PromotionResponse> list(RequestContext context, Boolean active, Pageable pageable) {
        entitlement.requireEntitled(context.organizationId());
        Page<Promotion> page = active == null
                ? promotions.findByOrganizationPublicId(context.organizationId(), pageable)
                : promotions.findByOrganizationPublicIdAndActive(context.organizationId(), active, pageable);
        return page.map(mapper::toResponse);
    }

    /** Desactiva un cupon; los canjes ya registrados se conservan. */
    @Transactional
    public PromotionResponse deactivate(RequestContext context, UUID promotionId) {
        entitlement.requireEntitled(context.organizationId());
        Promotion promotion = require(context.organizationId(), promotionId);
        promotion.deactivate();
        return mapper.toResponse(promotion);
    }

    /**
     * Valida un cupon sin consumirlo.
     *
     * <p>Un cupon que no sirve responde 200 con {@code valid=false} y su motivo, no un 4xx. Para
     * ms-facturacion, "este cupon no aplica" es una respuesta esperada del flujo de cobro, y
     * devolverle un error le obligaria a interpretar codigos para decidir si puede seguir cobrando.
     */
    @Transactional(readOnly = true)
    public CouponValidationResponse validate(RequestContext context, ValidateCouponRequest request) {
        UUID organizationId = context.organizationId();
        entitlement.requireEntitled(organizationId);

        String couponCode = normalize(request.couponCode());
        Optional<Promotion> found = promotions.findByOrganizationPublicIdAndCouponCode(organizationId, couponCode);
        if (found.isEmpty()) {
            return rejected(couponCode, "El cupon no existe en esta organizacion", request.baseAmountBob());
        }

        Promotion promotion = found.get();
        String rejection = rejectionReason(promotion, request.patientId(), request.serviceOfferingId());
        if (rejection != null) {
            return rejected(couponCode, rejection, request.baseAmountBob());
        }

        BigDecimal discount = promotion.discountFor(request.baseAmountBob());
        return new CouponValidationResponse(true, null, promotion.getPublicId(), couponCode,
                promotion.getDiscountType(), discount,
                Money.subtractFloorZero(request.baseAmountBob(), discount));
    }

    /**
     * Canjea el cupon y registra su consumo.
     *
     * <p>Se toma la fila con bloqueo de escritura antes de tocar el contador: dos cajas cobrando a la
     * vez con el mismo cupon de ultimo uso es un caso real, y sin el bloqueo ambas lo aplicarian.
     *
     * <p>Reintentar con la misma factura devuelve el canje existente en vez de consumir otro uso. La
     * red puede perder la respuesta y el reintento del productor es legitimo.
     */
    @Transactional
    public CouponRedemptionResponse redeem(RequestContext context, RedeemCouponRequest request) {
        UUID organizationId = context.organizationId();
        entitlement.requireEntitled(organizationId);

        String couponCode = normalize(request.couponCode());
        Promotion promotion = promotions.lockByCouponCode(organizationId, couponCode)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PROMOTION_NOT_FOUND,
                        "El cupon '" + couponCode + "' no existe en esta organizacion"));

        Optional<PromotionRedemption> existing =
                redemptions.findByPromotionIdAndInvoicePublicId(promotion.getId(), request.invoiceId());
        if (existing.isPresent()) {
            PromotionRedemption redemption = existing.get();
            log.info("Canje idempotente del cupon {} para la factura {}", couponCode, request.invoiceId());
            return mapper.toResponse(redemption,
                    Money.subtractFloorZero(request.baseAmountBob(), redemption.getDiscountedAmountBob()), true);
        }

        String rejection = rejectionReason(promotion, request.patientId(), request.serviceOfferingId());
        if (rejection != null) {
            throw new BusinessRuleException(ErrorCode.PROMOTION_NOT_APPLICABLE, rejection);
        }

        // El paciente tiene que estar replicado: promotion_redemption lo referencia por FK.
        referenceData.requirePatient(organizationId, request.patientId());

        BigDecimal discount = promotion.discountFor(request.baseAmountBob());
        promotion.registerRedemption();
        PromotionRedemption redemption = redemptions.save(PromotionRedemption.record(promotion,
                request.patientId(), request.invoiceId(), request.appointmentId(), discount));

        log.info("Cupon {} canjeado por {} BOB en la factura {}", couponCode, discount, request.invoiceId());
        return mapper.toResponse(redemption, Money.subtractFloorZero(request.baseAmountBob(), discount), false);
    }

    /** Resuelve un cupon del tenant o falla con 404. */
    @Transactional(readOnly = true)
    public Promotion require(UUID organizationId, UUID promotionId) {
        return promotions.findByPublicIdAndOrganizationPublicId(promotionId, organizationId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PROMOTION_NOT_FOUND,
                        "El cupon '" + promotionId + "' no existe en esta organizacion"));
    }

    /**
     * Devuelve el motivo por el que el cupon no aplica, o {@code null} si aplica.
     *
     * <p>Las reglas viven aqui, en un solo metodo, y no repartidas entre validar y canjear: si
     * divergieran, la caja mostraria un descuento que despues el canje rechazaria.
     */
    private String rejectionReason(Promotion promotion, UUID patientId, UUID serviceOfferingId) {
        LocalDate today = LocalDate.ofInstant(Instant.now(clock), ZoneOffset.UTC);
        if (!promotion.isActive()) {
            return "El cupon esta desactivado";
        }
        if (!promotion.isValidOn(today)) {
            return "El cupon esta fuera de su ventana de vigencia (" + promotion.getValidFrom() + " a "
                    + promotion.getValidUntil() + ")";
        }
        if (!promotion.hasRemainingRedemptions()) {
            return "El cupon agoto sus " + promotion.getMaxRedemptions() + " usos";
        }
        if (!promotion.appliesTo(serviceOfferingId)) {
            return "El cupon solo aplica a la prestacion " + promotion.getServiceOfferingPublicId();
        }
        long usedByPatient = redemptions.countByPromotionIdAndPatientPublicId(promotion.getId(), patientId);
        if (usedByPatient >= promotion.getRedemptionsPerPatient()) {
            return "El paciente ya uso este cupon " + usedByPatient + " vez/veces, el maximo permitido";
        }
        return null;
    }

    private CouponValidationResponse rejected(String couponCode, String reason, BigDecimal baseAmountBob) {
        return new CouponValidationResponse(false, reason, null, couponCode, null, Money.ZERO,
                Money.amount(baseAmountBob));
    }

    /** El codigo se compara siempre en mayusculas: se dicta por telefono y se teclea en un mostrador. */
    private String normalize(String couponCode) {
        return couponCode == null ? null : couponCode.trim().toUpperCase(Locale.ROOT);
    }
}
