package com.medicalsys.facturacion.client;

import com.medicalsys.facturacion.common.error.BusinessRuleException;
import com.medicalsys.facturacion.common.error.DependencyUnavailableException;
import com.medicalsys.facturacion.common.error.ErrorCode;
import com.medicalsys.facturacion.common.tenant.TenantContextFilter;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Obtiene el DTO fiscal consultando ms-pacientes.
 *
 * <p>Contrato consumido (ruta interna):
 * <pre>
 * GET /internal/v1/pacientes/{patientId}/fiscal   -> 200 con el DTO fiscal, 404 si no existe
 * </pre>
 *
 * <p>Un 404 es una respuesta de contrato y se traduce a 422; una caida de la dependencia se traduce
 * a 503 y nunca se confunde con un paciente inexistente.
 */
@Component
@ConditionalOnProperty(prefix = "medicalsys.patients", name = "mode", havingValue = "REMOTE")
public class RemotePatientsClient implements PatientsClient {

    private final RestClient restClient;

    public RemotePatientsClient(@Qualifier("patientsRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public FiscalCustomer fiscalCustomer(UUID organizationId, UUID patientId) {
        try {
            FiscalCustomer customer = restClient.get()
                    .uri("/internal/v1/pacientes/{patientId}/fiscal", patientId)
                    .header(TenantContextFilter.ORGANIZATION_HEADER, organizationId.toString())
                    .retrieve()
                    .body(FiscalCustomer.class);
            if (customer == null) {
                throw notBillable(patientId, "no devolvio datos fiscales");
            }
            return customer;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw notBillable(patientId, "no existe o no pertenece a esta organizacion");
            }
            throw new DependencyUnavailableException(
                    "ms-pacientes respondio " + e.getStatusCode() + " al pedir el DTO fiscal", e);
        } catch (RestClientException e) {
            throw new DependencyUnavailableException(
                    "ms-pacientes no esta disponible para resolver el cliente de la factura", e);
        }
    }

    private BusinessRuleException notBillable(UUID patientId, String reason) {
        return new BusinessRuleException(ErrorCode.PATIENT_NOT_BILLABLE,
                "El paciente '" + patientId + "' " + reason);
    }
}
