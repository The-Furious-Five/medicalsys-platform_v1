package com.medicalsys.facturacion.config;

import com.medicalsys.facturacion.common.correlation.CorrelationId;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Clientes HTTP hacia las dependencias sincronas del MVP.
 *
 * <p>Cada cliente lleva timeout de conexion y de lectura: una espera infinita convierte la caida de
 * un servicio en un bloqueo en cascada. Ademas propaga el correlationId para que un fallo pueda
 * reconstruirse a traves de los saltos.
 *
 * <p>No hay ningun cliente hacia el sistema fiscal: el adaptador del sprint es un stub sin red.
 */
@Configuration
public class RestClientConfig {

    /** Cliente hacia ms-pacientes. */
    @Bean
    RestClient patientsRestClient(RestClient.Builder builder, BillingProperties properties) {
        BillingProperties.Patients patients = properties.patients();
        return build(builder, patients.baseUrl(), patients.connectTimeout(), patients.readTimeout());
    }

    /** Cliente hacia ms-tenencia. */
    @Bean
    RestClient tenancyRestClient(RestClient.Builder builder, BillingProperties properties) {
        BillingProperties.Tenancy tenancy = properties.tenancy();
        return build(builder, tenancy.baseUrl(), tenancy.connectTimeout(), tenancy.readTimeout());
    }

    private RestClient build(RestClient.Builder builder, String baseUrl, Duration connectTimeout,
            Duration readTimeout) {
        return builder.clone()
                .baseUrl(baseUrl)
                .defaultHeader("X-Requested-By", "ms-facturacion")
                .requestFactory(requestFactory(connectTimeout, readTimeout))
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().set(CorrelationId.HEADER, CorrelationId.current());
                    return execution.execute(request, body);
                })
                .build();
    }

    private ClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
