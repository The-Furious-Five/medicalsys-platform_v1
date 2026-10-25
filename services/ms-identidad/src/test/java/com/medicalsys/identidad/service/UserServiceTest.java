package com.medicalsys.identidad.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalsys.identidad.audit.AuditEventDraft;
import com.medicalsys.identidad.audit.AuditTrail;
import com.medicalsys.identidad.domain.UserAccount;
import com.medicalsys.identidad.domain.UserStatus;
import com.medicalsys.identidad.keycloak.KeycloakClient;
import com.medicalsys.identidad.keycloak.KeycloakClient.PasswordCheck;
import com.medicalsys.identidad.keycloak.PasswordPolicyException;
import com.medicalsys.identidad.repository.RoleRepository;
import com.medicalsys.identidad.repository.UserAccountRepository;
import com.medicalsys.identidad.security.SessionStore;
import com.medicalsys.identidad.security.mfa.MfaChallengeStore;
import com.medicalsys.identidad.web.dto.ChangePasswordRequest;
import com.medicalsys.identidad.web.dto.ChangeStatusRequest;
import com.medicalsys.identidad.web.dto.CreateUserRequest;
import com.medicalsys.identidad.web.dto.TemporaryPasswordResponse;
import com.medicalsys.identidad.web.dto.UpdateUserRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * LOG-02: cada operación de ABM, contraseñas y desbloqueo sobre una cuenta deja su evento en la
 * bitácora, con el actor correcto y sin la contraseña en ningún campo.
 */
class UserServiceTest {

    private static final String KEYCLOAK_ID = "3b043518-c564-4fbb-b35a-bbd9c7e9abb1";
    private static final UUID ADMIN = UUID.fromString("00000000-0000-4000-8000-000000000901");

    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final SessionStore sessionStore = mock(SessionStore.class);
    private final RoleGovernance governance = mock(RoleGovernance.class);
    private final KeycloakClient keycloak = mock(KeycloakClient.class);
    private final TemporaryPasswordGenerator temporaryPasswords = mock(TemporaryPasswordGenerator.class);
    private final AuditTrail auditTrail = mock(AuditTrail.class);

    private final UserCodeGenerator userCodes = mock(UserCodeGenerator.class);
    private final MfaChallengeStore mfaChallenges = mock(MfaChallengeStore.class);

    private final UserService service = new UserService(users, mock(RoleRepository.class),
            mock(IdentityMapper.class), sessionStore, governance, keycloak,
            mock(KeycloakAccountLinker.class), userCodes, temporaryPasswords,
            mock(AccountAccessService.class), auditTrail, mock(UsernameGenerator.class), mfaChallenges);

    private UserAccount target;

    @BeforeEach
    void setUp() {
        target = new UserAccount();
        target.setEmail("lucia.quispe@medicalsys.demo");
        target.setFullName("Lucia Quispe Mamani");
        target.setUserCode("MS-26-00012");
        target.setStatus(UserStatus.ACTIVE);
        target.setKeycloakUserId(KEYCLOAK_ID);
        when(users.findByPublicId(target.getPublicId())).thenReturn(Optional.of(target));
    }

    @Test
    @DisplayName("modificación: USER_UPDATED con el administrador como actor")
    void recordsUserUpdatedWithTheAdminAsActor() {
        service.update(target.getPublicId(), new UpdateUserRequest("Lucia Quispe de Rojas", null, null, null), ADMIN);

        AuditEventDraft draft = captureDraft();
        assertThat(draft.action()).isEqualTo("USER_UPDATED");
        assertThat(draft.category()).isEqualTo("CONFIGURATION");
        assertThat(draft.actorUserId()).isEqualTo(ADMIN);
        assertThat(draft.resourcePublicId()).isEqualTo(target.getPublicId());
    }

    @Test
    @DisplayName("baja: USER_DISABLED con el estado anterior y el nuevo, y la cuenta deshabilitada en Keycloak")
    void recordsUserDisabledWhenStatusMovesAwayFromActive() {
        service.changeStatus(target.getPublicId(), new ChangeStatusRequest(UserStatus.INACTIVE, "Desvinculación"), ADMIN);

        AuditEventDraft draft = captureDraft();
        assertThat(draft.action()).isEqualTo("USER_DISABLED");
        assertThat(draft.reason()).isEqualTo("Desvinculación");
        assertThat(draft.metadata()).containsEntry("previousStatus", "ACTIVE").containsEntry("newStatus", "INACTIVE");
        verify(keycloak).setEnabled(KEYCLOAK_ID, false);
    }

