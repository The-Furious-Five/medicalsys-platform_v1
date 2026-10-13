package com.medicalsys.identidad.service;

import com.medicalsys.identidad.config.IdentityProperties;
import com.medicalsys.identidad.domain.UserAccount;
import com.medicalsys.identidad.domain.UserStatus;
import com.medicalsys.identidad.repository.UserAccountRepository;
import com.medicalsys.identidad.security.IssuedToken;
import com.medicalsys.identidad.security.KeycloakClient.PasswordCheck;
import com.medicalsys.identidad.security.SessionStore;
import com.medicalsys.identidad.security.TokenService;
import com.medicalsys.identidad.web.dto.AuthenticatedUser;
import com.medicalsys.identidad.web.dto.LoginRequest;
import com.medicalsys.identidad.web.dto.LoginResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserAccountRepository users;
    private final CredentialService credentials;
    private final TokenService tokenService;
    private final SessionStore sessionStore;
    private final IdentityMapper mapper;
    private final IdentityProperties properties;
    private final AccountAccessService accountAccess;

    public AuthService(UserAccountRepository users, CredentialService credentials, TokenService tokenService,
                       SessionStore sessionStore, IdentityMapper mapper, IdentityProperties properties,
                       AccountAccessService accountAccess) {
        this.users = users;
        this.credentials = credentials;
        this.tokenService = tokenService;
        this.sessionStore = sessionStore;
        this.mapper = mapper;
        this.properties = properties;
        this.accountAccess = accountAccess;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Instant now = Instant.now();
        Optional<UserAccount> found = users.findByEmailIgnoreCase(request.email().trim());
        if (found.isEmpty()) {
            // Mismo mensaje que una contrasena incorrecta: no revelamos si el correo existe.
            throw ApiException.unauthorized("Correo o contrasena incorrectos");
        }

        UserAccount user = found.get();
        if (user.isCurrentlyLocked(now)) {
            throw new ApiException(HttpStatus.LOCKED, "Cuenta bloqueada",
                    "La cuenta esta bloqueada hasta " + user.getLockedUntil());
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw ApiException.forbidden("La cuenta esta inactiva");
        }
        if (user.getStatus() == UserStatus.PENDING_ACTIVATION) {
            throw ApiException.forbidden("La cuenta aun no fue activada");
        }

        // La contrasena la verifica Keycloak (token endpoint, cliente confidencial ms-identidad).
        PasswordCheck check = credentials.verify(user, request.password());
        switch (check) {
            case INVALID -> {
                // El contador se guarda en una transaccion aparte: esta se revierte al lanzar el 401.
                int attempts = accountAccess.registerFailedAttempt(user.getId());
                log.warn("Intento de acceso fallido para la cuenta {} (intentos={})", user.getPublicId(), attempts);
                throw ApiException.unauthorized("Correo o contrasena incorrectos");
            }
            case LOCKED -> throw new ApiException(HttpStatus.LOCKED, "Cuenta bloqueada",
                    "La cuenta esta bloqueada temporalmente por intentos fallidos");
            case PASSWORD_CHANGE_REQUIRED -> throw new ApiException(HttpStatus.FORBIDDEN,
                    "Cambio de contrasena requerido",
                    "La contrasena vencio o es temporal: debe cambiarse antes de iniciar sesion",
                    "PASSWORD_CHANGE_REQUIRED");
            case VALID -> { }
        }

        user.registerSuccessfulAccess(now);
        List<String> roles = mapper.activeRoleCodes(user);
        List<String> permissions = mapper.activePermissionCodes(user);
        IssuedToken token = tokenService.issue(user, roles, permissions);
        sessionStore.open(token.jti(), user.getPublicId(), token.expiresAt());

        return new LoginResponse(
                "Bearer",
                token.value(),
                Duration.between(token.issuedAt(), token.expiresAt()).toSeconds(),
                token.issuedAt(),
                token.expiresAt(),
                describe(user, roles, permissions));
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
    }

    private AuthenticatedUser describe(UserAccount user, List<String> roles, List<String> permissions) {
        return new AuthenticatedUser(
                user.getPublicId(),
                user.getEmail(),
                user.getFullName(),
                user.getOrganizationPublicId(),
                user.getStatus(),
                user.getLanguage(),
                roles,
                permissions);
    }
}
