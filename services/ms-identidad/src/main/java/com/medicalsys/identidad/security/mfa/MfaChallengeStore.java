package com.medicalsys.identidad.security.mfa;

import com.medicalsys.identidad.config.IdentityProperties;
import com.medicalsys.identidad.service.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Desafíos del segundo factor en Redis (IAM-07). Tras una contraseña válida, el login entrega un
 * token opaco de un solo uso que vale 5 minutos y solo sirve para el paso del código: no es una
 * sesión ni abre ninguna ruta. En Redis se guarda su SHA-256, no el token.
 *
 * <p>Cada desafío admite pocos intentos; además, los códigos fallidos de una cuenta se cuentan en una
 * ventana de 15 minutos para bloquearla aunque se pidan desafíos nuevos con la contraseña correcta.
 */
@Component
public class MfaChallengeStore {

    public enum Purpose { ENROLL, VERIFY }

    public record Challenge(String key, UUID userPublicId, Purpose purpose, int attempts) { }

    private static final String CHALLENGE_PREFIX = "identity:mfa:challenge:";
    private static final String FAILURES_PREFIX = "identity:mfa:failures:";

    private final StringRedisTemplate redis;
    private final IdentityProperties.Mfa config;
    private final SecureRandom random = new SecureRandom();

    public MfaChallengeStore(StringRedisTemplate redis, IdentityProperties properties) {
        this.redis = redis;
        this.config = properties.getMfa();
    }

    /** Token para el cliente; solo su huella queda en Redis. */
    public String open(UUID userPublicId, Purpose purpose) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        String key = CHALLENGE_PREFIX + fingerprint(token);
        try {
            redis.opsForHash().putAll(key, Map.of("user", userPublicId.toString(), "purpose", purpose.name(),
                    "attempts", "0"));
            redis.expire(key, config.getChallengeTtl());
        } catch (DataAccessException ex) {
            throw unavailable();
        }
        return token;
    }

    public Optional<Challenge> find(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String key = CHALLENGE_PREFIX + fingerprint(token);
        try {
            Map<Object, Object> values = redis.opsForHash().entries(key);
            if (values.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Challenge(key, UUID.fromString((String) values.get("user")),
                    Purpose.valueOf((String) values.get("purpose")),
                    Integer.parseInt((String) values.getOrDefault("attempts", "0"))));
        } catch (DataAccessException ex) {
            throw unavailable();
        }
    }

    /** Suma un intento fallido; true si el desafío se agotó (y ya se borró). */
    public boolean registerFailedAttempt(Challenge challenge) {
        try {
            Long attempts = redis.opsForHash().increment(challenge.key(), "attempts", 1);
            if (attempts != null && attempts >= config.getMaxAttemptsPerChallenge()) {
                redis.delete(challenge.key());
                return true;
            }
            return false;
        } catch (DataAccessException ex) {
            throw unavailable();
        }
    }

    public void close(Challenge challenge) {
        try {
            redis.delete(challenge.key());
        } catch (DataAccessException ex) {
            throw unavailable();
        }
    }

    /** Códigos fallidos de la cuenta en la ventana; al primero se fija la expiración. */
    public long countFailure(UUID userPublicId) {
        String key = FAILURES_PREFIX + userPublicId;
        try {
            Long failures = redis.opsForValue().increment(key);
            if (failures != null && failures == 1) {
                redis.expire(key, config.getFailureWindow());
            }
            return failures == null ? 0 : failures;
        } catch (DataAccessException ex) {
            throw unavailable();
        }
    }

    public void clearFailures(UUID userPublicId) {
        try {
            redis.delete(FAILURES_PREFIX + userPublicId);
        } catch (DataAccessException ex) {
            throw unavailable();
        }
    }

    public Duration ttl() {
        return config.getChallengeTtl();
    }

    private static String fingerprint(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Almacen de sesiones no disponible",
                "No fue posible contactar Redis para el segundo factor.");
    }
}
