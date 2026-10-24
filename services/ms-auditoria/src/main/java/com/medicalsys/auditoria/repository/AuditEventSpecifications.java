package com.medicalsys.auditoria.repository;

import com.medicalsys.auditoria.domain.model.AuditEvent;
import com.medicalsys.auditoria.web.dto.AuditEventFilter;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Traduce los filtros de la pantalla a predicados JPA.
 *
 * <p>Se resuelve con {@code Specification} y no con un metodo derivado por combinacion porque los
 * doce criterios son opcionales e independientes: escribir un metodo por combinacion seria
 * imposible de mantener, y concatenar JPQL a mano abriria la puerta a inyeccion.
 */
public final class AuditEventSpecifications {

    private AuditEventSpecifications() {
    }

    /**
     * Arma la consulta del tenant con los criterios recibidos.
     *
     * <p>{@code organizationId} es un parametro aparte y no parte del filtro: lo resuelve
     * {@code AuditScope} desde el contexto de la solicitud y siempre se aplica, incluso si el filtro
     * llega vacio. Solo es nulo con alcance de plataforma, que ve todas las organizaciones.
     */
    public static Specification<AuditEvent> of(UUID organizationId, AuditEventFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (organizationId != null) {
                predicates.add(builder.equal(root.get("organizationId"), organizationId));
            }

            if (filter.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("occurredAt"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(builder.lessThan(root.get("occurredAt"), filter.to()));
            }
            if (filter.category() != null) {
                predicates.add(builder.equal(root.get("category"), filter.category()));
            }
            if (filter.outcome() != null) {
                predicates.add(builder.equal(root.get("outcome"), filter.outcome()));
            }
            if (filter.severity() != null) {
                predicates.add(builder.equal(root.get("severity"), filter.severity()));
            }
            if (hasText(filter.serviceName())) {
                predicates.add(builder.equal(root.get("serviceName"), filter.serviceName().trim()));
            }
            if (hasText(filter.action())) {
                predicates.add(builder.equal(root.get("action"),
                        filter.action().trim().toUpperCase(Locale.ROOT)));
            }
            if (hasText(filter.resourceType())) {
                predicates.add(builder.equal(root.get("resourceType"),
                        filter.resourceType().trim().toUpperCase(Locale.ROOT)));
            }
            if (filter.actorUserId() != null) {
                predicates.add(builder.equal(root.get("actorUserId"), filter.actorUserId()));
            }
            if (filter.patientPublicId() != null) {
                predicates.add(builder.equal(root.get("patientPublicId"), filter.patientPublicId()));
            }
            if (filter.resourcePublicId() != null) {
                predicates.add(builder.equal(root.get("resourcePublicId"), filter.resourcePublicId()));
            }
            if (hasText(filter.correlationId())) {
                predicates.add(builder.equal(root.get("correlationId"), filter.correlationId().trim()));
            }
            if (hasText(filter.q())) {
                // Busqueda de ultimo recurso sobre los campos legibles por una persona. No incluye
                // hashes ni identificadores: para esos hay filtros exactos, que si usan indice.
                String term = "%" + filter.q().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("action")), term),
                        builder.like(builder.lower(root.get("resourceType")), term),
                        builder.like(builder.lower(root.get("resourceLabel")), term),
                        builder.like(builder.lower(root.get("actorDisplayName")), term),
                        builder.like(builder.lower(root.get("reason")), term),
                        builder.like(builder.lower(root.get("requestPath")), term)));
            }

            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
