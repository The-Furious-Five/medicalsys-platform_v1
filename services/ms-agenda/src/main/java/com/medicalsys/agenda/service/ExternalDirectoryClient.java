package com.medicalsys.agenda.service;

import com.medicalsys.agenda.config.SchedulingProperties;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Valida por contrato HTTP las referencias externas de una cita antes de crearla.
 *
 * <p>Agenda no tiene FK hacia patients_db ni professionals_db: guarda el UUID publico y confia en
 * estas llamadas. Queda desactivado por configuracion hasta que ms-pacientes y ms-profesionales
 * esten publicados en la red Compose.
 */
@Component
public class ExternalDirectoryClient {

    private static final Logger log = LoggerFactory.getLogger(ExternalDirectoryClient.class);

    private final SchedulingProperties properties;
    private final RestClient patients;
    private final RestClient professionals;

    public ExternalDirectoryClient(SchedulingProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.patients = builder.clone().baseUrl(properties.getValidation().getPatientsBaseUrl()).build();
        this.professionals = builder.clone().baseUrl(properties.getValidation().getProfessionalsBaseUrl()).build();
    }

    public void requirePatient(UUID patientPublicId) {
        require(patients, "/internal/v1/patients/" + patientPublicId, "paciente", patientPublicId);
    }

    public void requireProfessional(UUID healthProfessionalPublicId) {
        require(professionals, "/internal/v1/professionals/" + healthProfessionalPublicId,
                "profesional", healthProfessionalPublicId);
    }

    private void require(RestClient client, String path, String what, UUID publicId) {
        if (!properties.getValidation().isEnabled()) {
            log.debug("Validacion externa desactivada; se acepta el {} {} sin comprobar", what, publicId);
            return;
        }
        try {
            client.get().uri(path).retrieve().toBodilessEntity();
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound ex) {
            throw ApiException.unprocessable("No existe el " + what + " " + publicId);
        } catch (ResourceAccessException | org.springframework.web.client.HttpServerErrorException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Servicio dependiente no disponible",
                    "No fue posible validar el " + what + " " + publicId + ": " + ex.getMessage());
        }
    }
}
