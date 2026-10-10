package com.medicalsys.marketing.web.controller;

import com.medicalsys.marketing.common.security.RequiresPermission;
import com.medicalsys.marketing.common.tenant.RequestContextHolder;
import com.medicalsys.marketing.service.LoyaltyService;
import com.medicalsys.marketing.web.dto.CreateLoyaltyProgramRequest;
import com.medicalsys.marketing.web.dto.LoyaltyBalanceResponse;
import com.medicalsys.marketing.web.dto.LoyaltyMovementRequest;
import com.medicalsys.marketing.web.dto.LoyaltyMovementResponse;
import com.medicalsys.marketing.web.dto.LoyaltyProgramResponse;
import com.medicalsys.marketing.web.dto.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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
 * Programa de fidelizacion: programas, saldo y libro mayor de puntos.
 *
 * <p>No hay ruta para acumular puntos. La acumulacion llega por evento desde ms-facturacion, con la
 * clave de idempotencia del evento; permitirla a mano abriria la puerta a otorgar dos veces los
 * puntos de una misma factura.
 */
@RestController
@RequestMapping("/api/v1/fidelidad")
public class LoyaltyController {

    private final LoyaltyService service;

    public LoyaltyController(LoyaltyService service) {
        this.service = service;
    }

    /** Crea un programa de fidelizacion. */
    @PostMapping("/programas")
    @RequiresPermission("marketing.loyalty_program.create")
    public ResponseEntity<LoyaltyProgramResponse> createProgram(
            @Valid @RequestBody CreateLoyaltyProgramRequest request) {
        LoyaltyProgramResponse program = service.createProgram(RequestContextHolder.require(), request);
        return ResponseEntity.created(URI.create("/api/v1/fidelidad/programas/" + program.id())).body(program);
    }

    /** Lista los programas del tenant. */
    @GetMapping("/programas")
    @RequiresPermission("marketing.loyalty_program.read")
    public List<LoyaltyProgramResponse> listPrograms() {
        return service.listPrograms(RequestContextHolder.require());
    }

    /** Desactiva un programa; sus asientos se conservan. */
    @PostMapping("/programas/{programId}/desactivacion")
    @RequiresPermission("marketing.loyalty_program.deactivate")
    public LoyaltyProgramResponse deactivateProgram(@PathVariable UUID programId) {
        return service.deactivateProgram(RequestContextHolder.require(), programId);
    }

    /** Saldo de puntos de un paciente. Sin {@code programId} usa el programa activo del tenant. */
    @GetMapping("/saldo")
    @RequiresPermission("marketing.loyalty_account.read")
    public LoyaltyBalanceResponse balance(@RequestParam UUID patientId,
            @RequestParam(required = false) UUID programId) {
        return service.balance(RequestContextHolder.require(), patientId, programId);
    }

    /** Libro mayor del paciente, del asiento mas reciente al mas antiguo. */
    @GetMapping("/movimientos")
    @RequiresPermission("marketing.loyalty_account.read")
    public PageResponse<LoyaltyMovementResponse> listMovements(@RequestParam UUID patientId,
            @RequestParam(required = false) UUID programId,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(service.listMovements(RequestContextHolder.require(), patientId, programId,
                pageable));
    }

    /** Registra un canje, un ajuste manual o una bonificacion. La acumulacion no pasa por aqui. */
    @PostMapping("/movimientos")
    @RequiresPermission("marketing.loyalty_movement.create")
    public LoyaltyMovementResponse registerMovement(@Valid @RequestBody LoyaltyMovementRequest request,
            @RequestParam(required = false) UUID programId) {
        return service.registerMovement(RequestContextHolder.require(), request, programId);
    }
}
