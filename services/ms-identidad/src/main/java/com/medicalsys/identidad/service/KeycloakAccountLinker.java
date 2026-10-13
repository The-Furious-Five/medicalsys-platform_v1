package com.medicalsys.identidad.service;

import com.medicalsys.identidad.domain.UserAccount;
import com.medicalsys.identidad.domain.UserStatus;
import com.medicalsys.identidad.keycloak.KeycloakClient;
import com.medicalsys.identidad.keycloak.LegacyArgon2Hash;
import com.medicalsys.identidad.repository.UserAccountRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Enlaza una cuenta de ms-identidad con su usuario de Keycloak (IAM-03).
 *
 * <p>Si Keycloak ya tiene un usuario con ese correo, solo se guarda su id. Si no, se crea con el
 * hash Argon2id local, sin conocer la contraseña. En ambos casos el hash local se borra: la
 * credencial queda solo en Keycloak. Corre en su propia transacción para que el enlace se confirme
 * aunque el login que lo pidió termine en 401.
 */
@Service
public class KeycloakAccountLinker {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAccountLinker.class);

    private final KeycloakClient keycloak;
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;

    public KeycloakAccountLinker(KeycloakClient keycloak, UserAccountRepository users,
                                 PasswordEncoder passwordEncoder) {
        this.keycloak = keycloak;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Para las cuentas cuya contraseña sale de la configuración (demostración y bootstrap): si no
     * tienen credencial en Keycloak ni hash local, por ejemplo porque se recreó la base de Keycloak,
     * la repone desde la configuración para que {@link KeycloakAccountMigration} la vuelva a crear.
     * Se llama dentro de la transacción del seeder, sobre la entidad que este administra.
     */
    public void restoreConfiguredCredential(UserAccount user, String password) {
        try {
            boolean missing = user.isLinkedToIdentityProvider()
                    ? !keycloak.userExists(user.getKeycloakUserId())
                    : !StringUtils.hasText(user.getPasswordHash());
            if (missing) {
                user.setKeycloakUserId(null);
                user.setPasswordHash(passwordEncoder.encode(password));
                log.warn("La cuenta {} no tenía credencial en Keycloak; se repone desde la configuración",
                        user.getPublicId());
            }
        } catch (ApiException ex) {
            log.warn("Keycloak no disponible: no se pudo revisar la credencial de {}", user.getPublicId());
        }
    }

    /** Id de Keycloak de la cuenta, enlazándola si hace falta; vacío si no hay con qué crearla. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<String> link(Long userAccountId) {
        UserAccount user = users.findById(userAccountId).orElse(null);
        if (user == null) {
            return Optional.empty();
        }
        if (user.isLinkedToIdentityProvider()) {
            return Optional.of(user.getKeycloakUserId());
        }
        Optional<String> existing = keycloak.findUserIdByEmail(user.getEmail());
        String keycloakUserId = existing.orElseGet(() -> createFromLegacyHash(user));
        if (keycloakUserId == null) {
            return Optional.empty();
        }
        user.setKeycloakUserId(keycloakUserId);
        user.setPasswordHash(null);
        log.info("Cuenta {} enlazada a Keycloak ({})", user.getPublicId(),
                existing.isPresent() ? "usuario existente" : "hash Argon2id importado");
        return Optional.of(keycloakUserId);
    }

    /** Desenlaza las cuentas cuyo usuario ya no existe en Keycloak, para que se puedan reenlazar. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean unlinkIfMissing(Long userAccountId) {
        UserAccount user = users.findById(userAccountId).orElse(null);
        if (user == null || !user.isLinkedToIdentityProvider() || keycloak.userExists(user.getKeycloakUserId())) {
            return false;
        }
        log.warn("La cuenta {} apuntaba a un usuario de Keycloak que ya no existe; se desenlaza",
                user.getPublicId());
        user.setKeycloakUserId(null);
        return true;
    }

    private String createFromLegacyHash(UserAccount user) {
        if (!StringUtils.hasText(user.getPasswordHash())) {
            log.warn("La cuenta {} no tiene usuario en Keycloak ni hash local: requiere reinicio de contraseña",
                    user.getPublicId());
            return null;
        }
        LegacyArgon2Hash hash;
        try {
            hash = LegacyArgon2Hash.parse(user.getPasswordHash());
        } catch (IllegalArgumentException ex) {
            log.warn("La cuenta {} tiene un hash que no se puede migrar: {}", user.getPublicId(), ex.getMessage());
            return null;
        }
        String[] names = splitName(user.getFullName());
        return keycloak.createUserWithLegacyHash(new KeycloakClient.NewUser(user.getEmail(), names[0], names[1],
                user.getStatus() != UserStatus.INACTIVE), hash);
    }

    /** Keycloak exige nombre y apellido; full_name es un solo campo. */
    static String[] splitName(String fullName) {
        String trimmed = fullName == null ? "" : fullName.trim();
        int space = trimmed.indexOf(' ');
        if (space < 0) {
            return new String[] {trimmed.isEmpty() ? "-" : trimmed, "-"};
        }
        return new String[] {trimmed.substring(0, space), trimmed.substring(space + 1).trim()};
    }
}
