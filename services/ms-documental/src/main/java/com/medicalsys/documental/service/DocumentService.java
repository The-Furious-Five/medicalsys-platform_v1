package com.medicalsys.documental.service;

import com.medicalsys.documental.api.dto.ClassifyDocumentRequest;
import com.medicalsys.documental.api.dto.DocumentLinkRequest;
import com.medicalsys.documental.api.dto.DocumentLinkResponse;
import com.medicalsys.documental.api.dto.DocumentPageResponse;
import com.medicalsys.documental.api.dto.DocumentResponse;
import com.medicalsys.documental.api.dto.InternalDocumentSummary;
import com.medicalsys.documental.api.dto.UploadDocumentRequest;
import com.medicalsys.documental.config.StorageProperties;
import com.medicalsys.documental.domain.DocumentLink;
import com.medicalsys.documental.domain.DocumentStatus;
import com.medicalsys.documental.domain.DocumentType;
import com.medicalsys.documental.domain.StoredDocument;
import com.medicalsys.documental.exception.ApiException;
import com.medicalsys.documental.exception.ErrorCode;
import com.medicalsys.documental.repository.DocumentLinkRepository;
import com.medicalsys.documental.repository.DocumentRepository;
import com.medicalsys.documental.storage.ObjectStorage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentLinkRepository linkRepository;
    private final ObjectStorage storage;
    private final StorageProperties storageProperties;
    private final HashingService hashingService;
    private final FilenameSanitizer filenameSanitizer;
    private final DocumentFilePolicy filePolicy;
    private final DocumentMapper mapper;

    public DocumentService(DocumentRepository documentRepository, DocumentLinkRepository linkRepository,
            ObjectStorage storage, StorageProperties storageProperties, HashingService hashingService,
            FilenameSanitizer filenameSanitizer, DocumentFilePolicy filePolicy, DocumentMapper mapper) {
        this.documentRepository = documentRepository;
        this.linkRepository = linkRepository;
        this.storage = storage;
        this.storageProperties = storageProperties;
        this.hashingService = hashingService;
        this.filenameSanitizer = filenameSanitizer;
        this.filePolicy = filePolicy;
        this.mapper = mapper;
    }

    @Transactional
    public DocumentResponse upload(MultipartFile file, UploadDocumentRequest request,
            UUID organizationId, UUID userId) {
        validateFile(file);
        validateRetention(request.retentionUntil());

        String mimeType = filePolicy.validateAndNormalize(request.type(), file.getContentType());
        String originalFilename = requiredFilename(file.getOriginalFilename());
        UUID documentId = UUID.randomUUID();
        String objectKey = objectKey(organizationId, documentId, filenameSanitizer.safe(originalFilename));
        String sha256 = hash(file);
        boolean dicom = isDicom(request.type(), mimeType, request.dicom());

        ObjectStorage.StoredObject storedObject;
        try (InputStream input = file.getInputStream()) {
            storedObject = storage.put(objectKey, input, file.getSize(), mimeType,
                    Map.of("sha256", sha256, "document-id", documentId.toString()));
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "No fue posible leer el archivo recibido", exception);
        }

        StoredDocument document = StoredDocument.available(
                documentId,
                organizationId,
                request.patientId(),
                request.type(),
                originalFilename,
                mimeType,
                file.getSize(),
                storageProperties.bucket(),
                objectKey,
                storedObject.versionId(),
                sha256,
                dicom,
                request.metadata(),
                userId,
                request.retentionUntil());

        if (request.link() != null) {
            document.addLink(newLink(document, request.link(), userId));
        }

        try {
            StoredDocument saved = documentRepository.saveAndFlush(document);
            return mapper.toResponse(saved);
        } catch (RuntimeException exception) {
            storage.delete(objectKey);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(UUID documentId, UUID organizationId) {
        return mapper.toResponse(findDetailed(documentId, organizationId));
    }

    @Transactional(readOnly = true)
    public DocumentPageResponse search(UUID organizationId, UUID patientId, DocumentType type,
            DocumentStatus status, int page, int size) {
        Page<StoredDocument> result = documentRepository.search(
                organizationId,
                patientId,
                type,
                status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "uploadedAt")));
        return new DocumentPageResponse(
                result.getContent().stream().map(mapper::toSummary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional
    public DocumentResponse classify(UUID documentId, ClassifyDocumentRequest request,
            UUID organizationId) {
        StoredDocument document = findDetailed(documentId, organizationId);
        filePolicy.validateAndNormalize(request.type(), document.getMimeType());
        boolean dicom = isDicom(request.type(), document.getMimeType(), request.dicom());
        document.classify(request.type(), dicom, request.metadata());
        return mapper.toResponse(documentRepository.saveAndFlush(document));
    }

    @Transactional
    public DocumentLinkResponse addLink(UUID documentId, DocumentLinkRequest request,
            UUID organizationId, UUID userId) {
        StoredDocument document = findDetailed(documentId, organizationId);
        if (linkRepository.existsByDocumentIdAndEntityTypeAndLinkedEntityPublicId(
                document.getId(), request.entityType(), request.entityId())) {
            throw new ApiException(ErrorCode.DOCUMENT_LINK_ALREADY_EXISTS,
                    "El documento ya esta vinculado a la entidad indicada");
        }
        DocumentLink link = newLink(document, request, userId);
        document.addLink(link);
        DocumentLink saved = linkRepository.saveAndFlush(link);
        return mapper.toLinkResponse(saved);
    }

    @Transactional(readOnly = true)
    public InternalDocumentSummary internalSummary(UUID documentId, UUID organizationId) {
        return mapper.toInternalSummary(find(documentId, organizationId));
    }

    @Transactional(readOnly = true)
    public PreparedDownload prepareDownload(UUID documentId, UUID organizationId) {
        StoredDocument document = find(documentId, organizationId);
        if (document.getStatus() != DocumentStatus.AVAILABLE) {
            throw new ApiException(ErrorCode.DOCUMENT_NOT_AVAILABLE,
                    "El documento no se encuentra disponible para descarga");
        }

        Path temporaryFile = createTemporaryFile(document.getPublicId());
        try (InputStream input = storage.get(document.getObjectKey());
                OutputStream output = Files.newOutputStream(temporaryFile)) {
            String actualHash = hashingService.copyAndSha256(input, output);
            long actualSize = Files.size(temporaryFile);
            if (!document.getSha256Hash().equalsIgnoreCase(actualHash)
                    || document.getSizeBytes() != actualSize) {
                throw new ApiException(ErrorCode.DOCUMENT_INTEGRITY_FAILURE,
                        "El archivo almacenado no coincide con sus metadatos de integridad");
            }
            return new PreparedDownload(
                    temporaryFile,
                    document.getOriginalFilename(),
                    document.getMimeType(),
                    actualSize,
                    actualHash);
        } catch (IOException exception) {
            deleteTemporary(temporaryFile);
            throw new ApiException(ErrorCode.STORAGE_UNAVAILABLE,
                    "No fue posible preparar el archivo para descarga", exception);
        } catch (RuntimeException exception) {
            deleteTemporary(temporaryFile);
            throw exception;
        }
    }

    private StoredDocument find(UUID documentId, UUID organizationId) {
        return documentRepository.findByPublicIdAndOrganizationPublicId(documentId, organizationId)
                .orElseThrow(() -> new ApiException(ErrorCode.DOCUMENT_NOT_FOUND,
                        "No existe un documento visible con el identificador indicado"));
    }

    private StoredDocument findDetailed(UUID documentId, UUID organizationId) {
        return documentRepository.findDetailedByPublicIdAndOrganization(documentId, organizationId)
                .orElseThrow(() -> new ApiException(ErrorCode.DOCUMENT_NOT_FOUND,
                        "No existe un documento visible con el identificador indicado"));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new ApiException(ErrorCode.EMPTY_FILE, "El archivo no puede estar vacio");
        }
        if (file.getSize() > storageProperties.maxSizeBytes()) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE,
                    "El archivo supera el limite de " + storageProperties.maxSizeBytes() + " bytes");
        }
    }

    private void validateRetention(LocalDate retentionUntil) {
        if (retentionUntil != null && retentionUntil.isBefore(LocalDate.now(ZoneOffset.UTC))) {
            throw new ApiException(ErrorCode.INVALID_RETENTION_DATE,
                    "La fecha de retencion no puede encontrarse en el pasado");
        }
    }

    private String requiredFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "El archivo debe incluir un nombre original");
        }
        String normalized = originalFilename.trim().replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        if (normalized.isBlank() || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "El nombre original contiene caracteres no permitidos");
        }
        if (normalized.length() > 255) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "El nombre original no puede superar 255 caracteres");
        }
        return normalized;
    }

    private String hash(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return hashingService.sha256(input);
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "No fue posible leer el archivo recibido", exception);
        }
    }

    private String objectKey(UUID organizationId, UUID documentId, String safeFilename) {
        return organizationId + "/" + documentId + "/v1/" + safeFilename;
    }

    private boolean isDicom(DocumentType type, String mimeType, Boolean requested) {
        return type == DocumentType.DICOM_STUDY
                || "application/dicom".equals(mimeType)
                || Boolean.TRUE.equals(requested);
    }

    private DocumentLink newLink(StoredDocument document, DocumentLinkRequest request, UUID userId) {
        String description = request.description() == null || request.description().isBlank()
                ? null
                : request.description().trim();
        return new DocumentLink(document, request.entityType(), request.entityId(), description, userId);
    }

    private Path createTemporaryFile(UUID documentId) {
        try {
            return Files.createTempFile("medicalsys-document-" + documentId + "-", ".download");
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "No fue posible crear el archivo temporal de descarga", exception);
        }
    }

    private void deleteTemporary(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // El directorio temporal del sistema eliminara cualquier residuo al reiniciar.
        }
    }
}
