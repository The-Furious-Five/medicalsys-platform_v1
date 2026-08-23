package com.medicalsys.identidad.security;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.medicalsys.identidad.service.ApiException;

/**
 * Las sesiones viven en Redis, no en una tabla relacional. Guardamos el jti del token
 * emitido; cerrar sesion borra la clave y el token deja de ser aceptado antes de expirar.
 */
@Component
public class SessionStore {

    private static final String SESSION_PREFIX = "identity:session:";
    private static final String USER_INDEX_PREFIX = "identity:user-sessions:";

    private final StringRedisTemplate redis;

    public SessionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void open(String jti, UUID userPublicId, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            ttl = Duration.ofSeconds(1);
        }
        try {
            redis.opsForValue().set(SESSION_PREFIX + jti, userPublicId.toString(), ttl);
            String index = USER_INDEX_PREFIX + userPublicId;
            redis.opsForSet().add(index, jti);
            redis.expire(index, ttl.plusHours(1));
        } catch (RedisConnectionFailureException ex) {
            throw unavailable(ex);
        }
    }

    public boolean isActive(String jti) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(SESSION_PREFIX + jti));
        } catch (RedisConnectionFailureException ex) {
            throw unavailable(ex);
        }
    }

    public void close(String jti, UUID userPublicId) {
        try {
            redis.delete(SESSION_PREFIX + jti);
            redis.opsForSet().remove(USER_INDEX_PREFIX + userPublicId, jti);
        } catch (RedisConnectionFailureException ex) {
            throw unavailable(ex);
        }
    }

    public long closeAll(UUID userPublicId) {
        try {
            String index = USER_INDEX_PREFIX + userPublicId;
            var members = redis.opsForSet().members(index);
            if (members == null || members.isEmpty()) {
                return 0;
            }
            long closed = 0;
            for (String jti : members) {
                closed += Boolean.TRUE.equals(redis.delete(SESSION_PREFIX + jti)) ? 1 : 0;
            }
            redis.delete(index);
            return closed;
        } catch (RedisConnectionFailureException ex) {
            throw unavailable(ex);
        }
    }

    private ApiException unavailable(RuntimeException cause) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Almacen de sesiones no disponible",
                "No fue posible contactar Redis: " + cause.getMessage());
    }
}
