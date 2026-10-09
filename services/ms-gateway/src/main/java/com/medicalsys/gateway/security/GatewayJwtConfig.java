package com.medicalsys.gateway.security;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Decodifica los JWT firmados por ms-identidad usando el mismo secreto compartido (HS256).
 *
 * <p>El gateway no emite tokens, solo los valida para poder repropagar la identidad como
 * headers de confianza (X-User-Id, X-Organization-Id, X-Roles) hacia los servicios internos,
 * tal como documentan sus TenantContextFilter (ver ms-tenencia, ms-historial, ms-facturacion,
 * ms-pacientes, ms-documental). Claims acordadas con ms-identidad: sub, organizationId, roles.
 */
@Configuration
public class GatewayJwtConfig {

    @Bean
    public SecretKey jwtSigningKey(@Value("${JWT_SECRET}") String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 bytes para firmar con HS256");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
