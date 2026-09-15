package com.medicalsys.historial.client;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Consulta a ms-pacientes si una organización distinta a la que abrió un expediente tiene permiso
 * (autorizado por el paciente) para leerlo. Best-effort a propósito del lado de la falla: si
 * ms-pacientes no responde, se trata como "sin permiso" — un expediente clínico no debe volverse
 * legible entre tenants por una caída de red, al revés de como se trata, por ejemplo, la validación
 * de un cupón de marketing.
 */
@Component
public class PatientAccessClient {

    private final RestClient restClient;

    public PatientAccessClient(@Qualifier("patientsRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public boolean hasAccess(UUID patientId, UUID organizationId) {
        try {
            AccessGrantCheckResponse response = restClient.get()
                    .uri("/internal/v1/patients/{patientId}/access-grants/{organizationId}", patientId, organizationId)
                    .retrieve()
                    .body(AccessGrantCheckResponse.class);
            return response != null && response.granted();
        } catch (RestClientException exception) {
            return false;
        }
    }

    private record AccessGrantCheckResponse(boolean granted) {
    }
}
