package com.medicalsys.identidad.service;

import com.medicalsys.identidad.audit.AuditEventDraft;
import com.medicalsys.identidad.audit.AuditTrail;
import com.medicalsys.identidad.config.IdentityProperties;
import com.medicalsys.identidad.domain.UserAccount;
import com.medicalsys.identidad.domain.UserStatus;
import com.medicalsys.identidad.keycloak.KeycloakClient;
import com.medicalsys.identidad.repository.UserAccountRepository;
import com.medicalsys.identidad.security.IssuedToken;
import com.medicalsys.identidad.security.mfa.MfaChallengeStore;
import com.medicalsys.identidad.security.SessionStore;
import com.medicalsys.identidad.security.TokenService;
import com.medicalsys.identidad.web.dto.AuthenticatedUser;
import com.medicalsys.identidad.web.dto.LoginRequest;
import com.medicalsys.identidad.web.dto.LoginResponse;
import com.medicalsys.identidad.web.dto.MfaChallengeResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inicio de sesión (IAM-03, IAM-07). Se entra con el nombre de usuario estándar; la contraseña se valida
 * contra Keycloak, que aplica la política, el vencimiento y el bloqueo al 3er intento. Una contraseña
 * válida no abre sesión: abre un desafío del segundo factor ({@link MfaService}) y recién con el código
 * TOTP correcto ms-identidad emite su JWT con roles y permisos. Cada desenlace queda en la bitácora de
 * ms-auditoria (LOG-01).
 */
@Service
public class AuthService {

    static final String INVALID_CREDENTIALS = "Usuario o contrasena incorrectos";

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserAccountRepository users;
    private final TokenService tokenService;
    private final SessionStore sessionStore;
    private final IdentityMapper mapper;
    private final AccountAccessService accountAccess;
    private final KeycloakClient keycloak;
    private final KeycloakAccountLinker linker;
    private final AuditTrail auditTrail;
    private final IdentityProperties properties;
    private final MfaChallengeStore challenges;

    public AuthService(UserAccountRepository users, TokenService tokenService, SessionStore sessionStore,
                       IdentityMapper mapper, AccountAccessService accountAccess, KeycloakClient keycloak,
                       KeycloakAccountLinker linker, AuditTrail auditTrail,
                       IdentityProperties properties, MfaChallengeStore challenges) {
        this.users = users;
        this.tokenService = tokenService;
        this.sessionStore = sessionStore;
        this.mapper = mapper;
        this.accountAccess = accountAccess;
        this.keycloak = keycloak;
        this.linker = linker;
        this.auditTrail = auditTrail;
        this.properties = properties;
        this.challenges = challenges;
    }

    /** Primer paso: usuario y contraseña. Si son válidos, devuelve el desafío del segundo factor. */
    @Transactional
    public MfaChallengeResponse login(LoginRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        Optional<UserAccount> found = users.findByUsername(username);
        if (found.isEmpty()) {
            // Mismo mensaje que una contrasena incorrecta: no revelamos si el usuario existe.
            // La cuenta no existe, asi que no tiene organizacion; ms-auditoria exige una. El intento se asienta
            // en la organizacion de la plataforma, que es la que ve los ataques de enumeracion de cuentas.
            auditTrail.record(AuditEventDraft.authentication("LOGIN_FAILED", "DENIED", null, username,
                    properties.getPlatformAdmin().getOrganizationPublicId(), 401, "Usuario no registrado"));
            throw ApiException.unauthorized(INVALID_CREDENTIALS);
        }

        UserAccount user = found.get();
        if (user.getStatus() == UserStatus.LOCKED) {
            // Bloqueo de Keycloak (3 contrasenas) o del segundo factor: solo un administrador lo levanta.
            recordFailure(user, 423, "La cuenta esta bloqueada");
            throw ApiException.accountLocked();
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            recordFailure(user, 403, "La cuenta esta inactiva");
            throw ApiException.forbidden("La cuenta esta inactiva");
        }
        if (user.getStatus() == UserStatus.PENDING_ACTIVATION) {
            recordFailure(user, 403, "La cuenta aun no fue activada");
            throw ApiException.forbidden("La cuenta aun no fue activada");
        }

        String keycloakUserId = keycloakUserId(user);
        switch (keycloak.checkPassword(user.getEmail(), request.password())) {
            case VALID -> { }
            case PASSWORD_CHANGE_REQUIRED -> {
                recordFailure(user, 403, "Contrasena vencida o temporal: debe cambiarse");
                throw ApiException.passwordChangeRequired();
            }
            case DISABLED -> {
                recordFailure(user, 403, "La cuenta esta deshabilitada en Keycloak");
                throw ApiException.forbidden("La cuenta esta inactiva");
            }
            case INVALID_CREDENTIALS -> {
                // Keycloak responde lo mismo a una contrasena incorrecta y a una cuenta bloqueada.
                if (keycloak.isLockedOut(keycloakUserId)) {
                    accountAccess.registerLockout(user.getId());
                    log.warn("Acceso rechazado: la cuenta {} esta bloqueada por intentos fallidos", user.getPublicId());
                    auditTrail.record(AuditEventDraft.authentication("ACCOUNT_LOCKED", "DENIED", user.getPublicId(),
                            user.getEmail(), user.getOrganizationPublicId(), 423,
                            "Bloqueada en Keycloak por intentos fallidos"));
                    throw ApiException.accountLocked();
                }
                log.warn("Intento de acceso fallido para la cuenta {}", user.getPublicId());
                recordFailure(user, 401, "Contrasena incorrecta");
                throw ApiException.unauthorized(INVALID_CREDENTIALS);
            }
        }

        MfaChallengeStore.Purpose purpose = user.isMfaEnabled()
                ? MfaChallengeStore.Purpose.VERIFY : MfaChallengeStore.Purpose.ENROLL;
        String token = challenges.open(user.getPublicId(), purpose);
        return new MfaChallengeResponse(
                purpose == MfaChallengeStore.Purpose.VERIFY ? "MFA_REQUIRED" : "MFA_ENROLLMENT_REQUIRED",
                token, challenges.ttl().toSeconds(), user.getUsername());
    }

