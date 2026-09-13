package com.medicalsys.tenencia.repository;

import com.medicalsys.tenencia.domain.enums.UsageMetric;
import com.medicalsys.tenencia.domain.model.PlanUsage;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** Acceso a las cuotas materializadas del tenant. */
public interface PlanUsageRepository extends JpaRepository<PlanUsage, Long> {

    List<PlanUsage> findByOrganizationPublicIdOrderByMetricAsc(UUID organizationId);

    Optional<PlanUsage> findByOrganizationPublicIdAndMetricAndPeriodStart(UUID organizationId, UsageMetric metric,
            LocalDate periodStart);

    /**
     * Contador vigente de una metrica: el de periodo abierto o el de inicio mas reciente.
     *
     * <p>Se bloquea la fila mientras se decide si queda cuota. Sin el bloqueo, dos altas simultaneas
     * podrian leer el mismo valor y ambas concluir que hay espacio para la ultima unidad.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT u FROM PlanUsage u
            WHERE u.organization.publicId = :organizationId
              AND u.metric = :metric
              AND (u.periodEnd IS NULL OR u.periodEnd >= :today)
            ORDER BY u.periodStart DESC
            """)
    List<PlanUsage> lockCurrent(UUID organizationId, UsageMetric metric, LocalDate today);

    /** Primer resultado de {@link #lockCurrent}, que es el contador vigente. */
    default Optional<PlanUsage> lockCurrentUsage(UUID organizationId, UsageMetric metric, LocalDate today) {
        return lockCurrent(organizationId, metric, today).stream().findFirst();
    }
}
