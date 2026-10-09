package com.medicalsys.documental.service;

import com.medicalsys.documental.api.dto.DocumentLinkResponse;
import com.medicalsys.documental.api.dto.DocumentResponse;
import com.medicalsys.documental.api.dto.DocumentSummaryResponse;
import com.medicalsys.documental.api.dto.InternalDocumentSummary;
import com.medicalsys.documental.domain.DocumentLink;
import com.medicalsys.documental.domain.StoredDocument;
import org.springframework.stereotype.Component;

@Component
public class DocumentMapper {

    public DocumentResponse toResponse(StoredDocument document) {
        return new DocumentResponse(
                document.getPublicId(),
                document.getPatientPublicId(),
                document.getType().name(),
                document.getOriginalFilename(),
                document.getMimeType(),
                document.getSizeBytes(),
                document.getSha256Hash(),
                document.isDicom(),
                document.getMetadata(),
                document.getStatus().name(),
                document.getAntivirusStatus().name(),
                document.getUploadedByUserPublicId(),
                document.getUploadedAt(),
                document.getRetentionUntil(),
                document.getLinks().stream().map(this::toLinkResponse).toList());
    }

    public DocumentSummaryResponse toSummary(StoredDocument document) {
        return new DocumentSummaryResponse(
                document.getPublicId(),
                document.getPatientPublicId(),
                document.getType().name(),
                document.getOriginalFilename(),
                document.getMimeType(),
                document.getSizeBytes(),
                document.getStatus().name(),
                document.getUploadedAt());
    }

    public InternalDocumentSummary toInternalSummary(StoredDocument document) {
        return new InternalDocumentSummary(
                document.getPublicId(),
                document.getPatientPublicId(),
                document.getType().name(),
                document.getMimeType(),
                document.getSizeBytes(),
                document.getSha256Hash(),
                document.getStatus().name());
    }

    public DocumentLinkResponse toLinkResponse(DocumentLink link) {
        return new DocumentLinkResponse(
                link.getEntityType().name(),
                link.getLinkedEntityPublicId(),
                link.getDescription(),
                link.getLinkedByUserPublicId(),
                link.getCreatedAt());
    }
}
