package com.medicalsys.documental.repository;

import com.medicalsys.documental.domain.DocumentStatus;
import com.medicalsys.documental.domain.DocumentType;
import com.medicalsys.documental.domain.StoredDocument;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<StoredDocument, Long> {

    @EntityGraph(attributePaths = "links")
    @Query("""
            select d from StoredDocument d
            where d.publicId = :publicId
              and d.organizationPublicId = :organizationId
            """)
    Optional<StoredDocument> findDetailedByPublicIdAndOrganization(
            @Param("publicId") UUID publicId,
            @Param("organizationId") UUID organizationId);

    Optional<StoredDocument> findByPublicIdAndOrganizationPublicId(UUID publicId, UUID organizationPublicId);

    @Query("""
            select d from StoredDocument d
            where d.organizationPublicId = :organizationId
              and (:patientId is null or d.patientPublicId = :patientId)
              and (:type is null or d.type = :type)
              and (:status is null or d.status = :status)
            """)
    Page<StoredDocument> search(
            @Param("organizationId") UUID organizationId,
            @Param("patientId") UUID patientId,
            @Param("type") DocumentType type,
            @Param("status") DocumentStatus status,
            Pageable pageable);
}
