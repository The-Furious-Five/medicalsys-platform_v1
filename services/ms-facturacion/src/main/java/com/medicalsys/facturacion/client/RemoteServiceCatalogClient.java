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
 * Obtiene la prestacion y su precio consultando ms-tenencia.
 *
 * <p>Contrato consumido (ruta interna):
 * <pre>
 * GET /internal/v1/prestaciones/{serviceOfferingId}  -> 200 con la prestacion, 404 si no existe
 * </pre>
 *
 * <p>Un 404 es una respuesta de contrato y se traduce a 422; una caida de la dependencia se traduce
 * a 503 y nunca se confunde con una prestacion inexistente.
 */
@Component
@ConditionalOnProperty(prefix = "medicalsys.tenancy", name = "mode", havingValue = "REMOTE")
public class RemoteServiceCatalogClient implements ServiceCatalogClient {

    private final RestClient restClient;

    public RemoteServiceCatalogClient(@Qualifier("tenancyRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public BillableService billableService(UUID organizationId, UUID serviceOfferingId) {
        BillableService offering;
        try {
            offering = restClient.get()
                    .uri("/internal/v1/prestaciones/{serviceOfferingId}", serviceOfferingId)
                    .header(TenantContextFilter.ORGANIZATION_HEADER, organizationId.toString())
                    .retrieve()
                    .body(BillableService.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw notBillable(serviceOfferingId, "no existe en el catalogo de esta organizacion");
            }
            throw new DependencyUnavailableException(
                    "ms-tenencia respondio " + e.getStatusCode() + " al pedir una prestacion", e);
        } catch (RestClientException e) {
            throw new DependencyUnavailableException(
                    "ms-tenencia no esta disponible para resolver las prestaciones a facturar", e);
        }
        if (offering == null) {
            throw notBillable(serviceOfferingId, "no devolvio datos de la prestacion");
        }
        if (!offering.active()) {
            throw notBillable(serviceOfferingId, "esta desactivada y no puede facturarse");
        }
        return offering;
    }

    private BusinessRuleException notBillable(UUID serviceOfferingId, String reason) {
        return new BusinessRuleException(ErrorCode.SERVICE_OFFERING_NOT_BILLABLE,
                "La prestacion '" + serviceOfferingId + "' " + reason);
    }
}
