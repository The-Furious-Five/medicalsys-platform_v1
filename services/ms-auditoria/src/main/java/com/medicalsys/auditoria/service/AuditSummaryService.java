package com.medicalsys.auditoria.service;

import com.medicalsys.auditoria.common.error.BusinessRuleException;
import com.medicalsys.auditoria.common.error.ErrorCode;
import com.medicalsys.auditoria.common.tenant.RequestContextHolder;
import com.medicalsys.auditoria.domain.enums.AuditOutcome;
import com.medicalsys.auditoria.domain.enums.AuditSeverity;
import com.medicalsys.auditoria.repository.AuditEventRepository;
import com.medicalsys.auditoria.web.dto.AuditSummaryResponse;
import com.medicalsys.auditoria.web.dto.AuditSummaryResponse.ActorActivity;
import com.medicalsys.auditoria.web.dto.AuditSummaryResponse.CountByKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cortes agregados de una ventana de tiempo.
 *
 * <p>Todo el conteo ocurre en el motor. Traer los eventos al servicio para contarlos parece
 * inofensivo con los datos de demostracion y deja de serlo el primer mes real de una clinica.
 */
@Service
public class AuditSummaryService {

    /** Ventana por defecto cuando el auditor abre la pantalla sin elegir fechas. */
    private static final Duration DEFAULT_WINDOW = Duration.ofDays(7);

    /** Techo de la ventana: mas alla, el resumen deja de ser una consulta interactiva. */
    private static final Duration MAX_WINDOW = Duration.ofDays(366);

    private static final int TOP_ACTORS = 5;

    private final AuditEventRepository events;
    private final Clock clock;

    public AuditSummaryService(AuditEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AuditSummaryResponse summarize(Instant from, Instant to) {
        UUID organizationId = RequestContextHolder.require().organizationId();
        Instant end = to == null ? clock.instant() : to;
        Instant start = from == null ? end.minus(DEFAULT_WINDOW) : from;
        validate(start, end);

        List<CountByKey> byCategory = toCounts(events.countByCategory(organizationId, start, end));
        List<CountByKey> byOutcome = toCounts(events.countByOutcome(organizationId, start, end));
        List<CountByKey> bySeverity = toCounts(events.countBySeverity(organizationId, start, end));
        List<CountByKey> byService = toCounts(events.countByService(organizationId, start, end));

        List<ActorActivity> topActors = events
                .countByActor(organizationId, start, end, PageRequest.of(0, TOP_ACTORS))
                .stream()
                .map(row -> new ActorActivity((UUID) row[0], (String) row[1], ((Number) row[2]).longValue()))
                .toList();

        long total = byOutcome.stream().mapToLong(CountByKey::total).sum();

        return new AuditSummaryResponse(start, end, total,
                totalFor(byOutcome, AuditOutcome.DENIED.name()),
                totalFor(byOutcome, AuditOutcome.FAILURE.name()),
                totalFor(bySeverity, AuditSeverity.CRITICAL.name()),
                byCategory, byOutcome, bySeverity, byService, topActors);
    }

    /** Convierte las filas {@code [clave, conteo]} de JPQL al par estable del contrato. */
    private List<CountByKey> toCounts(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new CountByKey(String.valueOf(row[0]), ((Number) row[1]).longValue()))
                .sorted(Comparator.comparingLong(CountByKey::total).reversed())
                .toList();
    }

    private long totalFor(List<CountByKey> counts, String key) {
        return counts.stream()
                .filter(count -> count.key().equals(key))
                .mapToLong(CountByKey::total)
                .findFirst()
                .orElse(0L);
    }

    private void validate(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new BusinessRuleException(ErrorCode.INVALID_AUDIT_RANGE,
                    "El inicio del rango debe ser anterior a su fin");
        }
        if (Duration.between(from, to).compareTo(MAX_WINDOW) > 0) {
            throw new BusinessRuleException(ErrorCode.INVALID_AUDIT_RANGE,
                    "El resumen admite ventanas de hasta " + MAX_WINDOW.toDays() + " dias");
        }
    }
}
