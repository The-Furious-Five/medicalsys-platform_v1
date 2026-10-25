package com.medicalsys.pacientes.service;

import com.medicalsys.pacientes.common.DateTimes;
import com.medicalsys.pacientes.common.error.ApiException;
import com.medicalsys.pacientes.common.error.ErrorCode;
import com.medicalsys.pacientes.domain.enums.AccessGrantStatus;
import com.medicalsys.pacientes.domain.model.Patient;
import com.medicalsys.pacientes.domain.model.PatientAccessGrant;
import com.medicalsys.pacientes.repository.PatientAccessGrantRepository;
import com.medicalsys.pacientes.web.dto.PatientAccessGrantResponse;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Autoriza a un tenant distinto al que registró al paciente a ver su ficha completa y (vía
 * {@code ms-historial}) su historial clínico. La única forma de autorizar es que el propio paciente
 * confirme un código OTP que le llega a su WhatsApp — ni el tenant que pide, ni el que registró al
 * paciente, pueden otorgárselo a sí mismos.
 */
@Service
public class PatientAccessService {

    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final int MAX_OTP_ATTEMPTS = 5;

    private final PatientAccessGrantRepository grants;
    private final NotificationsClient notificationsClient;
    private final SecureRandom random = new SecureRandom();

    public PatientAccessService(PatientAccessGrantRepository grants, NotificationsClient notificationsClient) {
        this.grants = grants;
        this.notificationsClient = notificationsClient;
    }

    @Transactional(readOnly = true)
    public boolean hasAuthorizedAccess(Patient patient, UUID requestingOrganizationId) {
        if (requestingOrganizationId == null) {
            return false;
        }
        if (requestingOrganizationId.equals(patient.getRegisteredByOrganizationPublicId())) {
            return true;
        }
        if (patient.getRegisteredByOrganizationPublicId() == null) {
            // Paciente sin tenant dueno (sembrado antes de este campo, o creado sin identidad
            // propagada): no hay a quien pedirle autorizacion, asi que no se restringe.
            return true;
        }
        return grants.existsByPatientAndRequestingOrganizationPublicIdAndStatus(
                patient, requestingOrganizationId, AccessGrantStatus.AUTHORIZED);
    }

