package com.medicalsys.marketing.web.controller;

import com.medicalsys.marketing.common.security.RequiresRole;
import com.medicalsys.marketing.common.security.Role;
import com.medicalsys.marketing.common.tenant.RequestContextHolder;
import com.medicalsys.marketing.service.PromotionService;
import com.medicalsys.marketing.web.dto.CouponRedemptionResponse;
import com.medicalsys.marketing.web.dto.CouponValidationResponse;
import com.medicalsys.marketing.web.dto.CreatePromotionRequest;
import com.medicalsys.marketing.web.dto.PageResponse;
import com.medicalsys.marketing.web.dto.PromotionResponse;
import com.medicalsys.marketing.web.dto.RedeemCouponRequest;
import com.medicalsys.marketing.web.dto.ValidateCouponRequest;
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
 * Cupones y descuentos.
 *
 * <p>{@code /validar} y {@code /canjes} estan separados a proposito: la caja necesita mostrar el
 * descuento antes de cobrar, y mostrar no puede gastar el cupon. El canje llega despues, cuando ya
 * existe la factura con la que hacerlo idempotente.
 *
 * <p>Validar responde 200 con {@code valid=false} cuando el cupon no aplica. Para ms-facturacion eso
 * es una respuesta esperada del flujo de cobro y no un fallo de la peticion.
 */
@RestController
@RequestMapping("/api/v1/promociones")
public class PromotionController {

    private final PromotionService service;

    public PromotionController(PromotionService service) {
        this.service = service;
    }

    /** Crea un cupon vigente. */
    @PostMapping
    @RequiresRole({Role.MARKETING_MANAGER, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public ResponseEntity<PromotionResponse> create(@Valid @RequestBody CreatePromotionRequest request) {
        PromotionResponse promotion = service.create(RequestContextHolder.require(), request);
        return ResponseEntity.created(URI.create("/api/v1/promociones/" + promotion.id())).body(promotion);
    }

    /** Lista los cupones del tenant. */
    @GetMapping
    @RequiresRole({Role.MARKETING_MANAGER, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN, Role.RECEPTIONIST,
            Role.BILLING_CLERK})
    public PageResponse<PromotionResponse> list(@RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(service.list(RequestContextHolder.require(), active, pageable));
    }

    /** Devuelve un cupon. */
    @GetMapping("/{promotionId}")
    @RequiresRole({Role.MARKETING_MANAGER, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN, Role.RECEPTIONIST,
            Role.BILLING_CLERK})
    public PromotionResponse get(@PathVariable UUID promotionId) {
        return service.get(RequestContextHolder.require(), promotionId);
    }

    /**
     * Valida un cupon sin consumirlo.
     *
     * <p>La consume ms-facturacion antes de aplicar el descuento. Es POST y no GET porque el cuerpo
     * lleva el paciente y el importe, que no deben viajar en la URL ni quedar en los logs de acceso.
     */
    @PostMapping("/validar")
    @RequiresRole({Role.BILLING_CLERK, Role.RECEPTIONIST, Role.MARKETING_MANAGER, Role.ADMINISTRATOR,
            Role.PLATFORM_ADMIN})
    public CouponValidationResponse validate(@Valid @RequestBody ValidateCouponRequest request) {
        return service.validate(RequestContextHolder.require(), request);
    }

    /** Canjea el cupon; reintentar con la misma factura devuelve el canje ya registrado. */
    @PostMapping("/canjes")
    @RequiresRole({Role.BILLING_CLERK, Role.RECEPTIONIST, Role.MARKETING_MANAGER, Role.ADMINISTRATOR,
            Role.PLATFORM_ADMIN})
    public CouponRedemptionResponse redeem(@Valid @RequestBody RedeemCouponRequest request) {
        return service.redeem(RequestContextHolder.require(), request);
    }

    /** Desactiva el cupon; los canjes ya registrados se conservan. */
    @PostMapping("/{promotionId}/desactivacion")
    @RequiresRole({Role.MARKETING_MANAGER, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public PromotionResponse deactivate(@PathVariable UUID promotionId) {
        return service.deactivate(RequestContextHolder.require(), promotionId);
    }
}
