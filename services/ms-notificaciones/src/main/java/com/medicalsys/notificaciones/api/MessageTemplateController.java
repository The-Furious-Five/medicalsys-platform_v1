package com.medicalsys.notificaciones.api;

import com.medicalsys.notificaciones.api.dto.MessageTemplateRequest;
import com.medicalsys.notificaciones.api.dto.MessageTemplateResponse;
import com.medicalsys.notificaciones.common.RequestOrganization;
import com.medicalsys.notificaciones.service.MessageTemplateService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/notifications/templates")
public class MessageTemplateController {

    private final MessageTemplateService service;

    public MessageTemplateController(MessageTemplateService service) {
        this.service = service;
    }

    @PostMapping
    public Mono<ResponseEntity<MessageTemplateResponse>> create(
            @RequestHeader(RequestOrganization.HEADER) String organizationHeader,
            @Valid @RequestBody MessageTemplateRequest request) {
        return service.create(RequestOrganization.parse(organizationHeader), request)
                .map(response -> ResponseEntity.created(
                        URI.create("/api/v1/notifications/templates/" + response.id())).body(response));
    }

    @GetMapping
    public Flux<MessageTemplateResponse> list(
            @RequestHeader(RequestOrganization.HEADER) String organizationHeader) {
        return service.list(RequestOrganization.parse(organizationHeader));
    }

    @GetMapping("/{templateId}")
    public Mono<MessageTemplateResponse> get(
            @RequestHeader(RequestOrganization.HEADER) String organizationHeader,
            @PathVariable UUID templateId) {
        return service.get(RequestOrganization.parse(organizationHeader), templateId);
    }

    @PutMapping("/{templateId}")
    public Mono<MessageTemplateResponse> update(
            @RequestHeader(RequestOrganization.HEADER) String organizationHeader,
            @PathVariable UUID templateId,
            @Valid @RequestBody MessageTemplateRequest request) {
        return service.update(RequestOrganization.parse(organizationHeader), templateId, request);
    }
}
