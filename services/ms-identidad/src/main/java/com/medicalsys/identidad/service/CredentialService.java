package com.medicalsys.identidad.service;

import com.medicalsys.identidad.config.IdentityProperties;
import com.medicalsys.identidad.domain.UserAccount;
import com.medicalsys.identidad.security.KeycloakClient;
import com.medicalsys.identidad.security.KeycloakClient.PasswordCheck;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Punto unico para verificar y guardar contrasenas.
 *
 * <p>Con Keycloak habilitado la contrasena vive solo en Keycloak: {@code password_hash} queda en
 * NULL y {@code hash_algorithm} en {@value #KEYCLOAK_ALGORITHM}. Las cuentas que todavia tienen un
 * hash Argon2 local se migran la primera vez que inician sesion con la contrasena correcta.
 */
@Service
public class CredentialService {

    public static final String KEYCLOAK_ALGORITHM = "keycloak";

    private static final Logger log = LoggerFactory.getLogger(CredentialService.class);

    private final KeycloakClient keycloak;
    private final PasswordEncoder passwordEncoder;
    private final IdentityProperties.Keycloak config;

    public CredentialService(KeycloakClient keycloak, PasswordEncoder passwordEncoder, IdentityProperties properties) {
        this.keycloak = keycloak;
        this.passwordEncoder = passwordEncoder;
        this.config = properties.getKeycloak();
    }

    public PasswordCheck verify(UserAccount user, String rawPassword) {
        if (isInKeycloak(user)) {
            requireKeycloak();
            return keycloak.checkPassword(user.getEmail(), rawPassword);
        }
        if (!matchesLocalHash(user, rawPassword)) {
            return PasswordCheck.INVALID;
        }
        if (config.isEnabled()) {
            store(user, rawPassword);
            log.info("Contrasena de la cuenta {} migrada a Keycloak", user.getPublicId());
        }
        return PasswordCheck.VALID;
    }

    public void store(UserAccount user, String rawPassword) {
        if (config.isEnabled()) {
            keycloak.setPassword(user.getEmail(), user.getFullName(), rawPassword);
            user.setPasswordHash(null);
            user.setHashAlgorithm(KEYCLOAK_ALGORITHM);
        } else {
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            user.setHashAlgorithm("argon2id");
        }
        user.setPasswordUpdatedAt(Instant.now());
    }

    /**
     * Migracion anticipada de cuentas sembradas: si el hash local corresponde a la contrasena
     * conocida, la cuenta pasa a Keycloak sin esperar a su primer inicio de sesion. Una cuenta que
     * ya existe en Keycloak conserva su contrasena de alli.
     */
    public void migrateIfMatches(UserAccount user, String knownPassword) {
        if (!config.isEnabled() || isInKeycloak(user) || !matchesLocalHash(user, knownPassword)) {
            return;
        }
        keycloak.ensureUser(user.getEmail(), user.getFullName(), knownPassword);
        user.setPasswordHash(null);
        user.setHashAlgorithm(KEYCLOAK_ALGORITHM);
        log.info("Cuenta {} migrada a Keycloak", user.getEmail());
    }

    private static boolean isInKeycloak(UserAccount user) {
        return KEYCLOAK_ALGORITHM.equals(user.getHashAlgorithm());
    }

    private boolean matchesLocalHash(UserAccount user, String rawPassword) {
        return user.getPasswordHash() != null && passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    private void requireKeycloak() {
        if (!config.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Keycloak deshabilitado",
                    "La contrasena de esta cuenta esta en Keycloak y KEYCLOAK_ENABLED=false");
        }
    }
}
