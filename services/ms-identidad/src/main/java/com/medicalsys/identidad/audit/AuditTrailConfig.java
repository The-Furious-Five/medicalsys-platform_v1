package com.medicalsys.identidad.audit;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Cliente HTTP y pool dedicados al envio de auditoria. */
@Configuration
@EnableConfigurationProperties(AuditTrailProperties.class)
public class AuditTrailConfig {

    /**
     * Cliente hacia ms-auditoria con tiempos de espera explicitos.
     *
     * <p>Los timeouts no son opcionales aqui: sin ellos, una bitacora colgada dejaria hilos del
     * pool de auditoria bloqueados hasta agotar la cola, y la contrapresion terminaria alcanzando
     * al hilo que atiende el login o el ABM.
     */
    // RestClient.builder() y no un RestClient.Builder inyectado: en el contenedor (Spring Boot 4) no hay un
    // builder autoconfigurado y el servicio no arrancaba. Los tests no lo veian porque su classpath si lo trae.
    @Bean
    RestClient auditRestClient(AuditTrailProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.isEnabled() ? properties.baseUrl() : "http://localhost")
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Pool propio, pequeno y con cola acotada.
     *
     * <p>Separarlo del hilo de la solicitud evita que la latencia de la bitacora se sume a la de
     * un login o un ABM de usuario. La cola es acotada y la politica es {@code CallerRuns}: cuando
     * la bitacora se atrasa, el ultimo evento lo envia el hilo que lo produjo. Es una degradacion
     * visible en el tiempo de respuesta, preferible a descartar auditoria en silencio.
     */
    @Bean(destroyMethod = "shutdown")
    ExecutorService auditExecutor(AuditTrailProperties properties) {
        AtomicInteger counter = new AtomicInteger();
        return new ThreadPoolExecutor(1, 2, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(properties.queueCapacity()),
                runnable -> {
                    Thread thread = new Thread(runnable, "audit-trail-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
