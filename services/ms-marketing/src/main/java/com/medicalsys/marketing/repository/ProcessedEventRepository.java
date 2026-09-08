package com.medicalsys.marketing.repository;

import com.medicalsys.marketing.domain.model.ProcessedEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso al inbox de idempotencia. */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
