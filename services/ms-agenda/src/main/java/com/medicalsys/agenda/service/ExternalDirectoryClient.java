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
 * estas llamadas.
 *
 * <p>Rutas consumidas, tomadas del codigo de cada servicio y no de la documentacion:
 * <pre>
 * GET /internal/v1/patients/{id}/scheduling-summary  (ms-pacientes, InternalPatientController)
 * GET /internal/v1/professionals/{id}                (ms-profesionales, ProfessionalController)
 * </pre>
 *
 * <p>La ruta de pacientes era {@code /internal/v1/patients/{id}} a secas, que ms-pacientes nunca
 * publico: con la validacion activada toda cita fallaba, y con ella apagada la agenda aceptaba
 * pacientes inexistentes. Por eso el contrato estaba desactivado y no porque los servicios no
 * estuvieran en la red.
 *
 * <p>Que ms-profesionales responda 404 a un profesional suspendido es intencional: su ruta interna
 * exige vinculo activo, de modo que no se puede citar a quien esta de licencia.
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
        require(patients, "/internal/v1/patients/" + patientPublicId + "/scheduling-summary",
                "paciente", patientPublicId);
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
