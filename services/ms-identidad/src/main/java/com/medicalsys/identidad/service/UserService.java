package com.medicalsys.identidad.service;

import com.medicalsys.identidad.domain.Role;
import com.medicalsys.identidad.domain.RoleScope;
import com.medicalsys.identidad.domain.UserAccount;
import com.medicalsys.identidad.domain.UserRole;
import com.medicalsys.identidad.domain.UserStatus;
import com.medicalsys.identidad.keycloak.KeycloakClient;
import com.medicalsys.identidad.keycloak.PasswordPolicyException;
import com.medicalsys.identidad.repository.RoleRepository;
import com.medicalsys.identidad.repository.UserAccountRepository;
import com.medicalsys.identidad.security.SessionStore;
import com.medicalsys.identidad.service.RoleGovernance.Actor;
import com.medicalsys.identidad.web.dto.ChangePasswordRequest;
import com.medicalsys.identidad.web.dto.ChangeStatusRequest;
import com.medicalsys.identidad.web.dto.CreateUserRequest;
import com.medicalsys.identidad.web.dto.PageResponse;
import com.medicalsys.identidad.web.dto.RoleAssignmentRequest;
import com.medicalsys.identidad.web.dto.UpdateUserRequest;
import com.medicalsys.identidad.web.dto.UserResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserService {

    private static final String ASSIGN_PERMISSION = "identity.role.assign";

    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final IdentityMapper mapper;
    private final SessionStore sessionStore;
    private final RoleGovernance governance;
    private final KeycloakClient keycloak;
    private final KeycloakAccountLinker linker;

    public UserService(UserAccountRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                       IdentityMapper mapper, SessionStore sessionStore, RoleGovernance governance,
                       KeycloakClient keycloak, KeycloakAccountLinker linker) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.mapper = mapper;
        this.sessionStore = sessionStore;
        this.governance = governance;
        this.keycloak = keycloak;
        this.linker = linker;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request, UUID actorPublicId) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Ya existe una cuenta con el correo " + email);
        }
        Actor actor = governance.resolve(actorPublicId);

        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPhoneE164(StringUtils.hasText(request.phoneE164()) ? request.phoneE164() : null);
        user.setOrganizationPublicId(governance.organizationForNewAccount(actor, request.organizationPublicId()));
        user.setLanguage(StringUtils.hasText(request.language()) ? request.language() : "es-BO");
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setHashAlgorithm("argon2id");
        user.setPasswordUpdatedAt(Instant.now());
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
        return mapper.toUserResponse(users.save(user));
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

    @Transactional
    public UserResponse update(UUID publicId, UpdateUserRequest request, UUID actorPublicId) {
        UserAccount user = require(publicId);
        governance.checkCanManageAccount(governance.resolve(actorPublicId), user);
        if (StringUtils.hasText(request.fullName())) {
            user.setFullName(request.fullName().trim());
        }
        if (request.phoneE164() != null) {
            user.setPhoneE164(StringUtils.hasText(request.phoneE164()) ? request.phoneE164() : null);
        }
        if (StringUtils.hasText(request.language())) {
            user.setLanguage(request.language());
        }
        return mapper.toUserResponse(user);
    }

    @Transactional
    public UserResponse changeStatus(UUID publicId, ChangeStatusRequest request, UUID actorPublicId) {
        UserAccount user = require(publicId);
        governance.checkCanManageAccount(governance.resolve(actorPublicId), user);
        UserStatus target = request.status();
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
        }
        user.setStatus(target);
        if (target != UserStatus.ACTIVE) {
            governance.checkRoleAdministrationSurvives(administrators);
            sessionStore.closeAll(user.getPublicId());
        }
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
        if (request.currentPassword().equals(request.newPassword())) {
            throw ApiException.badRequest("La nueva contrasena debe ser distinta de la actual");
        }
        try {
            keycloak.setPassword(keycloakUserId, request.newPassword(), false);
        } catch (PasswordPolicyException ex) {
            throw ApiException.passwordPolicy(ex.getMessage());
        }
        user.setPasswordUpdatedAt(Instant.now());
        sessionStore.closeAll(user.getPublicId());
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

    @Transactional
    public UserResponse assignRole(UUID publicId, RoleAssignmentRequest request, UUID actorPublicId) {
        UserAccount user = require(publicId);
        Actor actor = governance.resolve(actorPublicId);
        governance.checkCanManageAccount(actor, user);
        attachRole(user, request, actor);
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
}
