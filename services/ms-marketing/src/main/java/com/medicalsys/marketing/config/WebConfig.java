package com.medicalsys.marketing.config;

import com.medicalsys.marketing.common.security.RoleAuthorizationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registra la autorizacion por rol para las rutas del contrato. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RoleAuthorizationInterceptor roleAuthorizationInterceptor;

    public WebConfig(RoleAuthorizationInterceptor roleAuthorizationInterceptor) {
        this.roleAuthorizationInterceptor = roleAuthorizationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roleAuthorizationInterceptor)
                .addPathPatterns("/api/**", "/internal/**");
    }
}
