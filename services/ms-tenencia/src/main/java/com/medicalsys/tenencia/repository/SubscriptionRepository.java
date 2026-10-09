package com.medicalsys.tenencia.repository;

import com.medicalsys.tenencia.domain.enums.SubscriptionStatus;
import com.medicalsys.tenencia.domain.model.Subscription;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Acceso a los contratos de suscripcion.
 *
 * <p>Las consultas se acotan por {@code organization.publicId} y aprovechan el indice
 * {@code ix_subscription_organization_id_status} del diccionario.
 */
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    /**
     * Suscripcion vigente del tenant.
     *
     * <p>Se excluye CANCELLED y se toma la de inicio mas reciente: una organizacion puede acumular
     * contratos historicos, pero solo uno ocupa el lugar del actual.
     */
    @Query("""
            SELECT s FROM Subscription s
            JOIN FETCH s.plan
            WHERE s.organization.publicId = :organizationId
              AND s.status <> com.medicalsys.tenencia.domain.enums.SubscriptionStatus.CANCELLED
            ORDER BY s.startDate DESC, s.id DESC
            """)
    List<Subscription> findCurrent(UUID organizationId);

    /** Primer resultado de {@link #findCurrent(UUID)}, que es la suscripcion vigente. */
    default Optional<Subscription> findCurrentByOrganization(UUID organizationId) {
        return findCurrent(organizationId).stream().findFirst();
    }

    @Query("""
            SELECT s FROM Subscription s
            JOIN FETCH s.plan
            WHERE s.organization.publicId = :organizationId
            ORDER BY s.startDate DESC, s.id DESC
            """)
    List<Subscription> findHistory(UUID organizationId);

    boolean existsByOrganizationPublicIdAndStatus(UUID organizationId, SubscriptionStatus status);
}
