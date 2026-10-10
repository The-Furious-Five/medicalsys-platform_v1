package com.medicalsys.auditoria.config;

import com.medicalsys.auditoria.common.security.PermissionAuthorizationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registra la autorizacion por permiso para las rutas del contrato. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final PermissionAuthorizationInterceptor permissionAuthorizationInterceptor;

    public WebConfig(PermissionAuthorizationInterceptor permissionAuthorizationInterceptor) {
        this.permissionAuthorizationInterceptor = permissionAuthorizationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(permissionAuthorizationInterceptor)
                .addPathPatterns("/api/**", "/internal/**");
    }
}
