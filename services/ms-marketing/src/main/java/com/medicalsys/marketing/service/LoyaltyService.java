package com.medicalsys.marketing.service;

import com.medicalsys.marketing.common.DateTimes;
import com.medicalsys.marketing.common.error.BusinessRuleException;
import com.medicalsys.marketing.common.error.ConflictException;
import com.medicalsys.marketing.common.error.ErrorCode;
import com.medicalsys.marketing.common.error.NotFoundException;
import com.medicalsys.marketing.common.tenant.RequestContext;
import com.medicalsys.marketing.domain.enums.LoyaltyMovementType;
import com.medicalsys.marketing.domain.model.LoyaltyPointsMovement;
import com.medicalsys.marketing.domain.model.LoyaltyProgram;
import com.medicalsys.marketing.domain.model.RefPatient;
import com.medicalsys.marketing.mapper.MarketingMapper;
import com.medicalsys.marketing.repository.LoyaltyPointsMovementRepository;
import com.medicalsys.marketing.repository.LoyaltyProgramRepository;
import com.medicalsys.marketing.repository.MarketingViewRepository;
import com.medicalsys.marketing.repository.MarketingViewRepository.LoyaltyBalanceRow;
import com.medicalsys.marketing.web.dto.CreateLoyaltyProgramRequest;
import com.medicalsys.marketing.web.dto.LoyaltyBalanceResponse;
import com.medicalsys.marketing.web.dto.LoyaltyMovementRequest;
import com.medicalsys.marketing.web.dto.LoyaltyMovementResponse;
import com.medicalsys.marketing.web.dto.LoyaltyProgramResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso del programa de fidelizacion.
 *
 * <p>El libro mayor es append-only y el saldo se deriva de la suma de asientos. Cuesta un poco mas
 * que guardar un contador, y compensa: cuando un paciente reclama que le faltan puntos, la respuesta
 * es la lista de movimientos con su procedencia, no un numero que alguien pudo haber corregido a
 * mano.
 *
 * <p>La acumulacion por consumo no tiene ruta HTTP: llega por evento desde ms-facturacion, con la
 * clave de idempotencia del evento. Permitir acumular a mano abriria la puerta a otorgar dos veces
 * los puntos de una misma factura.
 */