    @Test
    @DisplayName("reactivar una cuenta bloqueada: ACCOUNT_UNLOCKED")
    void recordsAccountUnlockedWhenALockedAccountIsReactivated() {
        target.setStatus(UserStatus.LOCKED);

        service.changeStatus(target.getPublicId(), new ChangeStatusRequest(UserStatus.ACTIVE, null), ADMIN);

        assertThat(captureDraft().action()).isEqualTo("ACCOUNT_UNLOCKED");
        verify(keycloak).unlock(KEYCLOAK_ID);
    }

    @Test
    @DisplayName("IAM-05 desbloqueo: ACCOUNT_UNLOCKED con el administrador que lo ejecutó")
    void recordsAccountUnlockedOnUnlock() {
        service.unlock(target.getPublicId(), ADMIN);

        AuditEventDraft draft = captureDraft();
        assertThat(draft.action()).isEqualTo("ACCOUNT_UNLOCKED");
        assertThat(draft.actorUserId()).isEqualTo(ADMIN);
        verify(keycloak).unlock(KEYCLOAK_ID);
    }

    @Test
    @DisplayName("IAM-05 reinicio: PASSWORD_RESET sin la temporal y con el segundo factor borrado")
    void recordsPasswordResetWithoutTheTemporaryPassword() {
        when(temporaryPasswords.generate()).thenReturn("Kq7m-Xw3p-Rt9v-Hn4c");
        when(keycloak.userExists(KEYCLOAK_ID)).thenReturn(true);
        target.enableMfa(7L, java.time.Instant.now());

        TemporaryPasswordResponse response = service.resetPassword(target.getPublicId(), ADMIN);

        // IAM-07: el reinicio tambien borra el segundo factor; al entrar se vuelve a registrar el QR.
        assertThat(target.isMfaEnabled()).isFalse();
        assertThat(target.getEncryptedMfaSecret()).isNull();

        assertThat(response.temporaryPassword()).isEqualTo("Kq7m-Xw3p-Rt9v-Hn4c");
        AuditEventDraft draft = captureDraft();
        assertThat(draft.action()).isEqualTo("PASSWORD_RESET");
        assertThat(draft.actorUserId()).isEqualTo(ADMIN);
        assertThat(draft.toString()).doesNotContain("Kq7m-Xw3p-Rt9v-Hn4c");
        verify(keycloak).setPassword(KEYCLOAK_ID, "Kq7m-Xw3p-Rt9v-Hn4c", true);
    }

    @Test
    @DisplayName("cambio de contraseña: PASSWORD_CHANGED con la propia cuenta como actor y sin contraseñas")
    void recordsPasswordChangedWithTheOwnAccountAsActor() {
        when(keycloak.checkPassword(target.getEmail(), "Clave Actual 2026!")).thenReturn(PasswordCheck.VALID);

        service.changePassword(target.getPublicId(),
                new ChangePasswordRequest(null, "Clave Actual 2026!", "Clave Nueva 2026!"));

        AuditEventDraft draft = captureDraft();
        assertThat(draft.action()).isEqualTo("PASSWORD_CHANGED");
        assertThat(draft.actorUserId()).isEqualTo(target.getPublicId());
        assertThat(draft.toString()).doesNotContain("Clave Actual 2026!").doesNotContain("Clave Nueva 2026!");
        verify(keycloak).setPassword(eq(KEYCLOAK_ID), eq("Clave Nueva 2026!"), anyBoolean());
    }

    @Test
    @DisplayName("contraseña actual incorrecta: 401 y no se registra un cambio que no ocurrió")
    void wrongCurrentPasswordRecordsNothing() {
        when(keycloak.checkPassword(anyString(), anyString())).thenReturn(PasswordCheck.INVALID_CREDENTIALS);

        assertThatThrownBy(() -> service.changePassword(target.getPublicId(),
                new ChangePasswordRequest(null, "incorrecta", "Clave Nueva 2026!")))
                .isInstanceOf(ApiException.class);

        verify(auditTrail, never()).record(any());
    }

