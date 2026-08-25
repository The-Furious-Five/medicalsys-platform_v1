package com.medicalsys.documental.repository;

import com.medicalsys.documental.domain.DocumentLink;
import com.medicalsys.documental.domain.LinkedEntityType;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentLinkRepository extends JpaRepository<DocumentLink, Long> {

    boolean existsByDocumentIdAndEntityTypeAndLinkedEntityPublicId(
            Long documentId, LinkedEntityType entityType, UUID linkedEntityPublicId);
}
