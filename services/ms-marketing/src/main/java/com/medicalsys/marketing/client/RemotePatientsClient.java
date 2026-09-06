package com.medicalsys.marketing.client;

import com.medicalsys.marketing.common.error.DependencyUnavailableException;
import com.medicalsys.marketing.common.tenant.TenantContextFilter;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Obtiene la demografia consultando ms-pacientes.
 *
 * <p>Contrato consumido (ruta interna):
 * <pre>
 * GET /internal/v1/patients/{patientId}   -&gt; 200 con la ficha, 404 si no existe
 * </pre>
 *
 * <p>La respuesta se vincula a {@link PatientDemographics}, que descarta todo lo que no sea
 * demografia: aunque ms-pacientes devuelva la ficha completa, aqui no entra nada mas.
 *
 * <p>Un 404 se traduce a {@link Optional#empty()} porque es una respuesta valida del contrato; una
 * caida de la dependencia se traduce a 503 y nunca se confunde con un paciente inexistente.
 */
@Component
@ConditionalOnProperty(prefix = "medicalsys.patients", name = "mode", havingValue = "REMOTE")
public class RemotePatientsClient implements PatientsClient {

    private final RestClient restClient;

    public RemotePatientsClient(@Qualifier("patientsRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Optional<PatientDemographics> demographics(UUID organizationId, UUID patientId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/internal/v1/patients/{patientId}", patientId)
                    .header(TenantContextFilter.ORGANIZATION_HEADER, organizationId.toString())
                    .retrieve()
                    .body(PatientDemographics.class));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw new DependencyUnavailableException(
                    "ms-pacientes respondio " + e.getStatusCode() + " al pedir la demografia del paciente", e);
        } catch (RestClientException e) {
            throw new DependencyUnavailableException(
                    "ms-pacientes no esta disponible para resolver la demografia del paciente", e);
        }
    }
}
