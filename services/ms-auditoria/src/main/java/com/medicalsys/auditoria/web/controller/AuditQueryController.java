package com.medicalsys.auditoria.web.controller;

import com.medicalsys.auditoria.common.security.RequiresPermission;
import com.medicalsys.auditoria.domain.enums.AuditCategory;
import com.medicalsys.auditoria.domain.enums.AuditOutcome;
import com.medicalsys.auditoria.domain.enums.AuditSeverity;
import com.medicalsys.auditoria.service.AuditEventService;
import com.medicalsys.auditoria.service.AuditIntegrityService;
import com.medicalsys.auditoria.service.AuditSummaryService;
import com.medicalsys.auditoria.web.dto.AuditEventFilter;
import com.medicalsys.auditoria.web.dto.AuditEventResponse;
import com.medicalsys.auditoria.web.dto.AuditIntegrityResponse;
import com.medicalsys.auditoria.web.dto.AuditSummaryResponse;
import com.medicalsys.auditoria.web.dto.PageResponse;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de la bitacora para la pantalla de auditoria.
 *
 * <p>Solo lectura, y solo para quien administra la organizacion. Un medico o una recepcionista no
 * necesitan ver quien mas abrio un expediente; darles esa vista convertiria la bitacora en una
 * fuente nueva de exposicion en lugar de un control.
 *
 * <p>El servicio no ofrece ninguna ruta de escritura publica ni de borrado. No es una funcionalidad
 * por completar: es el contrato de una bitacora.
 */
@Validated
@RestController
@RequestMapping("/api/v1/audit")
public class AuditQueryController {

    private final AuditEventService auditEvents;
    private final AuditSummaryService summaries;
    private final AuditIntegrityService integrityService;

    public AuditQueryController(AuditEventService auditEvents, AuditSummaryService summaries,
            AuditIntegrityService integrityService) {
        this.auditEvents = auditEvents;
        this.summaries = summaries;
        this.integrityService = integrityService;
    }

    /**
     * Busqueda paginada de eventos del tenant en curso.
     *
     * <p>El orden por defecto es descendente por {@code occurredAt}, que es el que espera quien
     * revisa un incidente: lo ultimo que paso, primero.
     */
    @GetMapping("/events")
    @RequiresPermission("audit.log.read")
    public PageResponse<AuditEventResponse> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) AuditCategory category,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false) AuditSeverity severity,
            @RequestParam(required = false) @Size(max = 40) String serviceName,
            @RequestParam(required = false) @Size(max = 60) String action,
            @RequestParam(required = false) @Size(max = 60) String resourceType,
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) UUID resourceId,
            @RequestParam(required = false) @Size(max = 64) String correlationId,
            @RequestParam(required = false) @Size(max = 160) String q,
            @PageableDefault(size = 25, sort = "occurredAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        AuditEventFilter filter = new AuditEventFilter(from, to, category, outcome, severity, serviceName,
                action, resourceType, actorUserId, patientId, resourceId, correlationId, q);
        return PageResponse.of(auditEvents.search(filter, pageable).map(AuditEventResponse::from));
    }

    @GetMapping("/events/{eventId}")
    @RequiresPermission("audit.log.read")
    public AuditEventResponse get(@PathVariable UUID eventId) {
        return auditEvents.get(eventId);
    }

    /** Cortes agregados de la ventana; alimenta el encabezado de la pantalla. */
    @GetMapping("/summary")
    @RequiresPermission("audit.log.read")
    public AuditSummaryResponse summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return summaries.summarize(from, to);
    }

    /** Recalculo de la cadena de hashes sobre los ultimos eventos de la organizacion. */
    @GetMapping("/integrity")
    @RequiresPermission("audit.log.verify")
    public AuditIntegrityResponse integrity(@RequestParam(required = false) Integer window) {
        return integrityService.verifyLatest(window);
    }
}
