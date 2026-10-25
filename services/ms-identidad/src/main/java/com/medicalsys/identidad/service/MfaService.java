package com.medicalsys.identidad.service;

import com.medicalsys.identidad.audit.AuditEventDraft;
import com.medicalsys.identidad.audit.AuditTrail;
import com.medicalsys.identidad.config.IdentityProperties;
import com.medicalsys.identidad.domain.UserAccount;
import com.medicalsys.identidad.domain.UserStatus;
import com.medicalsys.identidad.repository.UserAccountRepository;
import com.medicalsys.identidad.security.SessionStore;
import com.medicalsys.identidad.security.mfa.Base32;
import com.medicalsys.identidad.security.mfa.MfaChallengeStore;
import com.medicalsys.identidad.security.mfa.MfaChallengeStore.Challenge;
import com.medicalsys.identidad.security.mfa.MfaChallengeStore.Purpose;
import com.medicalsys.identidad.security.mfa.MfaSecretCipher;
import com.medicalsys.identidad.security.mfa.QrCodeSvg;
import com.medicalsys.identidad.security.mfa.Totp;
import com.medicalsys.identidad.web.dto.LoginResponse;
import com.medicalsys.identidad.web.dto.MfaCodeRequest;
import com.medicalsys.identidad.web.dto.MfaEnrollmentResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Segundo factor TOTP obligatorio (IAM-07), compatible con Google Authenticator.
 *
 * <p>Primer ingreso: tras cambiar la contraseña temporal, el login devuelve MFA_ENROLLMENT_REQUIRED;
 * {@link #enrollment} entrega el QR y {@link #activate} lo confirma con un código, que además abre la
 * sesión. Ingresos siguientes: MFA_REQUIRED y {@link #verify}. Un código no se acepta dos veces, cada
 * desafío admite pocos intentos y demasiados códigos fallidos bloquean la cuenta como el 3er intento
 * de contraseña: solo un administrador la desbloquea.
 */
@Service
public class MfaService {

    private static final Logger log = LoggerFactory.getLogger(MfaService.class);

    private final UserAccountRepository users;
    private final MfaChallengeStore challenges;
    private final MfaSecretCipher cipher;
    private final AuthService authService;
    private final AccountAccessService accountAccess;
    private final SessionStore sessionStore;
    private final AuditTrail auditTrail;
    private final IdentityProperties.Mfa config;

    public MfaService(UserAccountRepository users, MfaChallengeStore challenges, MfaSecretCipher cipher,
                      AuthService authService, AccountAccessService accountAccess, SessionStore sessionStore,
                      AuditTrail auditTrail, IdentityProperties properties) {
        this.users = users;
        this.challenges = challenges;
        this.cipher = cipher;
        this.authService = authService;
        this.accountAccess = accountAccess;
        this.sessionStore = sessionStore;
        this.auditTrail = auditTrail;
        this.config = properties.getMfa();
    }

    /**
     * Secreto nuevo para registrar en la aplicación. Pedirlo otra vez dentro del mismo desafío lo
     * reemplaza (por ejemplo, si se escaneó en el teléfono equivocado): vale el último confirmado.
     */
    @Transactional
    public MfaEnrollmentResponse enrollment(String mfaToken) {
        Challenge challenge = require(mfaToken, Purpose.ENROLL);
        UserAccount user = account(challenge);
        byte[] secret = Totp.newSecret();
        user.setEncryptedMfaSecret(cipher.encrypt(secret, user.getPublicId().toString()));
        String encoded = Base32.encode(secret);
        String label = config.getIssuer() + ":" + user.getUsername();
        String uri = "otpauth://totp/" + encode(label) + "?secret=" + encoded + "&issuer=" + encode(config.getIssuer())
                + "&algorithm=SHA1&digits=" + Totp.DIGITS + "&period=" + Totp.PERIOD_SECONDS;
        return new MfaEnrollmentResponse(config.getIssuer(), user.getUsername(), group(encoded), uri,
                QrCodeSvg.dataUri(uri));
    }

    /** Confirma el registro con el primer código y abre la sesión. */
    @Transactional
    public LoginResponse activate(MfaCodeRequest request) {
        Challenge challenge = require(request.mfaToken(), Purpose.ENROLL);
        UserAccount user = account(challenge);
        if (user.getEncryptedMfaSecret() == null) {
            throw ApiException.conflict("Primero escanea el codigo QR");
        }
        OptionalLong step = check(user, request.code());
        if (step.isEmpty()) {
            throw failed(challenge, user);
        }
        user.enableMfa(step.getAsLong(), Instant.now());
        challenges.close(challenge);
        challenges.clearFailures(user.getPublicId());
        log.info("Segundo factor registrado para la cuenta {}", user.getUserCode());
        auditTrail.record(AuditEventDraft.authentication("MFA_ENROLLED", "SUCCESS", user.getPublicId(),
                user.getEmail(), user.getOrganizationPublicId(), 200, null));
        return authService.completeLogin(user);
    }

    /** Ingreso habitual: el código de la aplicación abre la sesión. */
    @Transactional
    public LoginResponse verify(MfaCodeRequest request) {
        Challenge challenge = require(request.mfaToken(), Purpose.VERIFY);
        UserAccount user = account(challenge);
        if (!user.isMfaEnabled()) {
            throw expired();
        }
        OptionalLong step = check(user, request.code());
        if (step.isEmpty()) {
            throw failed(challenge, user);
        }
        user.setMfaLastUsedStep(step.getAsLong());
        challenges.close(challenge);
        challenges.clearFailures(user.getPublicId());
        return authService.completeLogin(user);
    }

    private OptionalLong check(UserAccount user, String code) {
        byte[] secret = cipher.decrypt(user.getEncryptedMfaSecret(), user.getPublicId().toString());
        return Totp.verify(secret, code, Instant.now(), user.getMfaLastUsedStep());
    }

    /**
     * Código incorrecto: queda MFA_FAILED. El bloqueo se guarda en su propia transacción porque esta se
     * revierte con la excepción.
     */
    private ApiException failed(Challenge challenge, UserAccount user) {
        boolean exhausted = challenges.registerFailedAttempt(challenge);
        long failures = challenges.countFailure(user.getPublicId());
        auditTrail.record(AuditEventDraft.authentication("MFA_FAILED", "DENIED", user.getPublicId(),
                user.getEmail(), user.getOrganizationPublicId(), 401, "Codigo de segundo factor incorrecto"));
        if (failures >= config.getMaxFailures()) {
            challenges.close(challenge);
            accountAccess.registerLockout(user.getId());
            sessionStore.closeAll(user.getPublicId());
            log.warn("Cuenta {} bloqueada por {} codigos de segundo factor incorrectos", user.getUserCode(), failures);
            auditTrail.record(AuditEventDraft.authentication("ACCOUNT_LOCKED", "DENIED", user.getPublicId(),
                    user.getEmail(), user.getOrganizationPublicId(), 423,
                    "Demasiados codigos de segundo factor incorrectos"));
            return ApiException.accountLocked();
        }
        if (exhausted) {
            return new ApiException(HttpStatus.UNAUTHORIZED, "Verificacion vencida",
                    "Demasiados codigos incorrectos. Vuelve a iniciar sesion.", "MFA_CHALLENGE_EXPIRED");
        }
        return new ApiException(HttpStatus.UNAUTHORIZED, "Codigo incorrecto",
                "El codigo no es valido o ya se uso. Escribe el que muestra ahora tu aplicacion.", "MFA_INVALID_CODE");
    }

    private Challenge require(String token, Purpose purpose) {
        return challenges.find(token).filter(challenge -> challenge.purpose() == purpose)
                .orElseThrow(MfaService::expired);
    }

    /** La cuenta pudo cambiar desde la contraseña: se vuelve a revisar su estado. */
    private UserAccount account(Challenge challenge) {
        UserAccount user = users.findByPublicId(challenge.userPublicId()).orElseThrow(MfaService::expired);
        if (user.getStatus() == UserStatus.LOCKED) {
            challenges.close(challenge);
            throw ApiException.accountLocked();
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            challenges.close(challenge);
            throw ApiException.forbidden("La cuenta esta inactiva");
        }
        return user;
    }

    private static ApiException expired() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Verificacion vencida",
                "La verificacion vencio o no es valida. Vuelve a iniciar sesion.", "MFA_CHALLENGE_EXPIRED");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** De a 4 caracteres, para dictarlo o tipearlo sin perderse. */
    private static String group(String encoded) {
        return encoded.replaceAll("(.{4})(?!$)", "$1 ");
    }
}
