package com.medicalsys.identidad.config;

import com.medicalsys.identidad.audit.AuditEventDraft;
import com.medicalsys.identidad.audit.AuditTrail;
import com.medicalsys.identidad.security.JwtAuthenticationFilter;
import com.medicalsys.identidad.security.SecurityProblemWriter;
import com.medicalsys.identidad.security.SessionStore;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(IdentityProperties.class)
public class SecurityConfig {

    private final IdentityProperties properties;

    public SecurityConfig(IdentityProperties properties) {
        this.properties = properties;
    }

    /** Argon2id, tal como exige el diccionario de datos para user_account.password_hash. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    public SecretKey jwtSigningKey() {
        String secret = properties.getJwt().getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET debe tener al menos 32 bytes para firmar con HS256");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey key) {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder, SessionStore sessionStore,
                                           SecurityProblemWriter problemWriter, AuditTrail auditTrail)
            throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtDecoder, sessionStore);
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**", "/error").permitAll()
                        .requestMatchers("/api/v1/identity/ping").permitAll()
                        .requestMatchers("/api/v1/identity/auth/login").permitAll()
                        // IAM-05: sin sesion para la contrasena temporal o vencida; la actual la valida
                        // Keycloak, que cuenta los fallos para el bloqueo al 3er intento.
                        .requestMatchers(HttpMethod.POST, "/api/v1/identity/auth/change-password").permitAll()
                        // OWASP: ningun servicio consume las rutas internas de identidad.
                        .requestMatchers("/internal/v1/**").denyAll()
                        // Autoservicio de la propia sesion: lo necesita cualquier cuenta, incluido PATIENT.
                        .requestMatchers(HttpMethod.GET, "/api/v1/identity/auth/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/identity/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/identity/users/*/roles").hasAuthority("identity.role.assign")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/identity/users/*/roles/*").hasAuthority("identity.role.assign")
                        .requestMatchers(HttpMethod.GET, "/api/v1/identity/users/**").hasAuthority("identity.user.read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/identity/users").hasAuthority("identity.user.create")
                        .requestMatchers(HttpMethod.POST, "/api/v1/identity/users/*/unlock").hasAuthority("identity.user.unlock")
                        .requestMatchers(HttpMethod.POST, "/api/v1/identity/users/*/reset-password").hasAuthority("identity.user.reset")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/identity/users/*/status").hasAuthority("identity.user.change_status")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/identity/users/*").hasAuthority("identity.user.update")
                        // Una operacion nueva sobre cuentas no queda abierta por omision: necesita su propio permiso.
                        .requestMatchers("/api/v1/identity/users/**").denyAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/identity/roles/**", "/api/v1/identity/permissions",
                                "/api/v1/identity/role-incompatibilities").hasAuthority("identity.role.read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/identity/roles",
                                "/api/v1/identity/role-incompatibilities").hasAuthority("identity.role.manage")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/identity/roles/*").hasAuthority("identity.role.manage")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/identity/roles/*/permissions",
                                "/api/v1/identity/roles/*/assignable-roles").hasAuthority("identity.role.manage")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/identity/roles/*",
                                "/api/v1/identity/role-incompatibilities/*/*").hasAuthority("identity.role.manage")
                        // Igual que con las cuentas: una operacion nueva sobre roles necesita su propio permiso.
                        .requestMatchers("/api/v1/identity/roles/**", "/api/v1/identity/role-incompatibilities/**")
                                .denyAll()
                        // Denegar por defecto (RBAC-04): lo que no figura arriba no se atiende, ni siquiera con sesion.
                        .anyRequest().denyAll())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> problemWriter.write(
                                request, response, HttpStatus.UNAUTHORIZED, "Credenciales invalidas",
                                "Se requiere un token de acceso valido"))
                        .accessDeniedHandler((request, response, deniedException) -> {
                            recordAccessDenied(auditTrail, request.getRequestURI());
                            problemWriter.write(request, response, HttpStatus.FORBIDDEN, "Acceso denegado",
                                    "El token no tiene el permiso requerido para esta operacion");
                        }));
        return http.build();
    }

    /** El filtro de autorizacion ya rechazo la solicitud; aqui solo se deja constancia del hecho. */
    private void recordAccessDenied(AuditTrail auditTrail, String requestPath) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID actorId = null;
        List<String> actorRoles = List.of();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            actorId = UUID.fromString(jwt.getSubject());
            List<String> permissions = jwt.getClaimAsStringList("roles");
            actorRoles = permissions == null ? List.of() : permissions;
        }
        auditTrail.record(AuditEventDraft.accessDenied(actorId, actorRoles, requestPath,
                "Permiso insuficiente para " + requestPath));
    }
}
