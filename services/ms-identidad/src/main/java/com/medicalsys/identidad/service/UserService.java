package com.medicalsys.identidad.service;

import com.medicalsys.identidad.audit.AuditEventDraft;
import com.medicalsys.identidad.audit.AuditTrail;
import com.medicalsys.identidad.domain.Role;
import com.medicalsys.identidad.domain.RoleScope;
import com.medicalsys.identidad.domain.SecurityLevel;
import com.medicalsys.identidad.domain.UserAccount;
import com.medicalsys.identidad.domain.UserRole;
import com.medicalsys.identidad.domain.UserStatus;
import com.medicalsys.identidad.keycloak.KeycloakClient;
import com.medicalsys.identidad.keycloak.PasswordPolicyException;
import com.medicalsys.identidad.repository.RoleRepository;
import com.medicalsys.identidad.repository.UserAccountRepository;
import com.medicalsys.identidad.security.SessionStore;
import com.medicalsys.identidad.security.mfa.MfaChallengeStore;
import com.medicalsys.identidad.service.RoleGovernance.Actor;
import com.medicalsys.identidad.web.dto.ChangeClearanceRequest;
import com.medicalsys.identidad.web.dto.ChangePasswordRequest;
import com.medicalsys.identidad.web.dto.ChangeStatusRequest;
import com.medicalsys.identidad.web.dto.CreateUserRequest;
import com.medicalsys.identidad.web.dto.PageResponse;
import com.medicalsys.identidad.web.dto.RoleAssignmentRequest;
import com.medicalsys.identidad.web.dto.TemporaryPasswordResponse;
import com.medicalsys.identidad.web.dto.UpdateUserRequest;
import com.medicalsys.identidad.web.dto.UserResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final String ASSIGN_PERMISSION = "identity.role.assign";

    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final IdentityMapper mapper;
    private final SessionStore sessionStore;
    private final RoleGovernance governance;
    private final KeycloakClient keycloak;
    private final KeycloakAccountLinker linker;
    private final UserCodeGenerator userCodes;
    private final TemporaryPasswordGenerator temporaryPasswords;
    private final AccountAccessService accountAccess;
    private final AuditTrail auditTrail;
    private final UsernameGenerator usernames;
    private final MfaChallengeStore mfaChallenges;

    public UserService(UserAccountRepository users, RoleRepository roles, IdentityMapper mapper,
                       SessionStore sessionStore, RoleGovernance governance,
                       KeycloakClient keycloak, KeycloakAccountLinker linker, UserCodeGenerator userCodes,
                       TemporaryPasswordGenerator temporaryPasswords, AccountAccessService accountAccess,
                       AuditTrail auditTrail, UsernameGenerator usernames, MfaChallengeStore mfaChallenges) {
        this.users = users;
        this.roles = roles;
        this.mapper = mapper;
        this.sessionStore = sessionStore;
        this.governance = governance;
        this.keycloak = keycloak;
        this.linker = linker;
        this.userCodes = userCodes;
        this.temporaryPasswords = temporaryPasswords;
        this.accountAccess = accountAccess;
        this.auditTrail = auditTrail;
        this.usernames = usernames;
        this.mfaChallenges = mfaChallenges;
    }

    /**
     * Alta desde la API (IAM-04): la cuenta se crea en MedicalSys con su User ID y en Keycloak con
     * contraseña temporal (UPDATE_PASSWORD). Si no se indica, el sistema la genera y la devuelve una sola
     * vez. Si la transacción local falla después de crear el usuario en Keycloak, ese alta se deshace.
     */
    @Transactional
    public UserResponse create(CreateUserRequest request, UUID actorPublicId) {
        UserAccount user = newAccount(request, actorPublicId);
        user.setPasswordUpdatedAt(Instant.now());

        // Keycloak primero: si rechaza la contraseña por la política, no se consume un User ID.
        boolean generated = !StringUtils.hasText(request.password());
        String temporaryPassword = generated ? temporaryPasswords.generate() : request.password();
        String[] names = KeycloakAccountLinker.splitName(user.getFullName());
        String keycloakUserId;
        try {
            keycloakUserId = keycloak.createUserWithPassword(new KeycloakClient.NewUser(
                    user.getPublicId().toString(), user.getEmail(), names[0], names[1],
                    user.getStatus() != UserStatus.INACTIVE), temporaryPassword, true);
        } catch (PasswordPolicyException ex) {
            throw ApiException.passwordPolicy(ex.getMessage());
        }
        undoKeycloakAccountOnRollback(keycloakUserId);
        user.setKeycloakUserId(keycloakUserId);

        UserCodeGenerator.IssuedCode code = userCodes.next();
        user.setUserCode(code.code());
        users.saveAndFlush(user);
        userCodes.assign(code, user.getId());
        record("USER_CREATED", actorPublicId, user, null, Map.of("userCode", user.getUserCode()));

        UserResponse response = mapper.toUserResponse(user);
        return generated ? response.withTemporaryPassword(temporaryPassword) : response;
    }

    /**
     * Cuentas sembradas al arrancar: las de demostración y las dos de arranque (PLATFORM_ADMIN y OSI),
     * con su identificador público fijo. Se crean directo en Keycloak, así que la política de IAM-02
     * rige también para ellas (antes se importaba un hash local y la política no se aplicaba).
     *
     * <p>Sin {@code password}, la cuenta nace con una temporal aleatoria propia que no se devuelve ni se
     * registra: para usarla, el OSI la reinicia (PR-02) y la persona elige la suya. Ninguna cuenta
     * comparte contraseña con otra.
     */
    @Transactional
    public UserResponse createSeedAccount(CreateUserRequest request, UUID publicId) {
        UserAccount user = newAccount(request, null);
        user.setPublicId(publicId);
        ensureSeedCredential(user, request.password());
        UserCodeGenerator.IssuedCode code = userCodes.next();
        user.setUserCode(code.code());
        users.saveAndFlush(user);
        userCodes.assign(code, user.getId());
        return mapper.toUserResponse(user);
    }

    /**
     * Garantiza que la cuenta sembrada tenga credencial en Keycloak (por ejemplo, si se recreó su base);
     * si ya la tiene, no la toca. Con {@code password} vacío usa una temporal aleatoria. Devuelve true
     * si tuvo que crearla o reponerla.
     */
    @Transactional
    public boolean ensureSeedCredential(UserAccount user, String password) {
        if (user.isLinkedToIdentityProvider() && keycloak.userExists(user.getKeycloakUserId())) {
            return false;
        }
        boolean temporary = !StringUtils.hasText(password);
        String credential = temporary ? temporaryPasswords.generate() : password;
        String keycloakUserId;
        try {
            keycloakUserId = keycloak.findUserIdByEmail(user.getEmail()).orElse(null);
            if (keycloakUserId == null) {
                String[] names = KeycloakAccountLinker.splitName(user.getFullName());
                keycloakUserId = keycloak.createUserWithPassword(new KeycloakClient.NewUser(
                        user.getPublicId().toString(), user.getEmail(), names[0], names[1],
                        user.getStatus() != UserStatus.INACTIVE), credential, temporary);
                undoKeycloakAccountOnRollback(keycloakUserId);
            } else {
                keycloak.setPassword(keycloakUserId, credential, temporary);
            }
        } catch (PasswordPolicyException ex) {
            throw ApiException.passwordPolicy(ex.getMessage());
        }
        user.setKeycloakUserId(keycloakUserId);
        user.setPasswordHash(null);
        user.setPasswordUpdatedAt(Instant.now());
        return true;
    }

    /**
     * Rotación de una cuenta demo (IDENTITY_DEMO_ROTATE_PASSWORDS): temporal aleatoria nueva, cambio
     * obligatorio en el próximo ingreso y sesiones cerradas. Corrige las bases sembradas con la
     * contraseña común de versiones anteriores. La temporal no se devuelve ni se registra.
     */
    @Transactional
    public void rotateSeedPassword(UserAccount user) {
        if (!ensureSeedCredential(user, null)) {
            keycloak.setPassword(user.getKeycloakUserId(), temporaryPasswords.generate(), true);
            user.setPasswordUpdatedAt(Instant.now());
        }
        sessionStore.closeAll(user.getPublicId());
        record("PASSWORD_RESET", null, user, "Rotacion de la contrasena comun de las cuentas demo", Map.of());
    }

    private UserAccount newAccount(CreateUserRequest request, UUID actorPublicId) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Ya existe una cuenta con el correo " + email);
        }
        Actor actor = governance.resolve(actorPublicId);

        // IAM-07: el usuario sale de nombres y apellidos y no se edita nunca después.
        boolean structured = StringUtils.hasText(request.givenNames()) || StringUtils.hasText(request.firstSurname());
        UsernameGenerator.NameParts parts = structured
                ? new UsernameGenerator.NameParts(request.givenNames(), request.firstSurname(), request.secondSurname())
                : UsernameGenerator.parse(request.fullName());
        String fullName = StringUtils.hasText(request.fullName()) ? request.fullName().trim() : parts.fullName();

        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setFullName(fullName);
        user.setUsername(usernames.next(parts));
        user.setPhoneE164(StringUtils.hasText(request.phoneE164()) ? request.phoneE164() : null);
        user.setOrganizationPublicId(governance.organizationForNewAccount(actor, request.organizationPublicId()));
        user.setLanguage(StringUtils.hasText(request.language()) ? request.language() : "es-BO");
        user.setStatus(Boolean.TRUE.equals(request.activateImmediately())
                ? UserStatus.ACTIVE
                : UserStatus.PENDING_ACTIVATION);

        boolean withRoles = request.roles() != null && !request.roles().isEmpty();
        // identity.user.create no basta: sin esto, crear la cuenta con roles evitaria identity.role.assign.
        if (actor != null && withRoles && !actor.holds(ASSIGN_PERMISSION)) {
            throw ApiException.forbidden("Crear una cuenta con roles requiere el permiso " + ASSIGN_PERMISSION);
        }
        if (withRoles) {
            for (RoleAssignmentRequest assignment : request.roles()) {
                attachRole(user, assignment, actor);
            }
        }
        return user;
    }

    /** Si la transacción del alta se revierte, el usuario ya creado en Keycloak se borra. */
    private void undoKeycloakAccountOnRollback(String keycloakUserId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    keycloak.deleteUser(keycloakUserId);
                }
            }
        });
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(UUID organizationPublicId, UserStatus status, String query,
                                             Pageable pageable, UUID actorPublicId) {
        UUID scope = governance.searchScope(governance.resolve(actorPublicId), organizationPublicId);
        String search = StringUtils.hasText(query) ? "%" + query.trim().toLowerCase() + "%" : null;
        Page<UserAccount> page = users.search(scope, status, search, pageable);
        page.getContent().forEach(user -> user.getRoleAssignments().size());
        return PageResponse.of(page, mapper::toUserResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID publicId, UUID actorPublicId) {
        UserAccount user = require(publicId);
        governance.checkCanSee(governance.resolve(actorPublicId), user);
        return mapper.toUserResponse(user);
    }

    /** Modificación (IAM-04): nombre y correo se sincronizan con Keycloak; el User ID no cambia. */
    @Transactional
    public UserResponse update(UUID publicId, UpdateUserRequest request, UUID actorPublicId) {
        UserAccount user = require(publicId);
        governance.checkCanManageAccount(governance.resolve(actorPublicId), user);
        boolean profileChanged = false;
        if (StringUtils.hasText(request.fullName()) && !request.fullName().trim().equals(user.getFullName())) {
            user.setFullName(request.fullName().trim());
            profileChanged = true;
        }
        if (StringUtils.hasText(request.email())) {
            String email = request.email().trim().toLowerCase();
            if (!email.equalsIgnoreCase(user.getEmail())) {
                if (users.existsByEmailIgnoreCase(email)) {
                    throw ApiException.conflict("Ya existe una cuenta con el correo " + email);
                }
                user.setEmail(email);
                profileChanged = true;
            }
        }
        if (request.phoneE164() != null) {
            user.setPhoneE164(StringUtils.hasText(request.phoneE164()) ? request.phoneE164() : null);
        }
        if (StringUtils.hasText(request.language())) {
            user.setLanguage(request.language());
        }
        if (profileChanged && user.isLinkedToIdentityProvider()) {
            String[] names = KeycloakAccountLinker.splitName(user.getFullName());
            keycloak.updateProfile(user.getKeycloakUserId(), user.getEmail(), names[0], names[1]);
        }
        record("USER_UPDATED", actorPublicId, user, null, Map.of());
        return mapper.toUserResponse(user);
    }

    @Transactional
    public UserResponse changeStatus(UUID publicId, ChangeStatusRequest request, UUID actorPublicId) {
        UserAccount user = require(publicId);
        governance.checkCanManageAccount(governance.resolve(actorPublicId), user);
        UserStatus target = request.status();
        UserStatus previous = user.getStatus();
        long administrators = target == UserStatus.ACTIVE ? 0 : governance.roleAdministrators();
        if (target == UserStatus.LOCKED && user.getLockedUntil() == null) {
            user.setLockedUntil(Instant.now().plusSeconds(3600));
        }
        if (target == UserStatus.ACTIVE) {
            user.setLockedUntil(null);
            // El bloqueo al 3er intento lo aplica Keycloak: reactivar la cuenta tambien lo levanta.
            if (user.isLinkedToIdentityProvider()) {
                keycloak.unlock(user.getKeycloakUserId());
            }
            // Y el de los codigos de segundo factor (IAM-07).
            mfaChallenges.clearFailures(user.getPublicId());
        }
        // Baja (IAM-04): se deshabilita en Keycloak, nunca se borra, para conservar la trazabilidad.
        if ((target == UserStatus.INACTIVE || target == UserStatus.LOCKED) && user.isLinkedToIdentityProvider()) {
            keycloak.setEnabled(user.getKeycloakUserId(), false);
        }
        user.setStatus(target);
        if (target != UserStatus.ACTIVE) {
            governance.checkRoleAdministrationSurvives(administrators);
            sessionStore.closeAll(user.getPublicId());
        }
        String action = previous == UserStatus.LOCKED && target == UserStatus.ACTIVE
                ? "ACCOUNT_UNLOCKED"
                : target != UserStatus.ACTIVE ? "USER_DISABLED" : "USER_UPDATED";
        record(action, actorPublicId, user, request.reason(),
                Map.of("previousStatus", previous.name(), "newStatus", target.name()));
        return mapper.toUserResponse(user);
    }

    /**
     * IAM-03: la contrasena vive en Keycloak. La actual se valida con el token endpoint (tres errores
     * bloquean la cuenta, como en el login) y la nueva se fija por la Admin REST API, que aplica la
     * politica completa, incluido el historico de las ultimas 5.
     */
    @Transactional
    public void changePassword(UUID publicId, ChangePasswordRequest request) {
        UserAccount user = require(publicId);
        String keycloakUserId = keycloakUserId(user);
        switch (keycloak.checkPassword(user.getEmail(), request.currentPassword())) {
            case VALID, PASSWORD_CHANGE_REQUIRED -> { }
            case DISABLED -> throw ApiException.forbidden("La cuenta esta inactiva");
            case INVALID_CREDENTIALS -> throw ApiException.unauthorized("La contrasena actual no coincide");
        }
        applyNewPassword(user, keycloakUserId, request);
    }

    /**
     * IAM-05: cambio sin sesión, para el primer ingreso con contraseña temporal y para la contraseña
     * vencida. Keycloak valida la actual (y cuenta los fallos para el bloqueo al 3er intento) y aplica
     * la política completa a la nueva, incluido el histórico.
     */
    @Transactional
    public void changePasswordWithoutSession(ChangePasswordRequest request) {
        UserAccount user = users.findByUsername(request.username().trim().toLowerCase(java.util.Locale.ROOT))
                .orElseThrow(() -> ApiException.unauthorized(AuthService.INVALID_CREDENTIALS));
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw ApiException.forbidden("La cuenta esta inactiva");
        }
        String keycloakUserId = keycloakUserId(user);
        switch (keycloak.checkPassword(user.getEmail(), request.currentPassword())) {
            case VALID, PASSWORD_CHANGE_REQUIRED -> { }
            case DISABLED -> throw ApiException.forbidden("La cuenta esta inactiva");
            case INVALID_CREDENTIALS -> {
                if (keycloak.isLockedOut(keycloakUserId)) {
                    accountAccess.registerLockout(user.getId());
                    throw ApiException.accountLocked();
                }
                throw ApiException.unauthorized(AuthService.INVALID_CREDENTIALS);
            }
        }
        applyNewPassword(user, keycloakUserId, request);
    }

    /** IAM-05: levanta el bloqueo al 3er intento. Antes se verifica la identidad fuera del sistema. */
    @Transactional
    public UserResponse unlock(UUID publicId, UUID actorPublicId) {
        UserAccount user = require(publicId);
        governance.checkCanManageAccount(governance.resolve(actorPublicId), user);
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw ApiException.conflict("La cuenta esta dada de baja: reactivala en lugar de desbloquearla");
        }
        keycloak.unlock(keycloakUserId(user));
        mfaChallenges.clearFailures(user.getPublicId());
        user.setLockedUntil(null);
        if (user.getStatus() == UserStatus.LOCKED) {
            user.setStatus(UserStatus.ACTIVE);
        }
        log.info("Cuenta {} desbloqueada por {}", user.getUserCode(), actorPublicId);
        record("ACCOUNT_UNLOCKED", actorPublicId, user, null, Map.of());
        return mapper.toUserResponse(user);
    }

    /**
     * IAM-05: contraseña temporal de un solo uso; el usuario la cambia en su próximo ingreso. El
     * administrador nunca conoce la definitiva. Si la cuenta ya no existe en Keycloak (por ejemplo, se
     * recreó su base), se vuelve a crear con la temporal.
     */
    @Transactional
    public TemporaryPasswordResponse resetPassword(UUID publicId, UUID actorPublicId) {
        UserAccount user = require(publicId);
        governance.checkCanManageAccount(governance.resolve(actorPublicId), user);
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw ApiException.conflict("La cuenta esta dada de baja: reactivala antes de reiniciar la contrasena");
        }
        String temporaryPassword = temporaryPasswords.generate();
        if (user.isLinkedToIdentityProvider() && keycloak.userExists(user.getKeycloakUserId())) {
            keycloak.setPassword(user.getKeycloakUserId(), temporaryPassword, true);
        } else {
            String[] names = KeycloakAccountLinker.splitName(user.getFullName());
            String keycloakUserId = keycloak.findUserIdByEmail(user.getEmail()).orElse(null);
            if (keycloakUserId == null) {
                keycloakUserId = keycloak.createUserWithPassword(new KeycloakClient.NewUser(
                        user.getPublicId().toString(), user.getEmail(), names[0], names[1], true), temporaryPassword, true);
            } else {
                keycloak.setPassword(keycloakUserId, temporaryPassword, true);
            }
            user.setKeycloakUserId(keycloakUserId);
            user.setPasswordHash(null);
        }
        user.setPasswordUpdatedAt(Instant.now());
        // IAM-07: el reinicio tambien borra el segundo factor (p. ej. telefono perdido): al entrar con la
        // temporal, la persona vuelve a registrar el QR. Por eso exige verificar su identidad antes.
        boolean hadMfa = user.isMfaEnabled();
        user.resetMfa();
        mfaChallenges.clearFailures(user.getPublicId());
        sessionStore.closeAll(user.getPublicId());
        log.info("Contrasena temporal emitida para la cuenta {} por {}", user.getUserCode(), actorPublicId);
        // La temporal nunca viaja en el evento: solo consta que se emitio y quien la pidio.
        record("PASSWORD_RESET", actorPublicId, user, null, Map.of("mfaReset", String.valueOf(hadMfa)));
        return new TemporaryPasswordResponse(user.getPublicId(), user.getUserCode(), user.getUsername(),
                temporaryPassword);
    }

    private void applyNewPassword(UserAccount user, String keycloakUserId, ChangePasswordRequest request) {
        if (request.currentPassword().equals(request.newPassword())) {
            throw ApiException.badRequest("La nueva contrasena debe ser distinta de la actual");
        }
        // Keycloak aplica notUsername sobre su username (el UUID); el usuario de MedicalSys se revisa aqui.
        if (user.getUsername() != null
                && request.newPassword().toLowerCase(java.util.Locale.ROOT).contains(user.getUsername())) {
            throw ApiException.passwordPolicy("La contrasena no puede contener tu nombre de usuario.");
        }
        try {
            // temporary = false tambien quita UPDATE_PASSWORD: el usuario ya puede entrar.
            keycloak.setPassword(keycloakUserId, request.newPassword(), false);
        } catch (PasswordPolicyException ex) {
            throw ApiException.passwordPolicy(ex.getMessage());
        }
        user.setPasswordUpdatedAt(Instant.now());
        sessionStore.closeAll(user.getPublicId());
        // Autoservicio: el actor es la propia cuenta; la contrasena nunca viaja en el evento.
        record("PASSWORD_CHANGED", user.getPublicId(), user, null, Map.of());
    }

    private String keycloakUserId(UserAccount user) {
        if (user.isLinkedToIdentityProvider()) {
            return user.getKeycloakUserId();
        }
        String linked = linker.link(user.getId())
                .orElseThrow(() -> ApiException.conflict("La cuenta no tiene credencial: solicita un reinicio de contrasena"));
        user.setKeycloakUserId(linked);
        user.setPasswordHash(null);
        return linked;
    }

    /**
     * ISO-01 (MAC): el OSI fija el nivel de autorizacion. Las sesiones abiertas se cierran porque su
     * token lleva los permisos calculados con el nivel anterior.
     */
    @Transactional
    public UserResponse changeClearance(UUID publicId, ChangeClearanceRequest request, UUID actorPublicId) {
        UserAccount user = require(publicId);
        governance.checkCanSetClearance(governance.resolve(actorPublicId), user, request.clearance());
        SecurityLevel previous = user.getClearance();
        long administrators = governance.roleAdministrators();
        user.setClearance(request.clearance());
        users.saveAndFlush(user);
        governance.checkRoleAdministrationSurvives(administrators);
        sessionStore.closeAll(user.getPublicId());
        record("CLEARANCE_CHANGED", actorPublicId, user, request.reason(),
                Map.of("previousClearance", previous.name(), "newClearance", request.clearance().name()));
        return mapper.toUserResponse(user);
    }

    @Transactional
    public UserResponse assignRole(UUID publicId, RoleAssignmentRequest request, UUID actorPublicId) {
        UserAccount user = require(publicId);
        Actor actor = governance.resolve(actorPublicId);
        governance.checkCanManageAccount(actor, user);
        attachRole(user, request, actor);
        record("ROLE_ASSIGNED", actorPublicId, user, null, Map.of("role", request.role()));
        return mapper.toUserResponse(user);
    }

    @Transactional
    public UserResponse revokeRole(UUID publicId, String roleCode, UUID actorPublicId) {
        UserAccount user = require(publicId);
        Actor actor = governance.resolve(actorPublicId);
        governance.checkCanManageAccount(actor, user);
        String code = RoleManagementService.normalize(roleCode);
        List<UserRole> revoked = user.getRoleAssignments().stream()
                .filter(assignment -> assignment.getRole().getCode().equals(code))
                .toList();
        if (revoked.isEmpty()) {
            throw ApiException.notFound("La cuenta no tiene asignado el rol " + code);
        }
        for (UserRole assignment : revoked) {
            governance.checkCanManageAssignment(actor, user, assignment.getRole(), assignment.getOrganizationPublicId());
        }
        long administrators = governance.roleAdministrators();
        user.getRoleAssignments().removeAll(revoked);
        governance.checkRoleAdministrationSurvives(administrators);
        sessionStore.closeAll(user.getPublicId());
        record("ROLE_REVOKED", actorPublicId, user, null, Map.of("role", code));
        return mapper.toUserResponse(user);
    }

    private void attachRole(UserAccount user, RoleAssignmentRequest request, Actor actor) {
        String code = RoleManagementService.normalize(request.role());
        Role role = roles.findByCode(code)
                .orElseThrow(() -> ApiException.badRequest("Rol desconocido: " + code));

        UUID organizationPublicId = role.getScope() == RoleScope.PLATFORM
                ? null
                : request.organizationPublicId() != null
                        ? request.organizationPublicId()
                        : user.getOrganizationPublicId();

        governance.checkCanManageAssignment(actor, user, role, organizationPublicId);
        if (!role.isActive()) {
            throw ApiException.conflict("El rol " + code + " esta desactivado y no se puede asignar");
        }

        boolean duplicated = user.getRoleAssignments().stream()
                .anyMatch(existing -> existing.getRole().getCode().equals(code)
                        && Objects.equals(existing.getOrganizationPublicId(), organizationPublicId)
                        && Objects.equals(existing.getBranchPublicId(), request.branchPublicId()));
        if (duplicated) {
            throw ApiException.conflict("El rol " + code + " ya esta asignado en ese alcance");
        }

        LocalDate validFrom = request.validFrom() == null ? LocalDate.now() : request.validFrom();
        if (request.validUntil() != null && request.validUntil().isBefore(validFrom)) {
            throw ApiException.badRequest("validUntil no puede ser anterior a validFrom");
        }
        governance.checkCompatible(user, role);

        user.getRoleAssignments().add(new UserRole(user, role, organizationPublicId, request.branchPublicId(),
                validFrom, request.validUntil(), actor == null ? null : actor.account()));
    }

    @Transactional(readOnly = true)
    public UserAccount require(UUID publicId) {
        UserAccount user = users.findByPublicId(publicId)
                .orElseThrow(() -> ApiException.notFound("No existe la cuenta " + publicId));
        user.getRoleAssignments().forEach(assignment -> assignment.getRole().getPermissions().size());
        return user;
    }

    /** LOG-02: evento de ABM, contrasenas o roles en la bitacora de ms-auditoria. */
    private void record(String action, UUID actorPublicId, UserAccount user, String reason, Map<String, String> metadata) {
        auditTrail.record(AuditEventDraft.userManagement(action, actorPublicId, user.getPublicId(), user.getEmail(),
                user.getOrganizationPublicId(), reason, metadata));
    }
}
