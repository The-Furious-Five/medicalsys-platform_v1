package com.medicalsys.agenda.service;

import com.medicalsys.agenda.config.SchedulingProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

/**
 * Trae los datos de paciente y profesional que agenda no guarda (nombre, telefono, especialidad)
 * para armar la notificacion de WhatsApp de una cita creada o reagendada.
 *
 * <p>Es deliberadamente best-effort: si ms-pacientes o ms-profesionales no responden, se devuelve
 * {@link Optional#empty()} y quien llama decide seguir sin notificar en vez de romper la reserva
 * de la cita, que es la operacion principal.
 */
@Component
public class NotificationEnrichmentClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationEnrichmentClient.class);

    private final SchedulingProperties properties;
    private final RestClient patients;
    private final RestClient professionals;

    public NotificationEnrichmentClient(SchedulingProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.patients = builder.clone().baseUrl(properties.getValidation().getPatientsBaseUrl()).build();
        this.professionals = builder.clone().baseUrl(properties.getValidation().getProfessionalsBaseUrl()).build();
    }

    public Optional<PatientContact> fetchPatientContact(UUID patientPublicId) {
        return fetch(patients, "/internal/v1/patients/" + patientPublicId + "/notification-contact",
                PatientContact.class, "paciente", patientPublicId);
    }

    public Optional<ProfessionalContact> fetchProfessional(UUID healthProfessionalPublicId) {
        return fetch(professionals, "/internal/v1/professionals/" + healthProfessionalPublicId,
                ProfessionalContact.class, "profesional", healthProfessionalPublicId);
    }

    private <T> Optional<T> fetch(RestClient client, String path, Class<T> type, String what, UUID publicId) {
        try {
            return Optional.ofNullable(client.get().uri(path).retrieve().body(type));
        } catch (RestClientException exception) {
            log.warn("No se pudo enriquecer la notificacion con los datos del {} {}: {}",
                    what, publicId, exception.getMessage());
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PatientContact(UUID patientId, String givenNames, String fullName, String whatsappNumber) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProfessionalContact(UUID id, String fullName, List<Specialty> specialties) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Specialty(String code, boolean primary) {
        }
    }
}