    /** Segundo paso superado (MfaService): recién aquí se abre la sesión. */
    LoginResponse completeLogin(UserAccount user) {
        user.registerSuccessfulAccess(Instant.now());
        List<String> roles = mapper.activeRoleCodes(user);
        List<String> permissions = mapper.activePermissionCodes(user);
        IssuedToken token = tokenService.issue(user, roles, permissions);
        sessionStore.open(token.jti(), user.getPublicId(), token.expiresAt());

        auditTrail.record(AuditEventDraft.authentication("LOGIN_SUCCESS", "SUCCESS", user.getPublicId(),
                user.getEmail(), user.getOrganizationPublicId(), 200, null));

        return new LoginResponse(
                "Bearer",
                token.value(),
                Duration.between(token.issuedAt(), token.expiresAt()).toSeconds(),
                token.issuedAt(),
                token.expiresAt(),
                describe(user, roles, permissions));
    }

    /**
     * Id de Keycloak de la cuenta. Las cuentas creadas antes de IAM-03 se enlazan aqui si la migracion
     * de arranque no las alcanzo; el cambio se copia a esta entidad para que el guardado del login no
     * lo pise.
     */
    private String keycloakUserId(UserAccount user) {
        if (user.isLinkedToIdentityProvider()) {
            return user.getKeycloakUserId();
        }
        String linked = linker.link(user.getId()).orElseThrow(() -> {
            recordFailure(user, 401, "La cuenta no tiene credencial en Keycloak");
            return ApiException.unauthorized(INVALID_CREDENTIALS);
        });
        user.setKeycloakUserId(linked);
        user.setPasswordHash(null);
        return linked;
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser currentUser(UUID publicId) {
        UserAccount user = users.findByPublicId(publicId)
                .orElseThrow(() -> ApiException.notFound("No existe la cuenta " + publicId));
        user.getRoleAssignments().forEach(assignment -> assignment.getRole().getPermissions().size());
        return describe(user, mapper.activeRoleCodes(user), mapper.activePermissionCodes(user));
    }

    public void logout(String jti, UUID publicId) {
        sessionStore.close(jti, publicId);
        UserAccount user = users.findByPublicId(publicId).orElse(null);
        auditTrail.record(AuditEventDraft.authentication("LOGOUT", "SUCCESS", publicId,
                user == null ? null : user.getEmail(), user == null ? null : user.getOrganizationPublicId(),
                200, null));
    }

    private void recordFailure(UserAccount user, int status, String reason) {
        auditTrail.record(AuditEventDraft.authentication("LOGIN_FAILED", "DENIED", user.getPublicId(),
                user.getEmail(), user.getOrganizationPublicId(), status, reason));
    }

    private AuthenticatedUser describe(UserAccount user, List<String> roles, List<String> permissions) {
        return new AuthenticatedUser(
                user.getPublicId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getOrganizationPublicId(),
                user.getStatus(),
                user.getClearance(),
                user.getLanguage(),
                roles,
                permissions);
    }
}
