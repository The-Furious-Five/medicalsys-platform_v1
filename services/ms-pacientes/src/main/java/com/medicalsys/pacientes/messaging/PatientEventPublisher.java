package com.medicalsys.pacientes.messaging;

import com.medicalsys.pacientes.domain.enums.PatientStatus;
import com.medicalsys.pacientes.domain.model.Patient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Publica en {@code pacientes.paciente} cada vez que el paciente cambia, para que ms-notificaciones
 * (y cualquier otro consumidor futuro) tenga su propia copia de nombre/telefono/estado sin tener que
 * consultar a ms-pacientes por cada mensaje.
 *
 * <p>El paciente en si es global (sin organizacion propia: puede ser atendido por varias clinicas),
 * asi que el evento no puede llevar una organizacion "duena" del paciente. Se usa en su lugar la
 * organizacion de quien hizo el cambio ({@link com.medicalsys.pacientes.common.tenant.RequestContext}),
 * igual que ya hace {@link com.medicalsys.pacientes.common.audit.AuditTrail}: el hecho de que una
 * clinica edite o registre a un paciente es lo que lo hace "conocido" para ella. Sin identidad
 * propagada (contexto null) no hay organizacion que ponerle al evento, asi que no se publica nada.
 */
@Component
public class PatientEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PatientEventPublisher.class);
    private static final String TOPIC = "pacientes.paciente";

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;

    public PatientEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper objectMapper) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
    }

    public void publish(Patient patient, UUID organizationId) {
        if (organizationId == null) {
            log.debug("Paciente {} modificado sin identidad de organizacion propagada; no se publica en {}",
                    patient.getPublicId(), TOPIC);
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("patientId", patient.getPublicId().toString());
        payload.put("organizationId", organizationId.toString());
        payload.put("displayName", patient.getFullName());
        payload.put("whatsappNumber", patient.getPhoneE164());
        payload.put("active", patient.getStatus() == PatientStatus.ACTIVE);
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafka.send(TOPIC, patient.getPublicId().toString(), json);
        } catch (RuntimeException exception) {
            // Best-effort, igual que NotificationPreferenceEventPublisher: un fallo aqui no debe tumbar
            // el alta o la edicion del paciente ya guardada.
            log.error("No se pudo publicar el cambio del paciente {} en {}", patient.getPublicId(), TOPIC,
                    exception);
        }
    }
}
