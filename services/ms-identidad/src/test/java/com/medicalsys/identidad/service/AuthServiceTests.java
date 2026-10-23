package com.medicalsys.identidad.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.medicalsys.identidad.audit.AuditEventDraft;
import com.medicalsys.identidad.audit.AuditTrail;
import com.medicalsys.identidad.domain.UserAccount;
import com.medicalsys.identidad.domain.UserStatus;
import com.medicalsys.identidad.keycloak.KeycloakClient;
import com.medicalsys.identidad.keycloak.KeycloakClient.PasswordCheck;
import com.medicalsys.identidad.repository.UserAccountRepository;
import com.medicalsys.identidad.security.IssuedToken;
import com.medicalsys.identidad.security.SessionStore;
import com.medicalsys.identidad.security.TokenService;
import com.medicalsys.identidad.web.dto.LoginRequest;
import com.medicalsys.identidad.web.dto.LoginResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

/**
 * IAM-03: los cuatro casos del login delegado a Keycloak y el enlace de cuentas antiguas. LOG-01: cada
 * desenlace deja su evento en la bitácora, sin la contraseña.
 */
class AuthServiceTests {

    private static final String EMAIL = "admin@medicalsys.demo";
    private static final String KEYCLOAK_ID = "3b043518-c564-4fbb-b35a-bbd9c7e9abb1";

    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final TokenService tokenService = mock(TokenService.class);
    private final SessionStore sessionStore = mock(SessionStore.class);
    private final IdentityMapper mapper = mock(IdentityMapper.class);
    private final AccountAccessService accountAccess = mock(AccountAccessService.class);
    private final KeycloakClient keycloak = mock(KeycloakClient.class);
    private final KeycloakAccountLinker linker = mock(KeycloakAccountLinker.class);
    private final AuditTrail auditTrail = mock(AuditTrail.class);

    private final AuthService auth = new AuthService(users, tokenService, sessionStore, mapper, accountAccess,
            keycloak, linker, auditTrail);

    private UserAccount user;

    @BeforeEach
    void setUp() {
        user = new UserAccount();
        user.setEmail(EMAIL);
        user.setFullName("Patricia Loayza Bernal");
        user.setStatus(UserStatus.ACTIVE);
        user.setKeycloakUserId(KEYCLOAK_ID);
        when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        when(mapper.activeRoleCodes(user)).thenReturn(List.of("ADMINISTRATOR"));
        when(mapper.activePermissionCodes(user)).thenReturn(List.of("identity.user.read"));
        Instant now = Instant.now();
        when(tokenService.issue(any(), any(), any()))
                .thenReturn(new IssuedToken("jwt-de-medicalsys", "jti-1", now, now.plusSeconds(3600)));
    }

    @Test
    @DisplayName("contraseña válida en Keycloak: emite el JWT propio de MedicalSys con roles y permisos")
    void validPasswordIssuesMedicalsysToken() {
        when(keycloak.checkPassword(EMAIL, "Contraseña Valida 9!")).thenReturn(PasswordCheck.VALID);

        LoginResponse response = auth.login(new LoginRequest(EMAIL, "Contraseña Valida 9!"));

        assertThat(response.accessToken()).isEqualTo("jwt-de-medicalsys");
        assertThat(response.user().roles()).containsExactly("ADMINISTRATOR");
        verify(sessionStore).open(any(), any(), any());
    }

