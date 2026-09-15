package com.medicalsys.criptografia.common;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Todo lo que expone este servicio bajo {@code /internal/**} es una ruta entre microservicios (no
 * hay ninguna ruta publica de negocio en ms-criptografia); el interceptor de identidad de servicio
 * se aplica ahi. Se excluye {@code /actuator/**} porque el healthcheck de Docker lo golpea sin
 * cabeceras propias.
 */
@Configuration
@EnableConfigurationProperties(ServiceAuthProperties.class)
public class WebMvcConfig implements WebMvcConfigurer {

    private final ServiceAuthInterceptor serviceAuthInterceptor;

    public WebMvcConfig(ServiceAuthInterceptor serviceAuthInterceptor) {
        this.serviceAuthInterceptor = serviceAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(serviceAuthInterceptor).excludePathPatterns("/actuator/**");
    }
}
