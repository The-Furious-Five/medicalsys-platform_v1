package com.medicalsys.identidad.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.medicalsys.identidad.audit.AuditEventDraft;
import com.medicalsys.identidad.audit.AuditTrail;
import com.medicalsys.identidad.config.IdentityProperties;
import com.medicalsys.identidad.domain.UserAccount;
import com.medicalsys.identidad.domain.UserStatus;
import com.medicalsys.identidad.repository.UserAccountRepository;
import com.medicalsys.identidad.security.IssuedToken;
import com.medicalsys.identidad.security.SessionStore;
import com.medicalsys.identidad.security.TokenService;
import com.medicalsys.identidad.web.dto.LoginRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * LOG-01: cada desenlace de {@code AuthService.login} debe dejar un evento de auditoria con el
 * actor, el resultado y el motivo correctos, sin que una falla de la bitacora cambie el
 * comportamiento del login.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserAccountRepository users;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenService tokenService;
    @Mock
    private SessionStore sessionStore;
    @Mock
    private IdentityMapper mapper;
    @Mock
    private IdentityProperties properties;
    @Mock
    private AccountAccessService accountAccess;
    @Mock
    private AuditTrail auditTrail;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, passwordEncoder, tokenService, sessionStore, mapper, properties,
                accountAccess, auditTrail);
    }

    @Test
    void recordsLoginFailedWhenTheEmailDoesNotExist() {
        when(users.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> service.login(new LoginRequest("nobody@example.com", "whatever1")));

        AuditEventDraft draft = captureDraft();
        assertEquals("LOGIN_FAILED", draft.action());
        assertEquals("DENIED", draft.outcome());
        assertEquals(401, draft.responseStatus());
        assertEquals(null, draft.actorUserId());
        assertFalse(bodyContains(draft, "whatever1"));
    }

    @Test
    void recordsAccountLockedWithTheAccountAsActor() {
        UserAccount locked = account(UserStatus.LOCKED);
        locked.setLockedUntil(Instant.now().plusSeconds(600));
        when(users.findByEmailIgnoreCase("locked@example.com")).thenReturn(Optional.of(locked));

        assertThrows(ApiException.class, () -> service.login(new LoginRequest("locked@example.com", "whatever1")));

        AuditEventDraft draft = captureDraft();
        assertEquals("ACCOUNT_LOCKED", draft.action());
        assertEquals(423, draft.responseStatus());
        assertEquals(locked.getPublicId(), draft.actorUserId());
    }

    @Test
    void recordsLoginFailedOnWrongPassword() {
        UserAccount user = account(UserStatus.ACTIVE);
        when(users.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", user.getPasswordHash())).thenReturn(false);
        when(accountAccess.registerFailedAttempt(any())).thenReturn(1);

        assertThrows(ApiException.class, () -> service.login(new LoginRequest("user@example.com", "wrong-password")));

        AuditEventDraft draft = captureDraft();
        assertEquals("LOGIN_FAILED", draft.action());
        assertEquals(user.getPublicId(), draft.actorUserId());
        assertFalse(bodyContains(draft, "wrong-password"));
    }

    @Test
    void recordsLoginSuccessOnValidCredentials() {
        UserAccount user = account(UserStatus.ACTIVE);
        when(users.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", user.getPasswordHash())).thenReturn(true);
        when(mapper.activeRoleCodes(user)).thenReturn(List.of("ADMIN"));
        when(mapper.activePermissionCodes(user)).thenReturn(List.of());
        IssuedToken token = new IssuedToken("jwt-value", "jti-1", Instant.now(), Instant.now().plusSeconds(3600));
        when(tokenService.issue(any(), any(), any())).thenReturn(token);

        service.login(new LoginRequest("user@example.com", "correct-password"));

        AuditEventDraft draft = captureDraft();
        assertEquals("LOGIN_SUCCESS", draft.action());
        assertEquals("SUCCESS", draft.outcome());
        assertEquals(user.getPublicId(), draft.actorUserId());
    }

    @Test
    void recordsLogoutForTheRequestingAccount() {
        UUID publicId = UUID.randomUUID();
        UserAccount user = account(UserStatus.ACTIVE);
        user.setPublicId(publicId);
        when(users.findByPublicId(publicId)).thenReturn(Optional.of(user));

        service.logout("jti-1", publicId);

        AuditEventDraft draft = captureDraft();
        assertEquals("LOGOUT", draft.action());
        assertEquals(publicId, draft.actorUserId());
    }

    private AuditEventDraft captureDraft() {
        ArgumentCaptor<AuditEventDraft> captor = ArgumentCaptor.forClass(AuditEventDraft.class);
        org.mockito.Mockito.verify(auditTrail).record(captor.capture());
        return captor.getValue();
    }

    /** El evento jamas debe llevar la contrasena ni el token, ni siquiera dentro de metadata. */
    private boolean bodyContains(AuditEventDraft draft, String secret) {
        return (draft.reason() != null && draft.reason().contains(secret))
                || (draft.metadata() != null && draft.metadata().values().stream().anyMatch(v -> v.contains(secret)));
    }

    private void assertFalse(boolean value) {
        org.junit.jupiter.api.Assertions.assertFalse(value);
    }

    private UserAccount account(UserStatus status) {
        UserAccount user = new UserAccount();
        user.setEmail("user@example.com");
        user.setFullName("Usuaria de prueba");
        user.setPasswordHash("hashed-password");
        user.setStatus(status);
        user.setOrganizationPublicId(UUID.randomUUID());
        return user;
    }
}
