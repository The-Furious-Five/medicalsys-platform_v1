package com.medicalsys.documental.api;

import com.medicalsys.documental.api.dto.ConsentTemplateResponse;
import com.medicalsys.documental.api.dto.CreateConsentTemplateRequest;
import com.medicalsys.documental.security.RequestIdentity;
import com.medicalsys.documental.security.RequiresRole;
import com.medicalsys.documental.security.Role;
import com.medicalsys.documental.service.ConsentTemplateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Gestion de plantillas legales: mas sensible que subir un documento suelto, por eso el rol es mas alto. */
@Validated
@RestController
@RequestMapping("/api/v1/consentimientos/plantillas")
public class ConsentTemplateController {

    private final ConsentTemplateService service;

    public ConsentTemplateController(ConsentTemplateService service) {
        this.service = service;
    }

    @PostMapping
    @RequiresRole({Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public ResponseEntity<ConsentTemplateResponse> create(@Valid @RequestBody CreateConsentTemplateRequest request,
            HttpServletRequest servletRequest) {
        RequestIdentity identity = RequestIdentity.from(servletRequest);
        ConsentTemplateResponse response = service.create(request, identity.organizationId(), identity.userId());
        return ResponseEntity.created(URI.create("/api/v1/consentimientos/plantillas/" + response.templateId()))
                .body(response);
    }

    @GetMapping
    @RequiresRole({Role.PHYSICIAN, Role.RECEPTIONIST, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public List<ConsentTemplateResponse> list(HttpServletRequest servletRequest) {
        return service.list(RequestIdentity.from(servletRequest).organizationId());
    }

    @GetMapping("/{templateId}")
    @RequiresRole({Role.PHYSICIAN, Role.RECEPTIONIST, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public ConsentTemplateResponse get(@PathVariable UUID templateId, HttpServletRequest servletRequest) {
        return service.get(templateId, RequestIdentity.from(servletRequest).organizationId());
    }

    @PostMapping("/{templateId}/publicacion")
    @RequiresRole({Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public ConsentTemplateResponse publish(@PathVariable UUID templateId, HttpServletRequest servletRequest) {
        return service.publish(templateId, RequestIdentity.from(servletRequest).organizationId());
    }
}