    @Test
    @DisplayName("contraseña incorrecta: 401 genérico, sin decir si el correo existe")
    void invalidPasswordIsGeneric401() {
        when(keycloak.checkPassword(EMAIL, "incorrecta")).thenReturn(PasswordCheck.INVALID_CREDENTIALS);
        when(keycloak.isLockedOut(KEYCLOAK_ID)).thenReturn(false);

        assertThatThrownBy(() -> auth.login(new LoginRequest(EMAIL, "incorrecta")))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getMessage()).isEqualTo("Correo o contrasena incorrectos");
                });
        verify(accountAccess, never()).registerLockout(any());
    }

    @Test
    @DisplayName("correo inexistente: el mismo 401, sin consultar Keycloak")
    void unknownEmailIsSame401() {
        when(users.findByEmailIgnoreCase("nadie@medicalsys.demo")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auth.login(new LoginRequest("nadie@medicalsys.demo", "cualquiera")))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getMessage()).isEqualTo("Correo o contrasena incorrectos");
                });
        verifyNoInteractions(keycloak);
    }

    @Test
    @DisplayName("cuenta bloqueada por fuerza bruta: 423 y la cuenta local queda LOCKED")
    void lockedAccountIs423() {
        when(keycloak.checkPassword(anyString(), anyString())).thenReturn(PasswordCheck.INVALID_CREDENTIALS);
        when(keycloak.isLockedOut(KEYCLOAK_ID)).thenReturn(true);

        assertThatThrownBy(() -> auth.login(new LoginRequest(EMAIL, "Contraseña Valida 9!")))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.LOCKED);
                    assertThat(ex.getCode()).isEqualTo("ACCOUNT_LOCKED");
                });
        verify(accountAccess).registerLockout(user.getId());
    }

    @Test
    @DisplayName("contraseña vencida o temporal: 403 con PASSWORD_CHANGE_REQUIRED")
    void expiredPasswordRequiresChange() {
        when(keycloak.checkPassword(EMAIL, "Temporal Vencida 9!")).thenReturn(PasswordCheck.PASSWORD_CHANGE_REQUIRED);

        assertThatThrownBy(() -> auth.login(new LoginRequest(EMAIL, "Temporal Vencida 9!")))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getCode()).isEqualTo("PASSWORD_CHANGE_REQUIRED");
                });
        verify(tokenService, never()).issue(any(), any(), any());
    }

    @Test
    @DisplayName("cuenta inactiva: 403 sin consultar Keycloak, así no suma intentos fallidos")
    void inactiveAccountIsRejectedLocally() {
        user.setStatus(UserStatus.INACTIVE);

        assertThatThrownBy(() -> auth.login(new LoginRequest(EMAIL, "Contraseña Valida 9!")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(keycloak);
    }

    @Test
    @DisplayName("cuenta anterior a IAM-03: se enlaza en el login y el hash local se borra")
    void unlinkedAccountIsLinkedOnLogin() {
        user.setKeycloakUserId(null);
        user.setPasswordHash("$argon2id$v=19$m=16384,t=2,p=1$c2FsdA$aGFzaA");
        when(linker.link(user.getId())).thenReturn(Optional.of(KEYCLOAK_ID));
        when(keycloak.checkPassword(EMAIL, "Demo1234!")).thenReturn(PasswordCheck.VALID);

        auth.login(new LoginRequest(EMAIL, "Demo1234!"));

        assertThat(user.getKeycloakUserId()).isEqualTo(KEYCLOAK_ID);
        assertThat(user.getPasswordHash()).isNull();
    }

    @Test
    @DisplayName("cuenta sin credencial en ningún lado: 401 genérico")
    void accountWithoutCredentialIs401() {
        user.setKeycloakUserId(null);
        when(linker.link(user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auth.login(new LoginRequest(EMAIL, "Demo1234!")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(keycloak, never()).checkPassword(anyString(), anyString());
    }

    // ---- LOG-01: bitácora de autenticación ----

    @Test
    @DisplayName("bitácora: correo inexistente deja LOGIN_FAILED 401 sin actor y sin la contraseña")
    void auditsUnknownEmail() {
        when(users.findByEmailIgnoreCase("nadie@medicalsys.demo")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auth.login(new LoginRequest("nadie@medicalsys.demo", "cualquiera-9")));

        AuditEventDraft draft = captureDraft();
        assertThat(draft.action()).isEqualTo("LOGIN_FAILED");
        assertThat(draft.outcome()).isEqualTo("DENIED");
        assertThat(draft.responseStatus()).isEqualTo(401);
        assertThat(draft.actorUserId()).isNull();
        assertThat(draft.toString()).doesNotContain("cualquiera-9");
    }

    @Test
    @DisplayName("bitácora: contraseña incorrecta deja LOGIN_FAILED 401 con la cuenta como actor")
    void auditsWrongPassword() {
        when(keycloak.checkPassword(EMAIL, "incorrecta-9")).thenReturn(PasswordCheck.INVALID_CREDENTIALS);

        assertThatThrownBy(() -> auth.login(new LoginRequest(EMAIL, "incorrecta-9")));

        AuditEventDraft draft = captureDraft();
        assertThat(draft.action()).isEqualTo("LOGIN_FAILED");
        assertThat(draft.responseStatus()).isEqualTo(401);
        assertThat(draft.actorUserId()).isEqualTo(user.getPublicId());
        assertThat(draft.toString()).doesNotContain("incorrecta-9");
    }

    @Test
    @DisplayName("bitácora: el 3er intento deja ACCOUNT_LOCKED 423")
    void auditsLockout() {
        when(keycloak.checkPassword(anyString(), anyString())).thenReturn(PasswordCheck.INVALID_CREDENTIALS);
        when(keycloak.isLockedOut(KEYCLOAK_ID)).thenReturn(true);

        assertThatThrownBy(() -> auth.login(new LoginRequest(EMAIL, "incorrecta-9")));

        AuditEventDraft draft = captureDraft();
        assertThat(draft.action()).isEqualTo("ACCOUNT_LOCKED");
        assertThat(draft.responseStatus()).isEqualTo(423);
    }

    @Test
    @DisplayName("bitácora: contraseña vencida deja LOGIN_FAILED 403")
    void auditsExpiredPassword() {
        when(keycloak.checkPassword(EMAIL, "Temporal Vencida 9!")).thenReturn(PasswordCheck.PASSWORD_CHANGE_REQUIRED);

        assertThatThrownBy(() -> auth.login(new LoginRequest(EMAIL, "Temporal Vencida 9!")));

        AuditEventDraft draft = captureDraft();
        assertThat(draft.action()).isEqualTo("LOGIN_FAILED");
        assertThat(draft.responseStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("bitácora: cuenta sin credencial en Keycloak también deja LOGIN_FAILED 401")
    void auditsAccountWithoutCredential() {
        user.setKeycloakUserId(null);
        when(linker.link(user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auth.login(new LoginRequest(EMAIL, "cualquiera-9")));

        assertThat(captureDraft().action()).isEqualTo("LOGIN_FAILED");
    }

    @Test
    @DisplayName("bitácora: login correcto deja LOGIN_SUCCESS 200")
    void auditsSuccessfulLogin() {
        when(keycloak.checkPassword(EMAIL, "Contraseña Valida 9!")).thenReturn(PasswordCheck.VALID);

        auth.login(new LoginRequest(EMAIL, "Contraseña Valida 9!"));

        AuditEventDraft draft = captureDraft();
        assertThat(draft.action()).isEqualTo("LOGIN_SUCCESS");
        assertThat(draft.outcome()).isEqualTo("SUCCESS");
        assertThat(draft.responseStatus()).isEqualTo(200);
        assertThat(draft.toString()).doesNotContain("Contraseña Valida 9!");
    }

    @Test
    @DisplayName("bitácora: logout deja LOGOUT de la propia cuenta")
    void auditsLogout() {
        when(users.findByPublicId(user.getPublicId())).thenReturn(Optional.of(user));

        auth.logout("jti-1", user.getPublicId());

        AuditEventDraft draft = captureDraft();
        assertThat(draft.action()).isEqualTo("LOGOUT");
        assertThat(draft.actorUserId()).isEqualTo(user.getPublicId());
        verify(sessionStore).close("jti-1", user.getPublicId());
    }

    private AuditEventDraft captureDraft() {
        ArgumentCaptor<AuditEventDraft> captor = ArgumentCaptor.forClass(AuditEventDraft.class);
        verify(auditTrail).record(captor.capture());
        return captor.getValue();
    }
}
