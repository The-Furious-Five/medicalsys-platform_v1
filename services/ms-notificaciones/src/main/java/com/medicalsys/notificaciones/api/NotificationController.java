package com.medicalsys.notificaciones.api;

import com.medicalsys.notificaciones.api.dto.CreateNotificationRequest;
import com.medicalsys.notificaciones.api.dto.NotificationResponse;
import com.medicalsys.notificaciones.common.RequestOrganization;
import com.medicalsys.notificaciones.domain.NotificationStatus;
import com.medicalsys.notificaciones.security.RequiresPermission;
import com.medicalsys.notificaciones.service.NotificationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @PostMapping
    @RequiresPermission("notifications.notification.create")
    public Mono<ResponseEntity<NotificationResponse>> create(
            @RequestHeader(RequestOrganization.HEADER) String organizationHeader,
            @Valid @RequestBody CreateNotificationRequest request) {
        UUID organizationId = RequestOrganization.parse(organizationHeader);
        return service.create(organizationId, request)
                .map(response -> ResponseEntity.created(URI.create("/api/v1/notifications/" + response.id()))
                        .body(response));
    }

    @GetMapping
    @RequiresPermission("notifications.notification.read")
    public Flux<NotificationResponse> list(
            @RequestHeader(RequestOrganization.HEADER) String organizationHeader,
            @RequestParam(required = false) NotificationStatus status) {
        return service.list(RequestOrganization.parse(organizationHeader), status);
    }

    @GetMapping("/{notificationId}")
    @RequiresPermission("notifications.notification.read")
    public Mono<NotificationResponse> get(
            @RequestHeader(RequestOrganization.HEADER) String organizationHeader,
            @PathVariable UUID notificationId) {
        return service.get(RequestOrganization.parse(organizationHeader), notificationId);
    }

    @PostMapping("/{notificationId}/dispatch")
    @RequiresPermission("notifications.notification.dispatch")
    public Mono<NotificationResponse> dispatch(
            @RequestHeader(RequestOrganization.HEADER) String organizationHeader,
            @PathVariable UUID notificationId) {
        return service.dispatch(RequestOrganization.parse(organizationHeader), notificationId);
    }

    @PostMapping("/{notificationId}/retry")
    @RequiresPermission("notifications.notification.retry")
    public Mono<NotificationResponse> retry(
            @RequestHeader(RequestOrganization.HEADER) String organizationHeader,
            @PathVariable UUID notificationId) {
        return service.retry(RequestOrganization.parse(organizationHeader), notificationId);
    }
}
