package com.medicalsys.identidad.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalsys.identidad.audit.AuditEventDraft;
import com.medicalsys.identidad.audit.AuditTrail;
import com.medicalsys.identidad.config.IdentityProperties;
import com.medicalsys.identidad.domain.UserAccount;
import com.medicalsys.identidad.domain.UserStatus;
import com.medicalsys.identidad.repository.UserAccountRepository;
import com.medicalsys.identidad.security.SessionStore;
import com.medicalsys.identidad.security.mfa.MfaChallengeStore;
import com.medicalsys.identidad.security.mfa.MfaChallengeStore.Challenge;
import com.medicalsys.identidad.security.mfa.MfaChallengeStore.Purpose;
import com.medicalsys.identidad.security.mfa.MfaSecretCipher;
import com.medicalsys.identidad.security.mfa.Totp;
import com.medicalsys.identidad.web.dto.MfaCodeRequest;
import com.medicalsys.identidad.web.dto.MfaEnrollmentResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

/**
 * IAM-07: registro del QR en el primer ingreso, código en cada ingreso, sin reutilizar códigos y con
 * bloqueo tras demasiados fallos. LOG-02: cada desenlace queda en la bitácora sin el secreto.
 */
class MfaServiceTest {

    private static final String TOKEN = "token-del-desafio";

    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final MfaChallengeStore challenges = mock(MfaChallengeStore.class);
    private final AuthService authService = mock(AuthService.class);
    private final AccountAccessService accountAccess = mock(AccountAccessService.class);
    private final SessionStore sessionStore = mock(SessionStore.class);
    private final AuditTrail auditTrail = mock(AuditTrail.class);
    private final IdentityProperties properties = new IdentityProperties();
    private MfaSecretCipher cipher;
    private MfaService service;
    private UserAccount user;

