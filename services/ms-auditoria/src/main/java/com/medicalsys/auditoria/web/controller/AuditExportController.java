package com.medicalsys.auditoria.web.controller;

import com.medicalsys.auditoria.common.security.RequiresPermission;
import com.medicalsys.auditoria.common.tenant.RequestContextHolder;
import com.medicalsys.auditoria.domain.enums.AuditCategory;
import com.medicalsys.auditoria.domain.enums.AuditOutcome;
import com.medicalsys.auditoria.domain.enums.AuditSeverity;
import com.medicalsys.auditoria.domain.model.AuditEvent;
import com.medicalsys.auditoria.service.AuditCsvExporter;
import com.medicalsys.auditoria.service.AuditEventService;
import com.medicalsys.auditoria.web.dto.AuditEventFilter;
import com.medicalsys.auditoria.web.dto.RecordAuditEventRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exportacion CSV del tramo consultado.
 *
 * <p>Sacar la bitacora del sistema es en si mismo un hecho auditable: es la forma mas directa de
 * llevarse el rastro de quien atendio a quien. Por eso la exportacion asienta su propio evento
 * {@code AUDIT_LOG_EXPORTED} con categoria {@code SECURITY} antes de escribir una sola fila.
 * Registrarlo antes y no despues es deliberado: si la descarga se corta a la mitad, el intento
 * igual quedo asentado.
 */
@Validated
@RestController
@RequestMapping("/api/v1/audit")
public class AuditExportController {

    /**
     * Techo de filas por descarga.
     *
     * <p>Una exportacion legitima cabe de sobra; una que no cabe deja de ser una revision puntual y
     * merece pasar por un proceso explicito en lugar de por la pantalla.
     */
    private static final int MAX_ROWS = 10_000;

    private static final int PAGE_SIZE = 500;

    private final AuditEventService auditEvents;
    private final AuditCsvExporter csv;

    public AuditExportController(AuditEventService auditEvents, AuditCsvExporter csv) {
        this.auditEvents = auditEvents;
        this.csv = csv;
    }

    @GetMapping(value = "/events/export", produces = "text/csv")
    @RequiresPermission("audit.log.export")
    public void export(
            @RequestParam(required = false) UUID organizationId,
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
            HttpServletResponse response) throws IOException {
        AuditEventFilter filter = new AuditEventFilter(organizationId, from, to, category, outcome, severity, serviceName,
                action, resourceType, actorUserId, patientId, resourceId, correlationId, q);

        recordExportAttempt(filter);

        response.setContentType(MediaType.valueOf("text/csv").toString());
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"medicalsys-auditoria.csv\"");

        PrintWriter writer = response.getWriter();
        csv.writeHeader(writer);

        int written = 0;
        int pageNumber = 0;
        Page<AuditEvent> page;
        do {
            page = auditEvents.search(filter,
                    PageRequest.of(pageNumber, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "occurredAt")));
            for (AuditEvent event : page.getContent()) {
                csv.writeRow(writer, event);
                if (++written >= MAX_ROWS) {
                    writer.flush();
                    return;
                }
            }
            writer.flush();
            pageNumber++;
        } while (page.hasNext());
    }

    /**
     * Asienta la exportacion como evento de seguridad.
     *
     * <p>Los metadatos guardan el filtro pedido, no los datos exportados: lo relevante para una
     * revision posterior es que alcance se llevo esa persona, no repetir el contenido.
     */
    private void recordExportAttempt(AuditEventFilter filter) {
        Map<String, String> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "organizationId", filter.organizationId());
        putIfPresent(metadata, "from", filter.from());
        putIfPresent(metadata, "to", filter.to());
        putIfPresent(metadata, "category", filter.category());
        putIfPresent(metadata, "outcome", filter.outcome());
        putIfPresent(metadata, "severity", filter.severity());
        putIfPresent(metadata, "serviceName", filter.serviceName());
        putIfPresent(metadata, "action", filter.action());
        putIfPresent(metadata, "resourceType", filter.resourceType());
        putIfPresent(metadata, "actorUserId", filter.actorUserId());
        putIfPresent(metadata, "patientId", filter.patientPublicId());
        putIfPresent(metadata, "q", filter.q());

        var context = RequestContextHolder.require();
        List<String> roles = context.roles().stream().map(Enum::name).toList();

        auditEvents.record(new RecordAuditEventRequest(
                UUID.randomUUID(), context.organizationId(), null, "ms-auditoria", "AUDIT_LOG_EXPORTED",
                AuditCategory.SECURITY, AuditSeverity.NOTICE, AuditOutcome.SUCCESS, context.userId(),
                null, roles, null, "AUDIT_LOG", null, "Exportacion CSV de la bitacora", null,
                "GET", "/api/v1/audit/events/export", 200, null, metadata));
    }

    private void putIfPresent(Map<String, String> metadata, String key, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            metadata.put(key, String.valueOf(value));
        }
    }
}
