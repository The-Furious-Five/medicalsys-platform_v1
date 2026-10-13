package com.medicalsys.identidad.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalsys.identidad.config.IdentityProperties;
import com.medicalsys.identidad.domain.UserAccount;
import com.medicalsys.identidad.domain.UserStatus;
import com.medicalsys.identidad.repository.UserAccountRepository;
import com.medicalsys.identidad.security.IssuedToken;
import com.medicalsys.identidad.security.KeycloakClient.PasswordCheck;
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
import org.springframework.http.HttpStatus;

/** Login delegado a Keycloak: los cuatro desenlaces de la verificacion de contrasena. */
class AuthServiceTests {

    private static final String EMAIL = "doctor@medicalsys.demo";
    private static final String PASSWORD = "Demo1234!";

    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final CredentialService credentials = mock(CredentialService.class);
    private final TokenService tokenService = mock(TokenService.class);
    private final SessionStore sessionStore = mock(SessionStore.class);
    private final IdentityMapper mapper = mock(IdentityMapper.class);
    private final AccountAccessService accountAccess = mock(AccountAccessService.class);

    private final AuthService authService = new AuthService(users, credentials, tokenService, sessionStore, mapper,
            new IdentityProperties(), accountAccess);

    private UserAccount user;

    @BeforeEach
    void setUp() {
        user = new UserAccount();
        user.setEmail(EMAIL);
        user.setFullName("Ricardo Salazar Peredo");
        user.setStatus(UserStatus.ACTIVE);
        user.setHashAlgorithm(CredentialService.KEYCLOAK_ALGORITHM);
        when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        when(mapper.activeRoleCodes(user)).thenReturn(List.of("PHYSICIAN"));
        when(mapper.activePermissionCodes(user)).thenReturn(List.of("medical-record.read"));
    }

    @Test
    @DisplayName("credenciales validas en Keycloak: emite el JWT propio y abre la sesion")
    void validCredentialsIssueMedicalSysToken() {
        Instant now = Instant.now();
        when(credentials.verify(user, PASSWORD)).thenReturn(PasswordCheck.VALID);
        when(tokenService.issue(any(), any(), any()))
                .thenReturn(new IssuedToken("jwt-medicalsys", "jti-1", now, now.plusSeconds(3600)));

        LoginResponse response = authService.login(new LoginRequest(EMAIL, PASSWORD));

        assertThat(response.accessToken()).isEqualTo("jwt-medicalsys");
        assertThat(response.user().roles()).containsExactly("PHYSICIAN");
        verify(sessionStore).open("jti-1", user.getPublicId(), now.plusSeconds(3600));
        assertThat(user.getLastAccessAt()).isNotNull();
    }

    @Test
    @DisplayName("contrasena invalida: 401 generico y se registra el intento fallido")
    void invalidCredentialsAreGeneric401() {
        when(credentials.verify(user, "otra-clave")).thenReturn(PasswordCheck.INVALID);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "otra-clave")))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getMessage()).isEqualTo("Correo o contrasena incorrectos");
                });
        verify(accountAccess).registerFailedAttempt(user.getId());
        verify(tokenService, never()).issue(any(), any(), any());
    }

    @Test
    @DisplayName("correo inexistente: el mismo 401 que una contrasena incorrecta")
    void unknownEmailIsSame401() {
        when(users.findByEmailIgnoreCase("nadie@medicalsys.demo")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nadie@medicalsys.demo", PASSWORD)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getMessage()).isEqualTo("Correo o contrasena incorrectos");
                });
    }

    @Test
    @DisplayName("cuenta bloqueada en Keycloak: 423")
    void lockedAccountIs423() {
        when(credentials.verify(user, PASSWORD)).thenReturn(PasswordCheck.LOCKED);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD)))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.LOCKED));
        verify(tokenService, never()).issue(any(), any(), any());
    }

    @Test
    @DisplayName("contrasena vencida o temporal: 403 con PASSWORD_CHANGE_REQUIRED")
    void expiredPasswordRequiresChange() {
        when(credentials.verify(user, PASSWORD)).thenReturn(PasswordCheck.PASSWORD_CHANGE_REQUIRED);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getCode()).isEqualTo("PASSWORD_CHANGE_REQUIRED");
                });
        verify(accountAccess, never()).registerFailedAttempt(any());
    }
}
