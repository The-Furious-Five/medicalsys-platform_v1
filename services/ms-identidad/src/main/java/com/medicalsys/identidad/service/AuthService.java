package com.medicalsys.identidad.service;

import com.medicalsys.identidad.audit.AuditEventDraft;
import com.medicalsys.identidad.audit.AuditTrail;
import com.medicalsys.identidad.config.IdentityProperties;
import com.medicalsys.identidad.domain.UserAccount;
import com.medicalsys.identidad.domain.UserStatus;
import com.medicalsys.identidad.repository.UserAccountRepository;
import com.medicalsys.identidad.security.IssuedToken;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final SessionStore sessionStore;
    private final IdentityMapper mapper;
    private final IdentityProperties properties;
    private final AccountAccessService accountAccess;
    private final AuditTrail auditTrail;

    public AuthService(UserAccountRepository users, PasswordEncoder passwordEncoder, TokenService tokenService,
                       SessionStore sessionStore, IdentityMapper mapper, IdentityProperties properties,
                       AccountAccessService accountAccess, AuditTrail auditTrail) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.sessionStore = sessionStore;
        this.mapper = mapper;
        this.properties = properties;
        this.accountAccess = accountAccess;
        this.auditTrail = auditTrail;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Instant now = Instant.now();
        String email = request.email().trim();
        Optional<UserAccount> found = users.findByEmailIgnoreCase(email);
        if (found.isEmpty()) {
            // Mismo mensaje que una contrasena incorrecta: no revelamos si el correo existe.
            auditTrail.record(AuditEventDraft.authentication("LOGIN_FAILED", "DENIED", null, email, null, 401,
                    "Correo no registrado"));
            throw ApiException.unauthorized("Correo o contrasena incorrectos");
        }

        UserAccount user = found.get();
        if (user.isCurrentlyLocked(now)) {
            auditTrail.record(AuditEventDraft.authentication("ACCOUNT_LOCKED", "DENIED", user.getPublicId(),
                    user.getEmail(), user.getOrganizationPublicId(), 423,
                    "Cuenta bloqueada hasta " + user.getLockedUntil()));
            throw new ApiException(org.springframework.http.HttpStatus.LOCKED, "Cuenta bloqueada",
                    "La cuenta esta bloqueada hasta " + user.getLockedUntil());
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            auditTrail.record(AuditEventDraft.authentication("LOGIN_FAILED", "DENIED", user.getPublicId(),
                    user.getEmail(), user.getOrganizationPublicId(), 403, "La cuenta esta inactiva"));
            throw ApiException.forbidden("La cuenta esta inactiva");
        }
        if (user.getStatus() == UserStatus.PENDING_ACTIVATION) {
            auditTrail.record(AuditEventDraft.authentication("LOGIN_FAILED", "DENIED", user.getPublicId(),
                    user.getEmail(), user.getOrganizationPublicId(), 403, "La cuenta aun no fue activada"));
            throw ApiException.forbidden("La cuenta aun no fue activada");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // El contador se guarda en una transaccion aparte: esta se revierte al lanzar el 401.
            int attempts = accountAccess.registerFailedAttempt(user.getId());
            log.warn("Intento de acceso fallido para la cuenta {} (intentos={})", user.getPublicId(), attempts);
            auditTrail.record(AuditEventDraft.authentication("LOGIN_FAILED", "DENIED", user.getPublicId(),
                    user.getEmail(), user.getOrganizationPublicId(), 401, "Contrasena incorrecta"));
            throw ApiException.unauthorized("Correo o contrasena incorrectos");
        }

        user.registerSuccessfulAccess(now);
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
