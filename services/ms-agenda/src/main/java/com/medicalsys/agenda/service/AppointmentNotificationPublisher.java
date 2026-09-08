package com.medicalsys.agenda.service;

import com.medicalsys.agenda.config.SchedulingProperties;
import com.medicalsys.agenda.domain.Appointment;
import com.medicalsys.agenda.domain.PhysicalResource;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Publica en {@code agenda.cita} cuando se crea o reagenda una cita, para que ms-notificaciones
 * dispare la confirmacion por WhatsApp (y, para el mismo evento, el recordatorio de 25 horas antes
 * ya programado alla mediante sus propias reglas).
 *
 * <p>Es best-effort a proposito: nunca debe impedir que la cita se cree o reagende. Un fallo aqui
 * (Kafka caido, ms-pacientes/ms-profesionales sin responder) solo se registra en el log.
 */
@Component
public class AppointmentNotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(AppointmentNotificationPublisher.class);
    private static final String TOPIC = "agenda.cita";
    private static final String ENTITY_TYPE = "APPOINTMENT";
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", new Locale("es", "ES"));
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;
    private final NotificationEnrichmentClient directory;
    private final SchedulingProperties properties;
    private final Clock clock = Clock.systemUTC();

    public AppointmentNotificationPublisher(KafkaTemplate<String, String> kafka, ObjectMapper objectMapper,
            NotificationEnrichmentClient directory, SchedulingProperties properties) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.directory = directory;
        this.properties = properties;
    }

    public void publishCreated(Appointment appointment, PhysicalResource resource) {
        publish(appointment, resource, "APPOINTMENT_CREATED");
    }

    public void publishRescheduled(Appointment appointment, PhysicalResource resource) {
        publish(appointment, resource, "APPOINTMENT_RESCHEDULED");
    }

    private void publish(Appointment appointment, PhysicalResource resource, String eventType) {
        if (!properties.getNotifications().isEnabled()) {
            return;
        }
        try {
            Optional<NotificationEnrichmentClient.PatientContact> patient =
                    directory.fetchPatientContact(appointment.getPatientPublicId());
            if (patient.isEmpty() || isBlank(patient.get().whatsappNumber())) {
                log.info("Paciente {} sin telefono de contacto; no se notifica la cita {}",
                        appointment.getPatientPublicId(), appointment.getPublicId());
                return;
            }
            Optional<NotificationEnrichmentClient.ProfessionalContact> professional =
                    directory.fetchProfessional(appointment.getHealthProfessionalPublicId());

            Map<String, Object> variables = variables(appointment, patient.get(), professional.orElse(null), resource);
            String destination = patient.get().whatsappNumber();

            send(eventPayload(appointment, eventType, destination, variables, null));
            // ms-notificaciones trata todo LocalDateTime como reloj de pared en UTC (su propio Clock
            // es systemUTC); targetAt debe normalizarse a UTC aqui, no a la zona de negocio, o el
            // recordatorio de "25 horas antes" queda desfasado por el offset de America/La_Paz.
            send(eventPayload(appointment, "APPOINTMENT_REMINDER_DUE", destination, variables,
                    appointment.getStartsAt().atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()));
        } catch (RuntimeException exception) {
            log.error("No se pudo publicar la notificacion de la cita {}", appointment.getPublicId(), exception);
        }
    }

    private Map<String, Object> variables(Appointment appointment, NotificationEnrichmentClient.PatientContact patient,
            NotificationEnrichmentClient.ProfessionalContact professional, PhysicalResource resource) {
        var local = appointment.getStartsAt().atZoneSameInstant(properties.getZone());
        String time = local.format(TIME_FORMAT);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("patientName", patient.givenNames());
        variables.put("doctorName", professional == null ? "" : professional.fullName());
        variables.put("specialty", professional == null ? "" : humanizeSpecialty(professional));
        variables.put("date", local.format(DATE_FORMAT));
        variables.put("time", time);
        variables.put("office", resource == null ? "por confirmar" : resource.getName());
        variables.put("floor", resource == null || isBlank(resource.getFloor()) ? "-" : resource.getFloor());
        // Solo la usa la plantilla de recordatorio: la hora de corte para reagendar es la misma
        // hora de la cita, 24h antes.
        variables.put("cutoffTime", time);
        return variables;
    }

    private String humanizeSpecialty(NotificationEnrichmentClient.ProfessionalContact professional) {
        return professional.specialties().stream()
                .filter(NotificationEnrichmentClient.ProfessionalContact.Specialty::primary)
                .findFirst()
                .or(() -> professional.specialties().stream().findFirst())
                .map(specialty -> humanize(specialty.code()))
                .orElse("");
    }

    private String humanize(String code) {
        String[] words = code.toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
            }
        }
        return result.toString().trim();
    }

    private Map<String, Object> eventPayload(Appointment appointment, String eventType, String destination,
            Map<String, Object> variables, LocalDateTime targetAt) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("organizationId", appointment.getOrganizationPublicId().toString());
        event.put("patientId", appointment.getPatientPublicId().toString());
        event.put("eventType", eventType);
        event.put("entityType", ENTITY_TYPE);
        event.put("entityId", appointment.getPublicId().toString());
        event.put("destination", destination);
        event.put("variables", variables);
        event.put("occurredAt", LocalDateTime.now(clock).toString());
        event.put("targetAt", targetAt == null ? null : targetAt.toString());
        return event;
    }

    private void send(Map<String, Object> event) {
        String json = objectMapper.writeValueAsString(event);
        kafka.send(TOPIC, (String) event.get("patientId"), json);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
