package com.medicalsys.auditoria.config;

import com.medicalsys.auditoria.domain.enums.AuditCategory;
import com.medicalsys.auditoria.domain.enums.AuditOutcome;
import com.medicalsys.auditoria.domain.enums.AuditSeverity;
import com.medicalsys.auditoria.repository.AuditChainHeadRepository;
import com.medicalsys.auditoria.service.AuditEventService;
import com.medicalsys.auditoria.web.dto.RecordAuditEventRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Bitacora de ejemplo para la demostracion local.
 *
 * <p>Se siembra desde Java y no con una migracion SQL a proposito: los eventos van encadenados por
 * hash, y reimplementar ese calculo en SQL crearia una segunda version de la regla que se
 * desincronizaria con la primera. Pasando por el mismo servicio que usan los microservicios reales,
 * la cadena sembrada verifica igual que la de produccion.
 *
 * <p>Solo se activa con {@code medicalsys.audit.demo-seed.enabled=true} y unicamente si la
 * organizacion demo todavia no tiene eventos, para no crecer en cada reinicio.
 */
@Component
@ConditionalOnProperty(name = "medicalsys.audit.demo-seed.enabled", havingValue = "true")
public class DemoAuditSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAuditSeeder.class);

    /** Clinica Demo de tenancy_db (V2__seed_demo.sql de ms-tenencia). */
    private static final UUID DEMO_ORGANIZATION = UUID.fromString("00000000-0000-4000-8000-0000000000a1");

    /*
     * Actores reales, no inventados: son las cuentas que siembra DemoUserSeeder de
     * ms-identidad. Con los UUID que habia antes (20000000-...f1/f2/f3), la bitacora
     * atribuia hechos a usuarios que no existen en identity_db, y cruzar un asiento con
     * su cuenta -que es exactamente lo que se hace al auditar- no llevaba a ninguna parte.
     */
    private static final UUID DEMO_ADMIN = UUID.fromString("00000000-0000-4000-8000-000000000901");
    private static final UUID DEMO_RECEPTIONIST = UUID.fromString("00000000-0000-4000-8000-000000000902");
    private static final UUID DEMO_PHYSICIAN = UUID.fromString("00000000-0000-4000-8000-000000000903");
    private static final UUID DEMO_PATIENT = UUID.fromString("10000000-0000-4000-8000-000000000001");

    private final AuditEventService auditEvents;
    private final AuditChainHeadRepository chainHeads;
    private final Clock clock;

    public DemoAuditSeeder(AuditEventService auditEvents, AuditChainHeadRepository chainHeads, Clock clock) {
        this.auditEvents = auditEvents;
        this.chainHeads = chainHeads;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean alreadySeeded = chainHeads.findById(DEMO_ORGANIZATION)
                .filter(head -> head.getLastSequenceNumber() > 0)
                .isPresent();
        if (alreadySeeded) {
            return;
        }

        Instant now = clock.instant();
        seed().forEach(event -> auditEvents.record(shiftedTo(now, event)));
        log.info("Bitacora de demostracion sembrada para la organizacion {}", DEMO_ORGANIZATION);
    }

    /**
     * Guion de la demostracion: un dia de trabajo con sus tropiezos.
     *
     * <p>Incluye a proposito un login fallido, una denegacion por rol y una exportacion, que son los
     * tres casos que la pantalla de auditoria tiene que saber destacar.
     */
    private List<SeedEvent> seed() {
        return List.of(
                new SeedEvent(Duration.ofHours(9), "ms-identidad", "LOGIN_FAILED",
                        AuditCategory.AUTHENTICATION, AuditSeverity.WARNING, AuditOutcome.FAILURE,
                        DEMO_RECEPTIONIST, "Elena Cruz Apaza", List.of("RECEPTIONIST"), "USER_ACCOUNT", null,
                        null, null, "POST", "/api/v1/identity/login", 401,
                        "Credenciales invalidas", Map.of("attempt", "1")),
                new SeedEvent(Duration.ofHours(8).plusMinutes(58), "ms-identidad", "LOGIN_SUCCEEDED",
                        AuditCategory.AUTHENTICATION, AuditSeverity.INFO, AuditOutcome.SUCCESS,
                        DEMO_RECEPTIONIST, "Elena Cruz Apaza", List.of("RECEPTIONIST"), "USER_ACCOUNT", null,
                        null, null, "POST", "/api/v1/identity/login", 200, null, Map.of()),
                new SeedEvent(Duration.ofHours(8).plusMinutes(45), "ms-pacientes", "PATIENT_REGISTERED",
                        AuditCategory.PATIENT_DATA, AuditSeverity.NOTICE, AuditOutcome.SUCCESS,
                        DEMO_RECEPTIONIST, "Elena Cruz Apaza", List.of("RECEPTIONIST"), "PATIENT",
                        DEMO_PATIENT, "Quispe Mamani Maria Elena", DEMO_PATIENT, "POST",
                        "/api/v1/patients", 201, null, Map.of("documentType", "CI")),
                new SeedEvent(Duration.ofHours(7).plusMinutes(30), "ms-agenda", "APPOINTMENT_BOOKED",
                        AuditCategory.SCHEDULING, AuditSeverity.INFO, AuditOutcome.SUCCESS,
                        DEMO_RECEPTIONIST, "Elena Cruz Apaza", List.of("RECEPTIONIST"), "APPOINTMENT",
                        UUID.fromString("30000000-0000-4000-8000-000000000001"), "Consulta general",
                        DEMO_PATIENT, "POST", "/api/v1/scheduling/appointments", 201, null, Map.of()),
                new SeedEvent(Duration.ofHours(5), "ms-historial", "MEDICAL_RECORD_VIEWED",
                        AuditCategory.CLINICAL_RECORD, AuditSeverity.NOTICE, AuditOutcome.SUCCESS,
                        DEMO_PHYSICIAN, "Ricardo Salazar Peredo", List.of("PHYSICIAN"), "MEDICAL_RECORD",
                        UUID.fromString("40000000-0000-4000-8000-000000000001"), "Expediente clinico",
                        DEMO_PATIENT, "GET", "/api/v1/expedientes/40000000-0000-4000-8000-000000000001",
                        200, null, Map.of()),
                new SeedEvent(Duration.ofHours(4).plusMinutes(40), "ms-historial", "DIAGNOSIS_RECORDED",
                        AuditCategory.CLINICAL_RECORD, AuditSeverity.NOTICE, AuditOutcome.SUCCESS,
                        DEMO_PHYSICIAN, "Ricardo Salazar Peredo", List.of("PHYSICIAN"), "DIAGNOSIS",
                        UUID.fromString("40000000-0000-4000-8000-000000000002"), "Diagnostico de encuentro",
                        DEMO_PATIENT, "POST", "/api/v1/diagnosticos", 201, null,
                        Map.of("icd10Code", "J00")),
                new SeedEvent(Duration.ofHours(3), "ms-historial", "MEDICAL_RECORD_VIEWED",
                        AuditCategory.AUTHORIZATION, AuditSeverity.CRITICAL, AuditOutcome.DENIED,
                        DEMO_RECEPTIONIST, "Elena Cruz Apaza", List.of("RECEPTIONIST"), "MEDICAL_RECORD",
                        UUID.fromString("40000000-0000-4000-8000-000000000001"), "Expediente clinico",
                        DEMO_PATIENT, "GET", "/api/v1/expedientes/40000000-0000-4000-8000-000000000001",
                        403, "El rol RECEPTIONIST no puede leer expedientes", Map.of()),
                new SeedEvent(Duration.ofHours(2).plusMinutes(20), "ms-documental", "DOCUMENT_UPLOADED",
                        AuditCategory.DOCUMENT, AuditSeverity.INFO, AuditOutcome.SUCCESS,
                        DEMO_PHYSICIAN, "Ricardo Salazar Peredo", List.of("PHYSICIAN"), "DOCUMENT",
                        UUID.fromString("50000000-0000-4000-8000-000000000001"), "Resultado de laboratorio",
                        DEMO_PATIENT, "POST", "/api/v1/documents", 201, null,
                        Map.of("contentType", "application/pdf")),
                new SeedEvent(Duration.ofHours(1).plusMinutes(30), "ms-facturacion", "INVOICE_ISSUED",
                        AuditCategory.BILLING, AuditSeverity.INFO, AuditOutcome.SUCCESS,
                        DEMO_ADMIN, "Patricia Loayza Bernal", List.of("ADMINISTRATOR"), "INVOICE",
                        UUID.fromString("60000000-0000-4000-8000-000000000001"), "Factura demo 0001",
                        DEMO_PATIENT, "POST", "/api/v1/facturas", 201, null, Map.of()),
                new SeedEvent(Duration.ofMinutes(45), "ms-tenencia", "BRANCH_UPDATED",
                        AuditCategory.CONFIGURATION, AuditSeverity.NOTICE, AuditOutcome.SUCCESS,
                        DEMO_ADMIN, "Patricia Loayza Bernal", List.of("ADMINISTRATOR"), "BRANCH",
                        UUID.fromString("00000000-0000-4000-8000-0000000000a2"), "Sede Sopocachi", null,
                        "PUT", "/api/v1/tenancy/branches/00000000-0000-4000-8000-0000000000a2", 200, null,
                        Map.of("field", "phoneE164")));
    }

    private RecordAuditEventRequest shiftedTo(Instant now, SeedEvent event) {
        return new RecordAuditEventRequest(UUID.randomUUID(), DEMO_ORGANIZATION, now.minus(event.ago()),
                event.serviceName(), event.action(), event.category(), event.severity(), event.outcome(),
                event.actorUserId(), event.actorDisplayName(), event.actorRoles(), "10.0.0.10",
                event.resourceType(), event.resourcePublicId(), event.resourceLabel(),
                event.patientPublicId(), event.requestMethod(), event.requestPath(),
                event.responseStatus(), event.reason(), event.metadata());
    }

    /** Fila del guion; {@code ago} es la antiguedad respecto del arranque. */
    private record SeedEvent(Duration ago, String serviceName, String action, AuditCategory category,
            AuditSeverity severity, AuditOutcome outcome, UUID actorUserId, String actorDisplayName,
            List<String> actorRoles, String resourceType, UUID resourcePublicId, String resourceLabel,
            UUID patientPublicId, String requestMethod, String requestPath, Integer responseStatus,
            String reason, Map<String, String> metadata) {
    }
}
