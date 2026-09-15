package com.medicalsys.documental.repository;

import com.medicalsys.documental.domain.ConsentTemplate;
import com.medicalsys.documental.domain.ConsentTemplateStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentTemplateRepository extends JpaRepository<ConsentTemplate, Long> {

    Optional<ConsentTemplate> findByPublicIdAndOrganizationPublicId(UUID publicId, UUID organizationPublicId);

    List<ConsentTemplate> findAllByOrganizationPublicIdOrderByCodeAscVersionDesc(UUID organizationPublicId);

    /**
     * La version publicada mas reciente de un codigo, para esta organizacion. No resuelve
     * plantillas globales ({@code organization_public_id IS NULL}): en el MVP toda plantilla se crea
     * ya por organizacion.
     */
    Optional<ConsentTemplate> findFirstByOrganizationPublicIdAndCodeAndStatusOrderByVersionDesc(
            UUID organizationPublicId, String code, ConsentTemplateStatus status);
}