    @Transactional
    public PatientAccessGrantResponse requestAccess(Patient patient, UUID requestingOrganizationId, UUID userId) {
        if (requestingOrganizationId.equals(patient.getRegisteredByOrganizationPublicId())) {
            throw new ApiException(ErrorCode.ACCESS_GRANT_NOT_PENDING,
                    "Esta organizacion ya es la que registro al paciente; no necesita permiso");
        }
        if (patient.getPhoneE164() == null || patient.getPhoneE164().isBlank()) {
            throw new ApiException(ErrorCode.PATIENT_CONTACT_UNAVAILABLE,
                    "El paciente no tiene un numero de WhatsApp registrado para confirmar el permiso");
        }

        PatientAccessGrant grant = PatientAccessGrant.request(patient, requestingOrganizationId, userId);
        String code = generateCode();
        grant.updateOtp(hashCode(code, grant.getPublicId()), Instant.now().plus(OTP_TTL));
        grant = grants.saveAndFlush(grant);

        notificationsClient.sendWhatsappText(requestingOrganizationId.toString(), patient.getPhoneE164(),
                "Una clinica esta pidiendo acceso a tus datos y tu historial clinico en MedicalSys. "
                        + "Si autorizas, tu codigo es " + code + ". Vence en 10 minutos. "
                        + "Si no lo pediste vos, ignora este mensaje.");
        return toResponse(grant);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public PatientAccessGrantResponse confirmAccess(Patient patient, UUID grantPublicId, String otpCode) {
        PatientAccessGrant grant = find(patient, grantPublicId);
        if (grant.getStatus() != AccessGrantStatus.REQUESTED) {
            throw new ApiException(ErrorCode.ACCESS_GRANT_NOT_PENDING,
                    "El permiso no esta pendiente de confirmacion: " + grant.getStatus());
        }
        if (grant.isOtpExpired(Instant.now())) {
            throw new ApiException(ErrorCode.ACCESS_OTP_EXPIRED, "El codigo vencio; pide el acceso de nuevo");
        }
        if (grant.getOtpAttempts() >= MAX_OTP_ATTEMPTS) {
            throw new ApiException(ErrorCode.ACCESS_OTP_ATTEMPTS_EXCEEDED,
                    "Se agotaron los intentos para este codigo; pide el acceso de nuevo");
        }
        if (!grant.isOtpMatch(hashCode(otpCode, grant.getPublicId()))) {
            grant.recordFailedAttempt();
            grants.saveAndFlush(grant);
            throw new ApiException(ErrorCode.ACCESS_OTP_INVALID,
                    "Codigo incorrecto; intentos restantes: " + (MAX_OTP_ATTEMPTS - grant.getOtpAttempts()));
        }
        grant.authorize();
        return toResponse(grants.saveAndFlush(grant));
    }

    @Transactional
    public PatientAccessGrantResponse revoke(Patient patient, UUID grantPublicId, UUID requestingOrganizationId) {
        if (!requestingOrganizationId.equals(patient.getRegisteredByOrganizationPublicId())) {
            throw new ApiException(ErrorCode.ACCESS_GRANT_FORBIDDEN,
                    "Solo la organizacion que registro al paciente puede revocar un permiso");
        }
        PatientAccessGrant grant = find(patient, grantPublicId);
        try {
            grant.revoke();
        } catch (IllegalStateException exception) {
            throw new ApiException(ErrorCode.ACCESS_GRANT_NOT_AUTHORIZED, exception.getMessage());
        }
        return toResponse(grants.saveAndFlush(grant));
    }

    @Transactional(readOnly = true)
    public List<PatientAccessGrantResponse> list(Patient patient, UUID requestingOrganizationId) {
        List<PatientAccessGrant> found = requestingOrganizationId.equals(patient.getRegisteredByOrganizationPublicId())
                ? grants.findAllByPatientOrderByRequestedAtDesc(patient)
                : grants.findAllByPatientAndRequestingOrganizationPublicIdOrderByRequestedAtDesc(
                        patient, requestingOrganizationId);
        return found.stream().map(this::toResponse).toList();
    }

    /** Solo se invoca con el paciente resuelto desde la identidad autenticada. */
    @Transactional(readOnly = true)
    public List<PatientAccessGrantResponse> listForOwner(Patient patient) {
        return grants.findAllByPatientOrderByRequestedAtDesc(patient).stream().map(this::toResponse).toList();
    }

    @Transactional
    public PatientAccessGrantResponse revokeForOwner(Patient patient, UUID grantPublicId) {
        PatientAccessGrant grant = find(patient, grantPublicId);
        try {
            grant.revoke();
        } catch (IllegalStateException exception) {
            throw new ApiException(ErrorCode.ACCESS_GRANT_NOT_AUTHORIZED, exception.getMessage());
        }
        return toResponse(grants.saveAndFlush(grant));
    }

    private PatientAccessGrant find(Patient patient, UUID grantPublicId) {
        return grants.findByPublicIdAndPatient(grantPublicId, patient)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCESS_GRANT_NOT_FOUND,
                        "No existe un permiso con el identificador indicado para este paciente"));
    }

    private PatientAccessGrantResponse toResponse(PatientAccessGrant grant) {
        return new PatientAccessGrantResponse(
                grant.getPublicId(),
                grant.getPatient().getPublicId(),
                grant.getRequestingOrganizationPublicId(),
                grant.getStatus().name(),
                DateTimes.toOffset(grant.getRequestedAt()),
                DateTimes.toOffset(grant.getAuthorizedAt()),
                DateTimes.toOffset(grant.getRevokedAt()));
    }

    private String generateCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private String hashCode(String code, UUID grantPublicId) {
        String salted = code + ":" + grantPublicId;
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no esta disponible en la JVM", exception);
        }
        return HexFormat.of().formatHex(digest.digest(salted.getBytes(StandardCharsets.UTF_8)));
    }
}
