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
 * Obtiene la prestacion consultando ms-tenencia.
 *
 * <p>Contrato consumido (ruta interna, congelada por ms-tenencia):
 * <pre>
 * GET /internal/v1/prestaciones/{serviceOfferingId}  -&gt; 200 con la prestacion, 404 si no existe
 * </pre>
 *
 * <p>Un 404 significa que la prestacion no existe en ese tenant y se traduce a {@link Optional#empty()};
 * una caida de la dependencia se traduce a 503.
 */
@Component
@ConditionalOnProperty(prefix = "medicalsys.tenancy", name = "mode", havingValue = "REMOTE")
public class RemoteServiceCatalogClient implements ServiceCatalogClient {

    private final RestClient restClient;

    public RemoteServiceCatalogClient(@Qualifier("tenancyRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Optional<ServiceOfferingRef> offering(UUID organizationId, UUID serviceOfferingId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/internal/v1/prestaciones/{serviceOfferingId}", serviceOfferingId)
                    .header(TenantContextFilter.ORGANIZATION_HEADER, organizationId.toString())
                    .retrieve()
                    .body(ServiceOfferingRef.class));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw new DependencyUnavailableException(
                    "ms-tenencia respondio " + e.getStatusCode() + " al pedir la prestacion", e);
        } catch (RestClientException e) {
            throw new DependencyUnavailableException(
                    "ms-tenencia no esta disponible para resolver la prestacion", e);
        }
    }
}
