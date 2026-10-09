package com.medicalsys.pacientes.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.medicalsys.pacientes.common.error.ApiException;
import com.medicalsys.pacientes.common.error.ErrorCode;
import com.medicalsys.pacientes.domain.enums.AccessGrantStatus;
import com.medicalsys.pacientes.domain.enums.BloodType;
import com.medicalsys.pacientes.domain.enums.DocumentType;
import com.medicalsys.pacientes.domain.enums.MaritalStatus;
import com.medicalsys.pacientes.domain.enums.PatientStatus;
import com.medicalsys.pacientes.domain.enums.Sex;
import com.medicalsys.pacientes.domain.model.Patient;
import com.medicalsys.pacientes.domain.model.PatientAccessGrant;
import com.medicalsys.pacientes.repository.PatientAccessGrantRepository;
import com.medicalsys.pacientes.web.dto.PatientAccessGrantResponse;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PatientAccessServiceTest {

    private static final UUID HOME_ORG = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final UUID OTHER_ORG = UUID.fromString("00000000-0000-4000-8000-0000000000a9");

    @Mock private PatientAccessGrantRepository grants;
    @Mock private NotificationsClient notificationsClient;

    private PatientAccessService service;

    @BeforeEach
    void setUp() {
        service = new PatientAccessService(grants, notificationsClient);
        lenient().when(grants.saveAndFlush(any(PatientAccessGrant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void homeOrganizationAlwaysHasAccess() {
        Patient patient = patientWithPhone("+59170000000");
        assertTrue(service.hasAuthorizedAccess(patient, HOME_ORG));
    }

    @Test
    void otherOrganizationHasNoAccessWithoutAGrant() {
        Patient patient = patientWithPhone("+59170000000");
        assertFalse(service.hasAuthorizedAccess(patient, OTHER_ORG));
    }

    @Test
    void requestingAccessRequiresAPhoneOnFile() {
        Patient patient = patientWithPhone(null);
        ApiException exception = assertThrows(ApiException.class,
                () -> service.requestAccess(patient, OTHER_ORG, UUID.randomUUID()));
        assertEquals(ErrorCode.PATIENT_CONTACT_UNAVAILABLE, exception.code());
    }

    @Test
    void homeOrganizationCannotRequestAccessToItself() {
        Patient patient = patientWithPhone("+59170000000");
        ApiException exception = assertThrows(ApiException.class,
                () -> service.requestAccess(patient, HOME_ORG, UUID.randomUUID()));
        assertEquals(ErrorCode.ACCESS_GRANT_NOT_PENDING, exception.code());
    }

    @Test
    void requestingAccessSendsTheCodeToThePatientsWhatsapp() {
        Patient patient = patientWithPhone("+59171234567");
        PatientAccessGrantResponse response = service.requestAccess(patient, OTHER_ORG, UUID.randomUUID());
        assertEquals("REQUESTED", response.status());
        org.mockito.Mockito.verify(notificationsClient)
                .sendWhatsappText(org.mockito.ArgumentMatchers.eq(OTHER_ORG.toString()),
                        org.mockito.ArgumentMatchers.eq("+59171234567"), any());
    }

    @Test
    void confirmingWithTheWrongCodeIsRejectedAndCountsAsAnAttempt() {
        Patient patient = patientWithPhone("+59171234567");
        PatientAccessGrant grant = requestedGrant(patient, "123456", Instant.now().plus(5, ChronoUnit.MINUTES));
        when(grants.findByPublicIdAndPatient(grant.getPublicId(), patient)).thenReturn(Optional.of(grant));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.confirmAccess(patient, grant.getPublicId(), "000000"));
        assertEquals(ErrorCode.ACCESS_OTP_INVALID, exception.code());
        assertEquals(1, grant.getOtpAttempts());
    }

    @Test
    void confirmingWithAnExpiredCodeIsRejected() {
        Patient patient = patientWithPhone("+59171234567");
        PatientAccessGrant grant = requestedGrant(patient, "123456", Instant.now().minus(1, ChronoUnit.MINUTES));
        when(grants.findByPublicIdAndPatient(grant.getPublicId(), patient)).thenReturn(Optional.of(grant));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.confirmAccess(patient, grant.getPublicId(), "123456"));
        assertEquals(ErrorCode.ACCESS_OTP_EXPIRED, exception.code());
    }

    @Test
    void confirmingWithTheRightCodeAuthorizesTheGrant() {
        Patient patient = patientWithPhone("+59171234567");
        PatientAccessGrant grant = requestedGrant(patient, "123456", Instant.now().plus(5, ChronoUnit.MINUTES));
        when(grants.findByPublicIdAndPatient(grant.getPublicId(), patient)).thenReturn(Optional.of(grant));

        PatientAccessGrantResponse response = service.confirmAccess(patient, grant.getPublicId(), "123456");
        assertEquals("AUTHORIZED", response.status());
    }

    @Test
    void onlyTheHomeOrganizationCanRevoke() {
        Patient patient = patientWithPhone("+59171234567");
        PatientAccessGrant grant = requestedGrant(patient, "123456", Instant.now().plus(5, ChronoUnit.MINUTES));
        grant.authorize();

        ApiException exception = assertThrows(ApiException.class,
                () -> service.revoke(patient, grant.getPublicId(), OTHER_ORG));
        assertEquals(ErrorCode.ACCESS_GRANT_FORBIDDEN, exception.code());
    }

    private Patient patientWithPhone(String phone) {
        Patient patient = Patient.register(DocumentType.CI, "12345678", "");
        patient.updatePersonalData(null, "Ana", "Gomez", null, LocalDate.of(1990, 1, 1), Sex.F,
                MaritalStatus.SINGLE, BloodType.O_POSITIVE, null);
        patient.updateContactData(phone, null, null, null, null, null, null, null, null);
        patient.assignRegisteringOrganization(HOME_ORG);
        patient.changeStatus(PatientStatus.ACTIVE, null);
        return patient;
    }

    private PatientAccessGrant requestedGrant(Patient patient, String code, Instant expiresAt) {
        PatientAccessGrant grant = PatientAccessGrant.request(patient, OTHER_ORG, UUID.randomUUID());
        grant.updateOtp(hash(code, grant.getPublicId()), expiresAt);
        return grant;
    }

    private String hash(String code, UUID grantPublicId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((code + ":" + grantPublicId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
