package com.medicalsys.documental.repository;

import com.medicalsys.documental.domain.ConsentEvent;
import com.medicalsys.documental.domain.ConsentEventType;
import com.medicalsys.documental.domain.InformedConsent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentEventRepository extends JpaRepository<ConsentEvent, Long> {

    Optional<ConsentEvent> findFirstByInformedConsentAndTypeOrderByOccurredAtDesc(
            InformedConsent informedConsent, ConsentEventType type);
}
