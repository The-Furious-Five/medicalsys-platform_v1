package com.medicalsys.identidad.config;

import com.medicalsys.identidad.security.JwtAuthenticationFilter;
import com.medicalsys.identidad.security.SecurityProblemWriter;
import com.medicalsys.identidad.security.SessionStore;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
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
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
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
                                           SecurityProblemWriter problemWriter) throws Exception {
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
                        .requestMatchers("/internal/v1/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/identity/users/*/roles").hasAuthority("identity.role.assign")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/identity/users/*/roles/*").hasAuthority("identity.role.assign")
                        .requestMatchers(HttpMethod.GET, "/api/v1/identity/users/**").hasAuthority("identity.user.read")
                        .requestMatchers("/api/v1/identity/users/**").hasAuthority("identity.user.write")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> problemWriter.write(
                                request, response, HttpStatus.UNAUTHORIZED, "Credenciales invalidas",
                                "Se requiere un token de acceso valido"))
                        .accessDeniedHandler((request, response, deniedException) -> problemWriter.write(
                                request, response, HttpStatus.FORBIDDEN, "Acceso denegado",
                                "El token no tiene el permiso requerido para esta operacion")));
        return http.build();
    }
}
