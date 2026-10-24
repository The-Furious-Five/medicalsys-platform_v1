package com.medicalsys.documental.api;

import com.medicalsys.documental.api.dto.ClassifyDocumentRequest;
import com.medicalsys.documental.api.dto.DocumentIntegrityResponse;
import com.medicalsys.documental.api.dto.DocumentLinkRequest;
import com.medicalsys.documental.api.dto.DocumentLinkResponse;
import com.medicalsys.documental.api.dto.DocumentPageResponse;
import com.medicalsys.documental.api.dto.DocumentResponse;
import com.medicalsys.documental.api.dto.UploadDocumentRequest;
import com.medicalsys.documental.domain.DocumentStatus;
import com.medicalsys.documental.domain.DocumentType;
import com.medicalsys.documental.security.RequestIdentity;
import com.medicalsys.documental.security.RequiresPermission;
import com.medicalsys.documental.service.DocumentService;
import com.medicalsys.documental.service.PreparedDownload;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Validated
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentsController {

    private final DocumentService service;

    public DocumentsController(DocumentService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission("documents.document.upload")
    public ResponseEntity<DocumentResponse> upload(
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("metadata") UploadDocumentRequest metadata,
            HttpServletRequest servletRequest) {
        RequestIdentity identity = RequestIdentity.from(servletRequest);
        DocumentResponse response = service.upload(
                file, metadata, identity.organizationId(), identity.userId());
        URI location = URI.create("/api/v1/documents/" + response.documentId());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{documentId}")
    @RequiresPermission("documents.document.read")
    public DocumentResponse get(@PathVariable UUID documentId, HttpServletRequest servletRequest) {
        return service.get(documentId, RequestIdentity.from(servletRequest).organizationId());
    }

    @GetMapping
    @RequiresPermission("documents.document.read")
    public DocumentPageResponse search(
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) DocumentType type,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page no puede ser negativo") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size debe ser mayor que cero")
            @Max(value = 100, message = "size no puede superar 100") int size,
            HttpServletRequest servletRequest) {
        return service.search(RequestIdentity.from(servletRequest).organizationId(),
                patientId, type, status, page, size);
    }

    @PatchMapping("/{documentId}/classification")
    @RequiresPermission("documents.document.classify")
    public DocumentResponse classify(@PathVariable UUID documentId,
            @Valid @RequestBody ClassifyDocumentRequest request,
            HttpServletRequest servletRequest) {
        return service.classify(documentId, request,
                RequestIdentity.from(servletRequest).organizationId());
    }

    @PostMapping("/{documentId}/links")
    @RequiresPermission("documents.document.link")
    public ResponseEntity<DocumentLinkResponse> addLink(@PathVariable UUID documentId,
            @Valid @RequestBody DocumentLinkRequest request,
            HttpServletRequest servletRequest) {
        RequestIdentity identity = RequestIdentity.from(servletRequest);
        DocumentLinkResponse response = service.addLink(
                documentId, request, identity.organizationId(), identity.userId());
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/{documentId}/content")
    @RequiresPermission("documents.document.download")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable UUID documentId,
            HttpServletRequest servletRequest) {
        PreparedDownload download = service.prepareDownload(
                documentId, RequestIdentity.from(servletRequest).organizationId());
        StreamingResponseBody body = output -> {
            try {
                Files.copy(download.temporaryFile(), output);
            } finally {
                Files.deleteIfExists(download.temporaryFile());
            }
        };

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(download.mimeType());
        } catch (IllegalArgumentException ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.originalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-SHA256", download.sha256Hash())
                .body(body);
    }

    @GetMapping("/{documentId}/integrity")
    // CRY-02: consultar la integridad es leer metadatos del documento (mismos roles que documents.document.read).
    @RequiresPermission("documents.document.read")
    public DocumentIntegrityResponse integrity(@PathVariable UUID documentId,
            HttpServletRequest servletRequest) {
        return DocumentIntegrityResponse.from(service.checkIntegrity(
                documentId, RequestIdentity.from(servletRequest).organizationId()));
    }
}
