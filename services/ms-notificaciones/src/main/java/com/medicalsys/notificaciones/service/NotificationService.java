package com.medicalsys.notificaciones.service;

import com.medicalsys.notificaciones.api.dto.CreateNotificationRequest;
import com.medicalsys.notificaciones.api.dto.NotificationResponse;
import com.medicalsys.notificaciones.domain.Notification;
import com.medicalsys.notificaciones.domain.MessageTemplate;
import com.medicalsys.notificaciones.domain.NotificationRule;
import com.medicalsys.notificaciones.domain.NotificationStatus;
import com.medicalsys.notificaciones.error.ApiException;
import com.medicalsys.notificaciones.provider.NotificationProvider;
import com.medicalsys.notificaciones.messaging.NotificationDomainEvent;
import com.medicalsys.notificaciones.repository.NotificationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final NotificationProvider provider;
    private final ObjectMapper objectMapper;
    private final ReferenceSyncService references;
    private final Clock clock;
    private final int maxAttempts;

    @Autowired
    public NotificationService(NotificationRepository repository, NotificationProvider provider,
            ObjectMapper objectMapper, ReferenceSyncService references,
            @Value("${medicalsys.notifications.max-attempts:3}") int maxAttempts) {
        this(repository, provider, objectMapper, references, Clock.systemUTC(), maxAttempts);
    }

    NotificationService(NotificationRepository repository, NotificationProvider provider,
            ObjectMapper objectMapper, ReferenceSyncService references, Clock clock, int maxAttempts) {
        this.repository = repository;
        this.provider = provider;
        this.objectMapper = objectMapper;
        this.references = references;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
    }

    public Mono<NotificationResponse> createFromEvent(NotificationDomainEvent event, NotificationRule rule,
            MessageTemplate template, String renderedBody) {
        String idempotencyKey = event.eventId() + ":" + rule.publicId();
        return repository.existsByIdempotencyKey(idempotencyKey).flatMap(exists -> {
            if (exists) {
                return Mono.empty();
            }
            LocalDateTime now = LocalDateTime.now(clock);
            LocalDateTime base = event.targetAt() != null ? event.targetAt()
                    : event.occurredAt() != null ? event.occurredAt() : now;
            LocalDateTime scheduledFor = base.plusMinutes(rule.offsetMinutes());
            NotificationStatus status = scheduledFor.isAfter(now)
                    ? NotificationStatus.SCHEDULED : NotificationStatus.QUEUED;
            Notification notification = new Notification(null, UUID.randomUUID().toString(),
                    event.eventId().toString(), idempotencyKey, event.organizationId().toString(),
                    event.patientId().toString(), null, template.channel(), template.category(), template.id(),
                    rule.id(), event.eventType().trim().toUpperCase(), event.entityType().trim().toUpperCase(),
                    value(event.entityId()), event.destination(), json(event.variables()), renderedBody, status,
                    scheduledFor, 0, null, null, null, null, null, null, null, null, now, now);
            return repository.save(notification).map(NotificationResponse::from);
        });
    }

    public Mono<NotificationResponse> create(UUID organizationId, CreateNotificationRequest request) {
        String idempotencyKey = request.originEventId() == null
                ? "notification:" + UUID.randomUUID()
                : "manual:" + request.originEventId();
        Mono<Boolean> duplicateCheck = repository.existsByIdempotencyKey(idempotencyKey);

        return duplicateCheck.flatMap(duplicate -> {
            if (duplicate) {
                return Mono.error(new ApiException(HttpStatus.CONFLICT, "DUPLICATE_ORIGIN_EVENT",
                        "Ya existe una notificacion para el evento de origen"));
            }
            LocalDateTime now = LocalDateTime.now(clock);
            LocalDateTime scheduledFor = request.scheduledFor() == null ? now : request.scheduledFor();
            NotificationStatus status = scheduledFor.isAfter(now)
                    ? NotificationStatus.SCHEDULED : NotificationStatus.QUEUED;
            Notification notification = new Notification(null, UUID.randomUUID().toString(),
                    value(request.originEventId()), idempotencyKey, organizationId.toString(), request.patientId().toString(),
                    value(request.destinationUserId()), request.channel(), request.category(), null, null,
                    request.eventType().trim().toUpperCase(), request.entityType().trim().toUpperCase(),
                    value(request.entityId()), request.destination(), json(request.variables()), request.renderedBody(),
                    status, scheduledFor, 0, null, null, null, null, null, null, null, null, now, now);
            return repository.save(notification).map(NotificationResponse::from);
        });
    }

    public Mono<NotificationResponse> get(UUID organizationId, UUID notificationId) {
        return find(organizationId, notificationId).map(NotificationResponse::from);
    }

    public Flux<NotificationResponse> list(UUID organizationId, NotificationStatus status) {
        Flux<Notification> values = status == null
                ? repository.findAllByOrganizationIdOrderByCreatedAtDesc(organizationId.toString())
                : repository.findAllByOrganizationIdAndStatusInOrderByCreatedAtDesc(
                        organizationId.toString(), List.of(status));
        return values.map(NotificationResponse::from);
    }

    public Mono<NotificationResponse> dispatch(UUID organizationId, UUID notificationId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return find(organizationId, notificationId).flatMap(notification -> {
            if (notification.status() != NotificationStatus.QUEUED
                    && notification.status() != NotificationStatus.SCHEDULED) {
                return Mono.error(new ApiException(HttpStatus.CONFLICT, "NOTIFICATION_NOT_DISPATCHABLE",
                        "Solo se pueden despachar notificaciones programadas o encoladas"));
            }
            if (notification.scheduledFor().isAfter(now)) {
                return Mono.error(new ApiException(HttpStatus.CONFLICT, "NOTIFICATION_NOT_DUE",
                        "La notificacion todavia no alcanzo su fecha programada"));
            }
            Mono<Boolean> allowed = notification.patientId() == null
                    ? Mono.just(true)
                    : references.isAllowed(notification.patientId(), notification.channel(), notification.category());
            return allowed.flatMap(canSend -> {
                if (!canSend) {
                    return repository.save(notification.providerStatus(NotificationStatus.REJECTED_OPT_OUT, now,
                            "CONSENT_REQUIRED", "El paciente no autorizo este canal y categoria", now));
                }
                return provider.send(notification)
                        .flatMap(result -> repository.save(notification.sent(result.providerMessageId(), now)))
                        .onErrorResume(error -> repository.save(notification.failed("PROVIDER_ERROR",
                                safeMessage(error), nextAttempt(notification.attempts() + 1, now), now)));
            }).map(NotificationResponse::from);
        });
    }

    public Mono<NotificationResponse> dispatchAutomatically(Notification notification) {
        LocalDateTime now = LocalDateTime.now(clock);
        Notification ready = notification.status() == NotificationStatus.FAILED
                ? notification.queuedAgain(now) : notification;
        Mono<Boolean> allowed = ready.patientId() == null
                ? Mono.just(true)
                : references.isAllowed(ready.patientId(), ready.channel(), ready.category());
        return allowed.flatMap(canSend -> {
            if (!canSend) {
                return repository.save(ready.providerStatus(NotificationStatus.REJECTED_OPT_OUT, now,
                        "CONSENT_REQUIRED", "El paciente no autorizo este canal y categoria", now));
            }
            return provider.send(ready)
                    .flatMap(result -> repository.save(ready.sent(result.providerMessageId(), now)))
                    .onErrorResume(error -> repository.save(ready.failed("PROVIDER_ERROR", safeMessage(error),
                            nextAttempt(ready.attempts() + 1, now), now)));
        }).map(NotificationResponse::from);
    }

    public Mono<NotificationResponse> retry(UUID organizationId, UUID notificationId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return find(organizationId, notificationId).flatMap(notification -> {
            if (notification.status() != NotificationStatus.FAILED) {
                return Mono.error(new ApiException(HttpStatus.CONFLICT, "NOTIFICATION_NOT_FAILED",
                        "Solo se pueden reintentar notificaciones fallidas"));
            }
            if (notification.attempts() >= maxAttempts) {
                return Mono.error(new ApiException(HttpStatus.CONFLICT, "MAX_ATTEMPTS_REACHED",
                        "La notificacion alcanzo el maximo de intentos permitido"));
            }
            return repository.save(notification.queuedAgain(now)).map(NotificationResponse::from);
        });
    }

    private Mono<Notification> find(UUID organizationId, UUID notificationId) {
        return repository.findByPublicIdAndOrganizationId(notificationId.toString(), organizationId.toString())
                .switchIfEmpty(Mono.error(new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND",
                        "No existe la notificacion solicitada en esta organizacion")));
    }

    private LocalDateTime nextAttempt(int attempts, LocalDateTime now) {
        if (attempts >= maxAttempts) {
            return null;
        }
        long minutes = Math.min(60, 1L << Math.min(attempts, 5));
        return now.plusMinutes(minutes);
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VARIABLES",
                    "Las variables de la notificacion no son JSON valido");
        }
    }

    private String value(UUID value) {
        return value == null ? null : value.toString();
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "El proveedor rechazo el envio" : message.substring(0,
                Math.min(message.length(), 500));
    }
}
