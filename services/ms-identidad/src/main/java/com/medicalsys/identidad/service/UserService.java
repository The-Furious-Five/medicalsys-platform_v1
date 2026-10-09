package com.medicalsys.identidad.service;

import com.medicalsys.identidad.domain.Role;
import com.medicalsys.identidad.domain.RoleCode;
import com.medicalsys.identidad.domain.RoleScope;
import com.medicalsys.identidad.domain.UserAccount;
import com.medicalsys.identidad.domain.UserRole;
import com.medicalsys.identidad.domain.UserStatus;
import com.medicalsys.identidad.repository.RoleRepository;
import com.medicalsys.identidad.repository.UserAccountRepository;
import com.medicalsys.identidad.security.SessionStore;
import com.medicalsys.identidad.web.dto.ChangePasswordRequest;
import com.medicalsys.identidad.web.dto.ChangeStatusRequest;
import com.medicalsys.identidad.web.dto.CreateUserRequest;
import com.medicalsys.identidad.web.dto.PageResponse;
import com.medicalsys.identidad.web.dto.RoleAssignmentRequest;
import com.medicalsys.identidad.web.dto.UpdateUserRequest;
import com.medicalsys.identidad.web.dto.UserResponse;
import java.time.Instant;
import java.time.LocalDate;
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

    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final IdentityMapper mapper;
    private final SessionStore sessionStore;

    public UserService(UserAccountRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                       IdentityMapper mapper, SessionStore sessionStore) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.mapper = mapper;
        this.sessionStore = sessionStore;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request, UUID actorPublicId) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Ya existe una cuenta con el correo " + email);
        }

        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPhoneE164(StringUtils.hasText(request.phoneE164()) ? request.phoneE164() : null);
        user.setOrganizationPublicId(request.organizationPublicId());
        user.setLanguage(StringUtils.hasText(request.language()) ? request.language() : "es-BO");
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setHashAlgorithm("argon2id");
        user.setPasswordUpdatedAt(Instant.now());
        user.setStatus(Boolean.TRUE.equals(request.activateImmediately())
                ? UserStatus.ACTIVE
                : UserStatus.PENDING_ACTIVATION);

        UserAccount actor = actorPublicId == null ? null : users.findByPublicId(actorPublicId).orElse(null);
        if (request.roles() != null) {
            for (RoleAssignmentRequest assignment : request.roles()) {
                attachRole(user, assignment, actor);
            }
        }
        return mapper.toUserResponse(users.save(user));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(UUID organizationPublicId, UserStatus status, String query,
                                             Pageable pageable) {
        String search = StringUtils.hasText(query) ? "%" + query.trim().toLowerCase() + "%" : null;
        Page<UserAccount> page = users.search(organizationPublicId, status, search, pageable);
        page.getContent().forEach(user -> user.getRoleAssignments().size());
        return PageResponse.of(page, mapper::toUserResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID publicId) {
        return mapper.toUserResponse(require(publicId));
    }

    @Transactional
    public UserResponse update(UUID publicId, UpdateUserRequest request) {
        UserAccount user = require(publicId);
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
    public UserResponse changeStatus(UUID publicId, ChangeStatusRequest request) {
        UserAccount user = require(publicId);
        UserStatus target = request.status();
        if (target == UserStatus.LOCKED && user.getLockedUntil() == null) {
            user.setLockedUntil(Instant.now().plusSeconds(3600));
        }
        if (target == UserStatus.ACTIVE) {
            user.setLockedUntil(null);
        }
        user.setStatus(target);
        if (target != UserStatus.ACTIVE) {
            sessionStore.closeAll(user.getPublicId());
        }
        return mapper.toUserResponse(user);
    }

    @Transactional
    public void changePassword(UUID publicId, ChangePasswordRequest request) {
        UserAccount user = require(publicId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.unauthorized("La contrasena actual no coincide");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("La nueva contrasena debe ser distinta de la actual");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setHashAlgorithm("argon2id");
        user.setPasswordUpdatedAt(Instant.now());
        sessionStore.closeAll(user.getPublicId());
    }

    @Transactional
    public UserResponse assignRole(UUID publicId, RoleAssignmentRequest request, UUID actorPublicId) {
        UserAccount user = require(publicId);
        UserAccount actor = actorPublicId == null ? null : users.findByPublicId(actorPublicId).orElse(null);
        attachRole(user, request, actor);
        return mapper.toUserResponse(user);
    }

    @Transactional
    public UserResponse revokeRole(UUID publicId, RoleCode roleCode) {
        UserAccount user = require(publicId);
        boolean removed = user.getRoleAssignments()
                .removeIf(assignment -> assignment.getRole().getCode() == roleCode);
        if (!removed) {
            throw ApiException.notFound("La cuenta no tiene asignado el rol " + roleCode);
        }
        sessionStore.closeAll(user.getPublicId());
        return mapper.toUserResponse(user);
    }

    private void attachRole(UserAccount user, RoleAssignmentRequest request, UserAccount actor) {
        Role role = roles.findByCode(request.role())
                .orElseThrow(() -> ApiException.badRequest("Rol desconocido: " + request.role()));

        UUID organizationPublicId = role.getScope() == RoleScope.PLATFORM
                ? null
                : request.organizationPublicId() != null
                        ? request.organizationPublicId()
                        : user.getOrganizationPublicId();

        boolean duplicated = user.getRoleAssignments().stream()
                .anyMatch(existing -> existing.getRole().getCode() == request.role()
                        && Objects.equals(existing.getOrganizationPublicId(), organizationPublicId)
                        && Objects.equals(existing.getBranchPublicId(), request.branchPublicId()));
        if (duplicated) {
            throw ApiException.conflict("El rol " + request.role() + " ya esta asignado en ese alcance");
        }

        LocalDate validFrom = request.validFrom() == null ? LocalDate.now() : request.validFrom();
        if (request.validUntil() != null && request.validUntil().isBefore(validFrom)) {
            throw ApiException.badRequest("validUntil no puede ser anterior a validFrom");
        }

        user.getRoleAssignments().add(new UserRole(user, role, organizationPublicId, request.branchPublicId(),
                validFrom, request.validUntil(), actor));
    }

    @Transactional(readOnly = true)
    public UserAccount require(UUID publicId) {
        UserAccount user = users.findByPublicId(publicId)
                .orElseThrow(() -> ApiException.notFound("No existe la cuenta " + publicId));
        user.getRoleAssignments().forEach(assignment -> assignment.getRole().getPermissions().size());
        return user;
    }
}
