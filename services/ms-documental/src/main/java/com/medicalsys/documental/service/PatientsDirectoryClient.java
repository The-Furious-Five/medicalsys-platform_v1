package com.medicalsys.documental.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.medicalsys.documental.config.ConsentIntegrationProperties;
import com.medicalsys.documental.exception.ApiException;
import com.medicalsys.documental.exception.ErrorCode;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Trae el nombre y el WhatsApp del paciente desde ms-pacientes; ni ms-documental ni el evento
 * marketing.campana los guardan (mismo endpoint que ya usa ms-agenda para notificar una cita).
 */
@Component
public class PatientsDirectoryClient {

    private final RestClient client;

    public PatientsDirectoryClient(ConsentIntegrationProperties properties, RestClient.Builder builder) {
        this.client = builder.clone().baseUrl(properties.patientsBaseUrl()).build();
    }

    public PatientContact require(UUID patientPublicId) {
        try {
            PatientContact contact = client.get()
                    .uri("/internal/v1/patients/{id}/notification-contact", patientPublicId)
                    .retrieve()
                    .body(PatientContact.class);
            if (contact == null) {
                throw new ApiException(ErrorCode.PATIENT_CONTACT_UNAVAILABLE,
                        "ms-pacientes no devolvio datos de contacto del paciente " + patientPublicId);
            }
            return contact;
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ApiException(ErrorCode.PATIENT_CONTACT_UNAVAILABLE,
                    "No existe el paciente " + patientPublicId);
        } catch (ResourceAccessException exception) {
            throw new ApiException(ErrorCode.PATIENT_SERVICE_UNAVAILABLE,
                    "ms-pacientes no esta disponible: " + exception.getMessage());
        } catch (HttpClientErrorException exception) {
            throw new ApiException(ErrorCode.PATIENT_CONTACT_UNAVAILABLE,
                    "No fue posible obtener el contacto del paciente " + patientPublicId + ": "
                            + exception.getMessage());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PatientContact(UUID patientId, String givenNames, String fullName, String whatsappNumber) {
    }
}