    @Test
    @DisplayName("cuentas demo: cada una nace en Keycloak con su propia temporal, con cambio obligatorio")
    void seedAccountsGetTheirOwnTemporaryPassword() {
        when(userCodes.next()).thenReturn(new UserCodeGenerator.IssuedCode(1, "MS-26-00001"),
                new UserCodeGenerator.IssuedCode(2, "MS-26-00002"));
        when(temporaryPasswords.generate()).thenReturn("Kq7m-Xw3p-Rt9v-Hn4c", "Pz4n-Hb8s-Wd2k-Ym6f");
        when(keycloak.createUserWithPassword(any(), anyString(), anyBoolean())).thenReturn(KEYCLOAK_ID);
        UUID doctor = UUID.fromString("00000000-0000-4000-8000-000000000903");

        service.createSeedAccount(seedRequest("doctor@medicalsys.demo", null), doctor);
        service.createSeedAccount(seedRequest("caja@medicalsys.demo", null), UUID.randomUUID());

        ArgumentCaptor<KeycloakClient.NewUser> created = ArgumentCaptor.forClass(KeycloakClient.NewUser.class);
        verify(keycloak).createUserWithPassword(created.capture(), eq("Kq7m-Xw3p-Rt9v-Hn4c"), eq(true));
        verify(keycloak).createUserWithPassword(any(), eq("Pz4n-Hb8s-Wd2k-Ym6f"), eq(true));
        // El usuario de Keycloak es el identificador fijo, no uno sorteado.
        assertThat(created.getValue().username()).isEqualTo(doctor.toString());
        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(users, times(2)).saveAndFlush(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(user -> assertThat(user.getPasswordHash()).isNull());
    }

    @Test
    @DisplayName("cuentas de arranque: la contraseña configurada va a Keycloak, que aplica la política")
    void bootstrapAccountsUseTheConfiguredPasswordThroughThePolicy() {
        when(userCodes.next()).thenReturn(new UserCodeGenerator.IssuedCode(1, "MS-26-00001"));
        when(keycloak.createUserWithPassword(any(), anyString(), anyBoolean()))
                .thenThrow(new PasswordPolicyException("invalidPasswordMinLengthMessage"));

        assertThatThrownBy(() -> service.createSeedAccount(seedRequest("osi@medicalsys.demo", "corta"), UUID.randomUUID()))
                .isInstanceOf(ApiException.class);
        verify(keycloak).createUserWithPassword(any(), eq("corta"), eq(false));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("rotación demo: temporal nueva, sesiones cerradas y PASSWORD_RESET sin la contraseña")
    void rotationGivesANewTemporaryPasswordAndClosesSessions() {
        when(keycloak.userExists(KEYCLOAK_ID)).thenReturn(true);
        when(temporaryPasswords.generate()).thenReturn("Kq7m-Xw3p-Rt9v-Hn4c");

        service.rotateSeedPassword(target);

        verify(keycloak).setPassword(KEYCLOAK_ID, "Kq7m-Xw3p-Rt9v-Hn4c", true);
        verify(sessionStore).closeAll(target.getPublicId());
        AuditEventDraft draft = captureDraft();
        assertThat(draft.action()).isEqualTo("PASSWORD_RESET");
        assertThat(draft.actorUserId()).isNull();
        assertThat(draft.toString()).doesNotContain("Kq7m-Xw3p-Rt9v-Hn4c");
    }

    @Test
    @DisplayName("arranque normal: una cuenta sembrada que ya está en Keycloak no se toca")
    void existingSeedCredentialIsLeftAlone() {
        when(keycloak.userExists(KEYCLOAK_ID)).thenReturn(true);

        assertThat(service.ensureSeedCredential(target, null)).isFalse();

        verify(keycloak, never()).setPassword(anyString(), anyString(), anyBoolean());
        verify(keycloak, never()).createUserWithPassword(any(), anyString(), anyBoolean());
    }

    private static CreateUserRequest seedRequest(String email, String password) {
        return new CreateUserRequest(email, "Cuenta Sembrada", null, null, null, password, null, null, "es-BO", true, List.of());
    }

    private AuditEventDraft captureDraft() {
        ArgumentCaptor<AuditEventDraft> captor = ArgumentCaptor.forClass(AuditEventDraft.class);
        verify(auditTrail).record(captor.capture());
        return captor.getValue();
    }
}
