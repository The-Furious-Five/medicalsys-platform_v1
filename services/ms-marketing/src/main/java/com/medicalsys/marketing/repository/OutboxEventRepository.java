package com.medicalsys.marketing.repository;

import com.medicalsys.marketing.domain.enums.OutboxStatus;
import com.medicalsys.marketing.domain.model.OutboxEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso al outbox de eventos salientes. */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /** Lote de eventos por publicar, en orden de insercion. */
    List<OutboxEvent> findByStatusInOrderByIdAsc(List<OutboxStatus> statuses, Pageable pageable);

    List<OutboxEvent> findByAggregatePublicIdOrderByIdAsc(UUID aggregatePublicId);

    Optional<OutboxEvent> findByEventPublicId(UUID eventPublicId);

    long countByStatus(OutboxStatus status);
}
