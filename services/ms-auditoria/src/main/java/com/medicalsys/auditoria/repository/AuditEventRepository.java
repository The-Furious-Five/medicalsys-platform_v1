package com.medicalsys.auditoria.repository;

import com.medicalsys.auditoria.domain.model.AuditEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso de solo lectura y alta a la bitacora.
 *
 * <p>No declara ningun metodo de borrado ni de actualizacion a proposito: {@code JpaRepository}
 * hereda {@code delete*}, pero ningun servicio ni controlador de este microservicio los invoca y la
 * entidad marca todas sus columnas como {@code updatable = false}.
 *
 * <p>Toda consulta lleva {@code organizationId}: un auditor de una clinica no puede ver ni contar
 * los eventos de otra.
 */
public interface AuditEventRepository
        extends JpaRepository<AuditEvent, Long>, JpaSpecificationExecutor<AuditEvent> {

    Optional<AuditEvent> findByOrganizationIdAndPublicId(UUID organizationId, UUID publicId);

    boolean existsByPublicId(UUID publicId);

    /** Tramo de la cadena usado por la verificacion de integridad, en orden de asiento. */
    Page<AuditEvent> findByOrganizationIdAndSequenceNumberGreaterThanEqualOrderBySequenceNumberAsc(
            UUID organizationId, long fromSequenceNumber, Pageable pageable);

    /**
     * Conteos agrupados para el resumen de la pantalla.
     *
     * <p>El agrupamiento se resuelve en el motor y no en memoria: el resumen de un mes puede cubrir
     * cientos de miles de filas y traerlas al servicio solo para contarlas seria inaceptable.
     */
    @Query("""
            SELECT e.category, COUNT(e)
            FROM AuditEvent e
            WHERE e.organizationId = :organizationId
              AND e.occurredAt >= :from AND e.occurredAt < :to
            GROUP BY e.category
            """)
    List<Object[]> countByCategory(@Param("organizationId") UUID organizationId,
            @Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

    @Query("""
            SELECT e.outcome, COUNT(e)
            FROM AuditEvent e
            WHERE e.organizationId = :organizationId
              AND e.occurredAt >= :from AND e.occurredAt < :to
            GROUP BY e.outcome
            """)
    List<Object[]> countByOutcome(@Param("organizationId") UUID organizationId,
            @Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

    @Query("""
            SELECT e.severity, COUNT(e)
            FROM AuditEvent e
            WHERE e.organizationId = :organizationId
              AND e.occurredAt >= :from AND e.occurredAt < :to
            GROUP BY e.severity
            """)
    List<Object[]> countBySeverity(@Param("organizationId") UUID organizationId,
            @Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

    @Query("""
            SELECT e.serviceName, COUNT(e)
            FROM AuditEvent e
            WHERE e.organizationId = :organizationId
              AND e.occurredAt >= :from AND e.occurredAt < :to
            GROUP BY e.serviceName
            """)
    List<Object[]> countByService(@Param("organizationId") UUID organizationId,
            @Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

    /**
     * Actores mas activos de la ventana.
     *
     * <p>Se pagina desde el llamador para quedarse solo con los primeros; sin {@code Pageable} un
     * tenant con miles de usuarios devolveria una lista inutilizable para la pantalla.
     */
    @Query("""
            SELECT e.actorUserId, e.actorDisplayName, COUNT(e) AS total
            FROM AuditEvent e
            WHERE e.organizationId = :organizationId
              AND e.actorUserId IS NOT NULL
              AND e.occurredAt >= :from AND e.occurredAt < :to
            GROUP BY e.actorUserId, e.actorDisplayName
            ORDER BY total DESC
            """)
    List<Object[]> countByActor(@Param("organizationId") UUID organizationId,
            @Param("from") java.time.Instant from, @Param("to") java.time.Instant to, Pageable pageable);
}