@Service
public class LoyaltyService {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyService.class);

    private final LoyaltyProgramRepository programs;
    private final LoyaltyPointsMovementRepository movements;
    private final MarketingViewRepository views;
    private final MarketingEntitlementService entitlement;
    private final ReferenceDataService referenceData;
    private final MarketingMapper mapper;

    public LoyaltyService(LoyaltyProgramRepository programs, LoyaltyPointsMovementRepository movements,
            MarketingViewRepository views, MarketingEntitlementService entitlement,
            ReferenceDataService referenceData, MarketingMapper mapper) {
        this.programs = programs;
        this.movements = movements;
        this.views = views;
        this.entitlement = entitlement;
        this.referenceData = referenceData;
        this.mapper = mapper;
    }

    /** Crea un programa de fidelizacion. */
    @Transactional
    public LoyaltyProgramResponse createProgram(RequestContext context, CreateLoyaltyProgramRequest request) {
        UUID organizationId = context.organizationId();
        entitlement.requireEntitled(organizationId);
        referenceData.requireOrganization(organizationId);

        if (programs.existsByOrganizationPublicIdAndName(organizationId, request.name())) {
            throw new ConflictException(ErrorCode.LOYALTY_PROGRAM_ALREADY_EXISTS,
                    "Ya existe un programa llamado '" + request.name() + "' en esta organizacion");
        }
        LoyaltyProgram program = programs.save(LoyaltyProgram.create(organizationId, request.name(),
                request.pointsPerBob(), request.bobPerPoint(), request.pointsValidityMonths()));
        return mapper.toResponse(program);
    }

    /** Lista los programas del tenant. */
    @Transactional(readOnly = true)
    public List<LoyaltyProgramResponse> listPrograms(RequestContext context) {
        entitlement.requireEntitled(context.organizationId());
        return programs.findByOrganizationPublicIdOrderByCreatedAtAsc(context.organizationId()).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /** Desactiva un programa; sus asientos se conservan. */
    @Transactional
    public LoyaltyProgramResponse deactivateProgram(RequestContext context, UUID programId) {
        entitlement.requireEntitled(context.organizationId());
        LoyaltyProgram program = requireProgram(context.organizationId(), programId);
        program.deactivate();
        return mapper.toResponse(program);
    }

    /**
     * Saldo de puntos de un paciente.
     *
     * <p>Sin {@code programId} usa el programa activo del tenant, que es lo que quiere el portal del
     * paciente: preguntar "cuantos puntos tengo" sin saber cuantos programas existen.
     */
    @Transactional(readOnly = true)
    public LoyaltyBalanceResponse balance(RequestContext context, UUID patientId, UUID programId) {
        UUID organizationId = context.organizationId();
        entitlement.requireEntitled(organizationId);

        LoyaltyProgram program = programId == null
                ? requireActiveProgram(organizationId)
                : requireProgram(organizationId, programId);
        RefPatient patient = referenceData.requirePatient(organizationId, patientId);

        Optional<LoyaltyBalanceRow> row = views.loyaltyBalance(program.getId(), patientId);
        int balancePoints = row.map(LoyaltyBalanceRow::balancePoints).orElse(0);

        return new LoyaltyBalanceResponse(patientId, patient.getFullName(), program.getPublicId(),
                program.getName(), balancePoints, program.valueOf(balancePoints),
                row.map(LoyaltyBalanceRow::lastMovementAt).map(DateTimes::toOffset).orElse(null));
    }

    /** Lista los asientos de un paciente, del mas reciente al mas antiguo. */
    @Transactional(readOnly = true)
    public Page<LoyaltyMovementResponse> listMovements(RequestContext context, UUID patientId, UUID programId,
            Pageable pageable) {
        UUID organizationId = context.organizationId();
        entitlement.requireEntitled(organizationId);
        LoyaltyProgram program = programId == null
                ? requireActiveProgram(organizationId)
                : requireProgram(organizationId, programId);

        return movements
                .findByProgramIdAndPatientPublicIdOrderByOccurredAtDescIdDesc(program.getId(), patientId, pageable)
                .map(mapper::toResponse);
    }

    /**
     * Registra un canje, un ajuste manual o una bonificacion.
     *
     * <p>{@code points} llega siempre positivo y es el tipo el que decide el signo: pedir un numero
     * negativo para canjear invita a equivocarse justo en la operacion que resta saldo.
     */
    @Transactional
    public LoyaltyMovementResponse registerMovement(RequestContext context, LoyaltyMovementRequest request,
            UUID programId) {
        UUID organizationId = context.organizationId();
        entitlement.requireEntitled(organizationId);

        if (request.type() == LoyaltyMovementType.ACCRUAL) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR,
                    "La acumulacion no se registra a mano: llega por evento desde ms-facturacion con su clave "
                            + "de idempotencia, para que una misma factura no otorgue puntos dos veces");
        }
        if (request.type() == LoyaltyMovementType.MANUAL_ADJUSTMENT
                && (request.reason() == null || request.reason().isBlank())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR,
                    "Un ajuste manual exige un motivo: es lo unico que lo distingue de un descuadre");
        }

        LoyaltyProgram program = programId == null
                ? requireActiveProgram(organizationId)
                : requireProgram(organizationId, programId);
        if (!program.isActive()) {
            throw new BusinessRuleException(ErrorCode.LOYALTY_PROGRAM_NOT_ACTIVE,
                    "El programa '" + program.getName() + "' esta desactivado");
        }
        referenceData.requirePatient(organizationId, request.patientId());

        int signedPoints = request.type().isDebit() ? -request.points() : request.points();
        int previousBalance = movements.balanceOf(program.getId(), request.patientId());
        if (previousBalance + signedPoints < 0) {
            throw new BusinessRuleException(ErrorCode.LOYALTY_BALANCE_INSUFFICIENT,
                    "El paciente tiene " + previousBalance + " puntos y la operacion exige "
                            + Math.abs(signedPoints));
        }

        LoyaltyPointsMovement movement = movements.save(LoyaltyPointsMovement.post(program, request.patientId(),
                request.type(), signedPoints, previousBalance, null, null, null, request.reason(),
                context.userId()));
        log.info("Asiento {} de {} puntos para el paciente {}", request.type(), signedPoints,
                request.patientId());
        return mapper.toResponse(movement);
    }

    /**
     * Acumula puntos por una factura emitida. La usa el ingestor de eventos, no ninguna ruta HTTP.
     *
     * <p>{@code sourceEventId} lleva restriccion UNIQUE en la base: si el evento llega dos veces, el
     * segundo intento choca contra el motor en vez de duplicar los puntos.
     *
     * @return el asiento creado, o vacio si no habia programa activo o el importe no daba ni un punto
     */
    @Transactional
    public Optional<LoyaltyPointsMovement> accrueForInvoice(UUID organizationId, UUID patientId,
            BigDecimal billedAmountBob, UUID invoiceId, UUID sourceEventId) {
        Optional<LoyaltyProgram> active =
                programs.findFirstByOrganizationPublicIdAndActiveOrderByCreatedAtAsc(organizationId, true);
        if (active.isEmpty()) {
            return Optional.empty();
        }
        if (sourceEventId != null && movements.existsBySourceEventPublicId(sourceEventId)) {
            log.debug("El evento {} ya habia acumulado puntos; no se repite", sourceEventId);
            return Optional.empty();
        }

        LoyaltyProgram program = active.get();
        int points = program.pointsFor(billedAmountBob);
        if (points <= 0) {
            return Optional.empty();
        }

        int previousBalance = movements.balanceOf(program.getId(), patientId);
        return Optional.of(movements.save(LoyaltyPointsMovement.post(program, patientId,
                LoyaltyMovementType.ACCRUAL, points, previousBalance, invoiceId, null, sourceEventId,
                "Acumulacion por la factura " + invoiceId, null)));
    }

    /** Resuelve un programa del tenant o falla con 404. */
    @Transactional(readOnly = true)
    public LoyaltyProgram requireProgram(UUID organizationId, UUID programId) {
        return programs.findByPublicIdAndOrganizationPublicId(programId, organizationId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.LOYALTY_PROGRAM_NOT_FOUND,
                        "El programa de fidelidad '" + programId + "' no existe en esta organizacion"));
    }

    /** Resuelve el programa activo del tenant o falla con 404. */
    private LoyaltyProgram requireActiveProgram(UUID organizationId) {
        return programs.findFirstByOrganizationPublicIdAndActiveOrderByCreatedAtAsc(organizationId, true)
                .orElseThrow(() -> new NotFoundException(ErrorCode.LOYALTY_PROGRAM_NOT_FOUND,
                        "La organizacion no tiene ningun programa de fidelidad activo"));
    }
}
