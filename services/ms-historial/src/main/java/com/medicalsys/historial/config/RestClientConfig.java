package com.medicalsys.historial.config;

import com.medicalsys.historial.common.correlation.CorrelationId;
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
 */
@Configuration
public class RestClientConfig {

    /** Cliente hacia ms-criptografia. */
    @Bean
    RestClient cryptoRestClient(RestClient.Builder builder, HistorialProperties properties) {
        HistorialProperties.Crypto crypto = properties.crypto();
        return build(builder, crypto.baseUrl(), crypto.connectTimeout(), crypto.readTimeout());
    }

    /** Cliente hacia ms-catalogos. */
    @Bean
    RestClient catalogsRestClient(RestClient.Builder builder, HistorialProperties properties) {
        HistorialProperties.Catalogs catalogs = properties.catalogs();
        return build(builder, catalogs.baseUrl(), catalogs.connectTimeout(), catalogs.readTimeout());
    }

    private RestClient build(RestClient.Builder builder, String baseUrl, Duration connectTimeout,
            Duration readTimeout) {
        return builder.clone()
                .baseUrl(baseUrl)
                .defaultHeader("X-Requested-By", "ms-historial")
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
