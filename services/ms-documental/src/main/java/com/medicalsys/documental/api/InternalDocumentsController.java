package com.medicalsys.documental.api;

import com.medicalsys.documental.api.dto.InternalDocumentSummary;
import com.medicalsys.documental.service.DocumentService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/documents")
public class InternalDocumentsController {

    private final DocumentService service;

    public InternalDocumentsController(DocumentService service) {
        this.service = service;
    }

    @GetMapping("/{documentId}")
    public InternalDocumentSummary get(@PathVariable UUID documentId,
            @RequestHeader("X-Organization-Id") UUID organizationId) {
        return service.internalSummary(documentId, organizationId);
    }
}
