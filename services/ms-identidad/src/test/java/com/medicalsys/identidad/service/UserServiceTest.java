package com.medicalsys.identidad.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.medicalsys.identidad.audit.AuditEventDraft;
import com.medicalsys.identidad.audit.AuditTrail;
import com.medicalsys.identidad.domain.Role;
import com.medicalsys.identidad.domain.RoleCode;
import com.medicalsys.identidad.domain.UserAccount;
import com.medicalsys.identidad.domain.UserStatus;
import com.medicalsys.identidad.repository.RoleRepository;
import com.medicalsys.identidad.repository.UserAccountRepository;
import com.medicalsys.identidad.security.SessionStore;
import com.medicalsys.identidad.web.dto.ChangePasswordRequest;
import com.medicalsys.identidad.web.dto.ChangeStatusRequest;
import com.medicalsys.identidad.web.dto.CreateUserRequest;
import com.medicalsys.identidad.web.dto.RoleAssignmentRequest;
import com.medicalsys.identidad.web.dto.UpdateUserRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * LOG-02: cada operacion de ABM de usuarios y roles debe dejar un evento con el actor (quien hace
 * la operacion) y el objetivo (la cuenta o el rol afectado).
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserAccountRepository users;
    @Mock
    private RoleRepository roles;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SessionStore sessionStore;
    @Mock
    private AuditTrail auditTrail;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(users, roles, passwordEncoder, new IdentityMapper(), sessionStore, auditTrail);
    }

    @AfterEach
    void clearActor() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordsUserCreatedWithTheAdminAsActor() {
        UUID actorId = UUID.randomUUID();
        when(users.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(new CreateUserRequest("new@example.com", "Nueva Cuenta", "Password123!", null, null, null,
                true, null), actorId);

        AuditEventDraft draft = captureDraft();
        assertEquals("USER_CREATED", draft.action());
        assertEquals(actorId, draft.actorUserId());
        assertEquals("new@example.com", draft.resourceLabel());
    }

    @Test
    void recordsUserUpdatedUsingTheAuthenticatedActor() {
        UUID publicId = UUID.randomUUID();
        UUID actorId = authenticateAs(UUID.randomUUID());
        when(users.findByPublicId(publicId)).thenReturn(Optional.of(targetAccount(publicId, UserStatus.ACTIVE)));

        service.update(publicId, new UpdateUserRequest("Nuevo Nombre", null, null));

        AuditEventDraft draft = captureDraft();
        assertEquals("USER_UPDATED", draft.action());
        assertEquals(actorId, draft.actorUserId());
        assertEquals(publicId, draft.resourcePublicId());
    }

    @Test
    void recordsUserDisabledWhenStatusMovesAwayFromActive() {
        UUID publicId = UUID.randomUUID();
        authenticateAs(UUID.randomUUID());
        when(users.findByPublicId(publicId)).thenReturn(Optional.of(targetAccount(publicId, UserStatus.ACTIVE)));

        service.changeStatus(publicId, new ChangeStatusRequest(UserStatus.INACTIVE, "Baja solicitada"));

        AuditEventDraft draft = captureDraft();
        assertEquals("USER_DISABLED", draft.action());
        assertEquals("Baja solicitada", draft.reason());
    }

    @Test
    void recordsAccountUnlockedWhenALockedAccountIsReactivated() {
        UUID publicId = UUID.randomUUID();
        authenticateAs(UUID.randomUUID());
        when(users.findByPublicId(publicId)).thenReturn(Optional.of(targetAccount(publicId, UserStatus.LOCKED)));

        service.changeStatus(publicId, new ChangeStatusRequest(UserStatus.ACTIVE, null));

        AuditEventDraft draft = captureDraft();
        assertEquals("ACCOUNT_UNLOCKED", draft.action());
    }

    @Test
    void recordsPasswordChangedWithTheOwnAccountAsActor() {
        UUID publicId = UUID.randomUUID();
        UserAccount account = targetAccount(publicId, UserStatus.ACTIVE);
        when(users.findByPublicId(publicId)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("current-pass", account.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.matches("new-password", account.getPasswordHash())).thenReturn(false);

        service.changePassword(publicId, new ChangePasswordRequest("current-pass", "new-password"));

        AuditEventDraft draft = captureDraft();
        assertEquals("PASSWORD_CHANGED", draft.action());
        assertEquals(publicId, draft.actorUserId());
        assertFalse(bodyContains(draft, "new-password"));
    }

    @Test
    void recordsRoleAssignedWithTheTargetRoleInMetadata() {
        UUID publicId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UserAccount target = targetAccount(publicId, UserStatus.ACTIVE);
        when(users.findByPublicId(publicId)).thenReturn(Optional.of(target));
        Role role = new Role();
        when(roles.findByCode(RoleCode.RECEPTIONIST)).thenReturn(Optional.of(role));
        try {
            java.lang.reflect.Field codeField = Role.class.getDeclaredField("code");
            codeField.setAccessible(true);
            codeField.set(role, RoleCode.RECEPTIONIST);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }

        service.assignRole(publicId, new RoleAssignmentRequest(RoleCode.RECEPTIONIST, null, null, null, null),
                actorId);

        AuditEventDraft draft = captureDraft();
        assertEquals("ROLE_ASSIGNED", draft.action());
        assertEquals(actorId, draft.actorUserId());
        assertEquals("RECEPTIONIST", draft.metadata().get("role"));
    }

    @Test
    void recordsRoleRevokedForTheTargetAccount() {
        UUID publicId = UUID.randomUUID();
        UUID actorId = authenticateAs(UUID.randomUUID());
        UserAccount target = targetAccount(publicId, UserStatus.ACTIVE);
        Role role = new Role();
        try {
            java.lang.reflect.Field codeField = Role.class.getDeclaredField("code");
            codeField.setAccessible(true);
            codeField.set(role, RoleCode.RECEPTIONIST);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        target.getRoleAssignments().add(new com.medicalsys.identidad.domain.UserRole(target, role, null, null,
                java.time.LocalDate.now(), null, null));
        when(users.findByPublicId(publicId)).thenReturn(Optional.of(target));

        service.revokeRole(publicId, RoleCode.RECEPTIONIST);

        AuditEventDraft draft = captureDraft();
        assertEquals("ROLE_REVOKED", draft.action());
        assertEquals(actorId, draft.actorUserId());
        assertEquals("RECEPTIONIST", draft.metadata().get("role"));
    }

    private UUID authenticateAs(UUID subject) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(subject.toString())
                .claim("sub", subject.toString())
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(jwt, "token", List.of()));
        return subject;
    }

    private UserAccount targetAccount(UUID publicId, UserStatus status) {
        UserAccount account = new UserAccount();
        account.setPublicId(publicId);
        account.setEmail("target@example.com");
        account.setFullName("Cuenta Objetivo");
        account.setPasswordHash("hashed-password");
        account.setStatus(status);
        account.setOrganizationPublicId(UUID.randomUUID());
        return account;
    }

    private AuditEventDraft captureDraft() {
        ArgumentCaptor<AuditEventDraft> captor = ArgumentCaptor.forClass(AuditEventDraft.class);
        org.mockito.Mockito.verify(auditTrail).record(captor.capture());
        return captor.getValue();
    }

    private boolean bodyContains(AuditEventDraft draft, String secret) {
        return (draft.reason() != null && draft.reason().contains(secret))
                || (draft.metadata() != null && draft.metadata().values().stream().anyMatch(v -> v.contains(secret)));
    }

    private void assertFalse(boolean value) {
        org.junit.jupiter.api.Assertions.assertFalse(value);
    }
}