    @BeforeEach
    void setUp() {
        properties.getMfa().setEncryptionKey("una-llave-de-pruebas-de-mas-de-32-bytes!");
        cipher = new MfaSecretCipher(properties);
        service = new MfaService(users, challenges, cipher, authService, accountAccess, sessionStore, auditTrail,
                properties);
        user = new UserAccount();
        user.setUsername("patricia.loayza.b");
        user.setEmail("admin@medicalsys.demo");
        user.setStatus(UserStatus.ACTIVE);
        when(users.findByPublicId(user.getPublicId())).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("primer ingreso: QR con la URI otpauth de Google Authenticator y el secreto guardado cifrado")
    void enrollmentReturnsTheQrAndStoresTheSecretEncrypted() {
        challenge(Purpose.ENROLL, 0);

        MfaEnrollmentResponse response = service.enrollment(TOKEN);

        assertThat(response.otpauthUri()).startsWith("otpauth://totp/MedicalSys%3Apatricia.loayza.b?secret=")
                .contains("&issuer=MedicalSys&algorithm=SHA1&digits=6&period=30");
        assertThat(response.qrImage()).startsWith("data:image/svg+xml;base64,");
        assertThat(user.getEncryptedMfaSecret()).isNotNull();
        assertThat(user.isMfaEnabled()).isFalse();
    }

    @Test
    @DisplayName("primer ingreso: el primer código válido activa el segundo factor y abre la sesión")
    void activationWithAValidCodeEnablesMfaAndLogsIn() {
        Challenge challenge = challenge(Purpose.ENROLL, 0);
        service.enrollment(TOKEN);

        service.activate(new MfaCodeRequest(TOKEN, currentCode()));

        assertThat(user.isMfaEnabled()).isTrue();
        assertThat(user.getMfaEnrolledAt()).isNotNull();
        verify(challenges).close(challenge);
        verify(authService).completeLogin(user);
        assertThat(drafts()).extracting(AuditEventDraft::action).containsExactly("MFA_ENROLLED");
    }

    @Test
    @DisplayName("ingreso habitual: código incorrecto es 401 MFA_INVALID_CODE y queda MFA_FAILED")
    void wrongCodeIsRejectedAndAudited() {
        enrolled();
        challenge(Purpose.VERIFY, 0);
        when(challenges.countFailure(user.getPublicId())).thenReturn(1L);

        assertThatThrownBy(() -> service.verify(new MfaCodeRequest(TOKEN, wrongCode())))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getCode()).isEqualTo("MFA_INVALID_CODE");
                });
        verify(authService, never()).completeLogin(any());
        assertThat(drafts()).extracting(AuditEventDraft::action).containsExactly("MFA_FAILED");
    }

    @Test
    @DisplayName("un código que ya abrió una sesión no abre otra")
    void aUsedCodeCannotBeReplayed() {
        enrolled();
        user.setMfaLastUsedStep(Totp.step(Instant.now()) + 1);
        challenge(Purpose.VERIFY, 0);
        when(challenges.countFailure(user.getPublicId())).thenReturn(1L);

        assertThatThrownBy(() -> service.verify(new MfaCodeRequest(TOKEN, currentCode())))
                .isInstanceOf(ApiException.class);
        verify(authService, never()).completeLogin(any());
    }

    @Test
    @DisplayName("demasiados códigos incorrectos: la cuenta se bloquea (423) y se cierran sus sesiones")
    void tooManyFailuresLockTheAccount() {
        enrolled();
        challenge(Purpose.VERIFY, 3);
        when(challenges.countFailure(user.getPublicId())).thenReturn(10L);

        assertThatThrownBy(() -> service.verify(new MfaCodeRequest(TOKEN, wrongCode())))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.LOCKED));
        verify(accountAccess).registerLockout(user.getId());
        verify(sessionStore).closeAll(user.getPublicId());
        assertThat(drafts()).extracting(AuditEventDraft::action).containsExactly("MFA_FAILED", "ACCOUNT_LOCKED");
    }

    @Test
    @DisplayName("desafío vencido o de otro paso: 401 MFA_CHALLENGE_EXPIRED")
    void expiredOrMismatchedChallengeIsRejected() {
        when(challenges.find(TOKEN)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.verify(new MfaCodeRequest(TOKEN, "123456")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("MFA_CHALLENGE_EXPIRED"));

        challenge(Purpose.VERIFY, 0);
        assertThatThrownBy(() -> service.enrollment(TOKEN))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("MFA_CHALLENGE_EXPIRED"));
    }

    private Challenge challenge(Purpose purpose, int attempts) {
        Challenge challenge = new Challenge("identity:mfa:challenge:x", user.getPublicId(), purpose, attempts);
        when(challenges.find(TOKEN)).thenReturn(Optional.of(challenge));
        return challenge;
    }

    private void enrolled() {
        user.setEncryptedMfaSecret(cipher.encrypt(Totp.newSecret(), user.getPublicId().toString()));
        user.enableMfa(0L, Instant.now());
    }

    private String currentCode() {
        byte[] secret = cipher.decrypt(user.getEncryptedMfaSecret(), user.getPublicId().toString());
        return Totp.code(secret, Totp.step(Instant.now()));
    }

    /** Distinto de los tres códigos que la ventana acepta. */
    private String wrongCode() {
        byte[] secret = cipher.decrypt(user.getEncryptedMfaSecret(), user.getPublicId().toString());
        long step = Totp.step(Instant.now());
        List<String> valid = List.of(Totp.code(secret, step - 1), Totp.code(secret, step), Totp.code(secret, step + 1));
        for (int candidate = 0; ; candidate++) {
            String code = String.format("%06d", candidate);
            if (!valid.contains(code)) {
                return code;
            }
        }
    }

    private List<AuditEventDraft> drafts() {
        ArgumentCaptor<AuditEventDraft> captor = ArgumentCaptor.forClass(AuditEventDraft.class);
        verify(auditTrail, org.mockito.Mockito.atLeast(0)).record(captor.capture());
        return captor.getAllValues();
    }
}
