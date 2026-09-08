package com.medicalsys.pacientes.messaging;

import com.medicalsys.pacientes.domain.model.NotificationPreference;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Publica en {@code pacientes.preferencia} cada vez que cambia un consentimiento de contacto.
 *
 * <p>ms-notificaciones consume este topico para su propia tabla de consentimiento y es la unica
 * fuente que autoriza el envio de una notificacion por un canal y categoria dados. Sin este
 * publicador, cualquier notificacion (incluida la de una cita agendada) queda siempre rechazada
 * por falta de consentimiento sincronizado.
 *
 * <p>La categoria se traduce porque los dos servicios versionan el consentimiento con nombres
 * distintos: CLINICAL (esta app) es equivalente a UTILITY (ms-notificaciones), y ADMINISTRATIVE se
 * traduce a SERVICE. Ninguna de las dos es MARKETING, que es la unica distincion real que hace el
 * gate de consentimiento del otro lado.
 */
@Component
public class NotificationPreferenceEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPreferenceEventPublisher.class);
    private static final String TOPIC = "pacientes.preferencia";

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;

    public NotificationPreferenceEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper objectMapper) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
    }

    public void publish(NotificationPreference preference) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("patientId", preference.getPatient().getPublicId().toString());
        payload.put("channel", preference.getChannel().name());
        payload.put("category", translateCategory(preference.getCategory()));
        payload.put("optedIn", preference.isOptIn());
        payload.put("optedInAt", toLocalDateTime(preference.getOptInAt()));
        payload.put("optedOutAt", toLocalDateTime(preference.getOptOutAt()));
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafka.send(TOPIC, preference.getPatient().getPublicId().toString(), json);
        } catch (RuntimeException exception) {
            // Un fallo aqui no debe tumbar el cambio de consentimiento ya guardado; ms-notificaciones
            // simplemente seguira rechazando el canal hasta que la sincronizacion se reintente.
            log.error("No se pudo publicar el cambio de preferencia del paciente {} en {}",
                    preference.getPatient().getPublicId(), TOPIC, exception);
        }
    }

    private String translateCategory(com.medicalsys.pacientes.domain.enums.NotificationCategory category) {
        return category == com.medicalsys.pacientes.domain.enums.NotificationCategory.CLINICAL
                ? "UTILITY" : "SERVICE";
    }

    private String toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC).toString();
    }
}
