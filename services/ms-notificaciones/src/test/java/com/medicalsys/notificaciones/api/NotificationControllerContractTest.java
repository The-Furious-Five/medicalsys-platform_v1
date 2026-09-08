package com.medicalsys.notificaciones.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalsys.notificaciones.api.dto.NotificationResponse;
import com.medicalsys.notificaciones.common.CorrelationIdWebFilter;
import com.medicalsys.notificaciones.domain.NotificationCategory;
import com.medicalsys.notificaciones.domain.NotificationChannel;
import com.medicalsys.notificaciones.domain.NotificationStatus;
import com.medicalsys.notificaciones.error.ApiException;
import com.medicalsys.notificaciones.error.GlobalExceptionHandler;
import com.medicalsys.notificaciones.service.NotificationService;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class NotificationControllerContractTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID NOTIFICATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    @Mock NotificationService service;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToController(new NotificationController(service), new NotificationsPingController())
                .controllerAdvice(new GlobalExceptionHandler())
                .webFilter(new CorrelationIdWebFilter())
                .build();
    }

    @Test
    void createsNotificationWithLocationAndCorrelationId() {
        when(service.create(eq(ORGANIZATION_ID), any())).thenReturn(Mono.just(response(NotificationStatus.QUEUED)));
        client.post().uri("/api/v1/notifications")
                .header("X-Organization-Id", ORGANIZATION_ID.toString())
                .header("X-Correlation-Id", "trace-123")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validRequest())
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals("Location", "/api/v1/notifications/" + NOTIFICATION_ID)
                .expectHeader().valueEquals("X-Correlation-Id", "trace-123")
                .expectBody().jsonPath("$.status").isEqualTo("QUEUED");
    }

    @Test
    void rejectsInvalidE164DestinationUsingStandardErrorContract() {
        String invalid = validRequest().replace("+59170000000", "70000000");
        client.post().uri("/api/v1/notifications")
                .header("X-Organization-Id", ORGANIZATION_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalid)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.details[0].field").isEqualTo("destination");
    }

    @Test
    void rejectsMissingOrganizationHeader() {
        client.post().uri("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validRequest())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    void rejectsMalformedOrganizationHeader() {
        client.post().uri("/api/v1/notifications")
                .header("X-Organization-Id", "not-a-uuid")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validRequest())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_ORGANIZATION_ID");
    }

    @Test
    void exposesTenantFilteredStatusList() {
        when(service.list(ORGANIZATION_ID, NotificationStatus.SENT))
                .thenReturn(Flux.just(response(NotificationStatus.SENT)));
        client.get().uri("/api/v1/notifications?status=SENT")
                .header("X-Organization-Id", ORGANIZATION_ID.toString())
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$[0].status").isEqualTo("SENT");
        verify(service).list(ORGANIZATION_ID, NotificationStatus.SENT);
    }

    @Test
    void returnsStructuredNotFoundFromService() {
        when(service.get(ORGANIZATION_ID, NOTIFICATION_ID)).thenReturn(Mono.error(
                new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "No existe")));
        client.get().uri("/api/v1/notifications/{id}", NOTIFICATION_ID)
                .header("X-Organization-Id", ORGANIZATION_ID.toString())
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("NOTIFICATION_NOT_FOUND")
                .jsonPath("$.path").isEqualTo("/api/v1/notifications/" + NOTIFICATION_ID);
    }

    @Test
    void pingContractIsStable() {
        client.get().uri("/api/v1/notifications/ping").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("ms-notificaciones")
                .jsonPath("$.status").isEqualTo("ok");
    }

    private String validRequest() {
        return """
                {"originEventId":"30000000-0000-0000-0000-000000000001",
                 "patientId":"20000000-0000-0000-0000-000000000001",
                 "channel":"WHATSAPP","category":"UTILITY","eventType":"APPOINTMENT_CREATED",
                 "entityType":"APPOINTMENT","destination":"+59170000000","renderedBody":"Mensaje"}
                """;
    }

    private NotificationResponse response(NotificationStatus status) {
        LocalDateTime now = LocalDateTime.now();
        return new NotificationResponse(NOTIFICATION_ID.toString(), null, ORGANIZATION_ID.toString(), null,
                NotificationChannel.WHATSAPP, NotificationCategory.UTILITY, "EVENT", "ENTITY", null,
                "+59170000000", "Mensaje", status, now, 0, null, null, null, null, null, now, now);
    }
}
