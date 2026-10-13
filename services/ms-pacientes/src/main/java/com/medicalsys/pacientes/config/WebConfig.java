package com.medicalsys.pacientes.config;

import com.medicalsys.pacientes.common.security.PermissionAuthorizationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registra la autorizacion por permiso, que deniega por defecto (RBAC-04). */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final PermissionAuthorizationInterceptor permissionAuthorizationInterceptor;

    public WebConfig(PermissionAuthorizationInterceptor permissionAuthorizationInterceptor) {
        this.permissionAuthorizationInterceptor = permissionAuthorizationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Todas las rutas: un controlador nuevo fuera de /api o /internal tampoco queda abierto. /error
        // y /actuator no son del contrato y los atiende Spring.
        registry.addInterceptor(permissionAuthorizationInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/error", "/actuator/**");
    }
}
