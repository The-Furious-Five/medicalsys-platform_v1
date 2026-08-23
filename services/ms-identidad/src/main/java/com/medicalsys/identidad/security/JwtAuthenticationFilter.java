package com.medicalsys.identidad.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Valida el JWT firmado por este mismo servicio y comprueba que la sesion siga viva en Redis.
 * No usamos el DSL de resource-server para mantener una sola ruta de validacion, compartida
 * mas adelante con el gateway a traves del mismo secreto e issuer.
 *
 * <p>No se declara como @Component a proposito: Boot registraria el filtro tambien en el
 * contenedor de servlets y, al extender OncePerRequestFilter, la ejecucion dentro de la
 * cadena de seguridad quedaria omitida. SecurityConfig lo instancia y lo inserta.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtDecoder decoder;
    private final SessionStore sessionStore;

    public JwtAuthenticationFilter(JwtDecoder decoder, SessionStore sessionStore) {
        this.decoder = decoder;
        this.sessionStore = sessionStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7).trim();
        try {
            Jwt jwt = decoder.decode(token);
            String jti = jwt.getId();
            if (jti == null || !sessionStore.isActive(jti)) {
                SecurityContextHolder.clearContext();
                chain.doFilter(request, response);
                return;
            }
            var authentication = new UsernamePasswordAuthenticationToken(jwt, token, authorities(jwt));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException ex) {
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }

    private List<GrantedAuthority> authorities(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null) {
            roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        }
        List<String> permissions = jwt.getClaimAsStringList("permissions");
        if (permissions != null) {
            permissions.forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        }
        return authorities;
    }
}
