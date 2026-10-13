package com.medicalsys.criptografia.common;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Todo lo que expone este servicio bajo {@code /internal/**} es una ruta entre microservicios (no
 * hay ninguna ruta publica de negocio en ms-criptografia); el interceptor de identidad de servicio
 * se aplica ahi. Se excluye {@code /actuator/**} porque el healthcheck de Docker lo golpea sin
 * cabeceras propias. Ademas, un endpoint que no se declare interno o publico se deniega por defecto
 * (RBAC-04).
 */
@Configuration
@EnableConfigurationProperties(ServiceAuthProperties.class)
public class WebMvcConfig implements WebMvcConfigurer {

    private final ServiceAuthInterceptor serviceAuthInterceptor;
    private final EndpointPolicyInterceptor endpointPolicyInterceptor;

    public WebMvcConfig(ServiceAuthInterceptor serviceAuthInterceptor,
            EndpointPolicyInterceptor endpointPolicyInterceptor) {
        this.serviceAuthInterceptor = serviceAuthInterceptor;
        this.endpointPolicyInterceptor = endpointPolicyInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(serviceAuthInterceptor).excludePathPatterns("/actuator/**");
        // Despues de verificar al servicio que llama, se deniega todo endpoint no declarado (RBAC-04).
        registry.addInterceptor(endpointPolicyInterceptor).excludePathPatterns("/error", "/actuator/**");
    }
}
