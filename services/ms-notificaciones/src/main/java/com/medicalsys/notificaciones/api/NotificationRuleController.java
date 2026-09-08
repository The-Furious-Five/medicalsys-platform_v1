package com.medicalsys.notificaciones.api;

import com.medicalsys.notificaciones.api.dto.NotificationRuleRequest;
import com.medicalsys.notificaciones.api.dto.NotificationRuleResponse;
import com.medicalsys.notificaciones.common.RequestOrganization;
import com.medicalsys.notificaciones.service.NotificationRuleService;
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
@RequestMapping("/api/v1/notifications/rules")
public class NotificationRuleController {

    private final NotificationRuleService service;

    public NotificationRuleController(NotificationRuleService service) {
        this.service = service;
    }

    @PostMapping
    public Mono<ResponseEntity<NotificationRuleResponse>> create(
            @RequestHeader(RequestOrganization.HEADER) String organizationHeader,
            @Valid @RequestBody NotificationRuleRequest request) {
        return service.create(RequestOrganization.parse(organizationHeader), request)
                .map(response -> ResponseEntity.created(
                        URI.create("/api/v1/notifications/rules/" + response.id())).body(response));
    }

    @GetMapping
    public Flux<NotificationRuleResponse> list(
            @RequestHeader(RequestOrganization.HEADER) String organizationHeader) {
        return service.list(RequestOrganization.parse(organizationHeader));
    }

    @PutMapping("/{ruleId}")
    public Mono<NotificationRuleResponse> update(
            @RequestHeader(RequestOrganization.HEADER) String organizationHeader,
            @PathVariable UUID ruleId,
            @Valid @RequestBody NotificationRuleRequest request) {
        return service.update(RequestOrganization.parse(organizationHeader), ruleId, request);
    }
}
