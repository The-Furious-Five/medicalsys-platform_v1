package com.medicalsys.marketing.config;

import com.medicalsys.marketing.common.correlation.CorrelationId;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Clientes HTTP hacia las dependencias sincronas de ms-marketing.
 *
 * <p>Son dos, y la lista completa importa tanto como su contenido: <b>ms-pacientes</b> y
 * <b>ms-tenencia</b>. No hay un tercer cliente hacia ms-historial y no existe propiedad con la que
 * crearlo. Un revisor que quiera comprobar que marketing no puede leer el expediente puede empezar y
 * terminar en este archivo. {@code MarketingIsolationTest} lo verifica automaticamente.
 *
 * <p>Cada cliente lleva timeout de conexion y de lectura: una espera infinita convierte la caida de
 * un servicio en un bloqueo en cascada, y este servicio -- el de menor criticidad del sistema -- es
 * justamente el que nunca debe arrastrar a los demas. Ademas propaga el correlationId para que un
 * fallo pueda reconstruirse a traves de los saltos.
 */
@Configuration
public class RestClientConfig {

    /** Cliente hacia ms-pacientes: demografia replicada. */
    @Bean
    RestClient patientsRestClient(RestClient.Builder builder, MarketingProperties properties) {
        MarketingProperties.Patients patients = properties.patients();
        return build(builder, patients.baseUrl(), patients.connectTimeout(), patients.readTimeout());
    }

    /** Cliente hacia ms-tenencia: catalogo de prestaciones. */
    @Bean
    RestClient tenancyRestClient(RestClient.Builder builder, MarketingProperties properties) {
        MarketingProperties.Tenancy tenancy = properties.tenancy();
        return build(builder, tenancy.baseUrl(), tenancy.connectTimeout(), tenancy.readTimeout());
    }

    private RestClient build(RestClient.Builder builder, String baseUrl, Duration connectTimeout,
            Duration readTimeout) {
        return builder.clone()
                .baseUrl(baseUrl)
                .defaultHeader("X-Requested-By", "ms-marketing")
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
