package com.medicalsys.pacientes.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medicalsys.pacientes.common.correlation.CorrelationIdFilter;
import com.medicalsys.pacientes.common.error.ConflictException;
import com.medicalsys.pacientes.common.error.ErrorCode;
import com.medicalsys.pacientes.common.error.GlobalExceptionHandler;
import com.medicalsys.pacientes.common.security.PermissionAuthorizationInterceptor;
import com.medicalsys.pacientes.common.tenant.TenantContextFilter;
import com.medicalsys.pacientes.domain.enums.BloodType;
import com.medicalsys.pacientes.domain.enums.Department;
import com.medicalsys.pacientes.domain.enums.DocumentType;
import com.medicalsys.pacientes.domain.enums.MaritalStatus;
import com.medicalsys.pacientes.domain.enums.PatientStatus;
import com.medicalsys.pacientes.domain.enums.Sex;
import com.medicalsys.pacientes.service.PatientService;
import com.medicalsys.pacientes.web.dto.BillingPatientSummary;
import com.medicalsys.pacientes.web.dto.PatientResponse;
import com.medicalsys.pacientes.web.dto.SchedulingPatientSummary;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PatientControllerTest {

    private static final String ORGANIZATION_ID = "00000000-0000-4000-8000-0000000000a1";
    private static final String USER_ID = "00000000-0000-4000-8000-0000000000f1";
    private static final UUID PATIENT_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b1");

    @Mock
    private PatientService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new PatientController(service),
                        new InternalPatientController(service),
                        new PatientsPingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new PermissionAuthorizationInterceptor())
                .addFilters(new CorrelationIdFilter(), new TenantContextFilter())
                .build();
    }

    @Test
    void createsPatientAndReturnsTheCompleteRepresentation() throws Exception {
        when(service.register(any())).thenReturn(response("", PATIENT_ID));

        mvc.perform(post("/api/v1/patients")
                        .header("X-Organization-Id", ORGANIZATION_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-Permissions", "patients.patient.create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/patients/" + PATIENT_ID))
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.patientId").value(PATIENT_ID.toString()))
                .andExpect(jsonPath("$.documentNumber").value("123"))
                .andExpect(jsonPath("$.bloodType").value("O+"))
                .andExpect(jsonPath("$.occupation").value("Arquitecta"))
                .andExpect(jsonPath("$.emergencyContactRelationship").value("Madre"));
    }

    @Test
    void returns409WhenTheExactCiAlreadyExists() throws Exception {
        when(service.register(any())).thenThrow(new ConflictException(
                ErrorCode.PATIENT_DOCUMENT_ALREADY_EXISTS,
                "Ya existe un paciente con el mismo tipo, numero y complemento de documento"));

        mvc.perform(post("/api/v1/patients")
                        .header("X-Organization-Id", ORGANIZATION_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-Permissions", "patients.patient.create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PATIENT_DOCUMENT_ALREADY_EXISTS"));
    }

    @Test
    void acceptsCi123WithComplementAAsADifferentRequest() throws Exception {
        UUID secondId = UUID.fromString("00000000-0000-4000-8000-0000000000b2");
        when(service.register(any())).thenReturn(response("A", secondId));

        mvc.perform(post("/api/v1/patients")
                        .header("X-Organization-Id", ORGANIZATION_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-Permissions", "patients.patient.create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("A")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentNumber").value("123"))
                .andExpect(jsonPath("$.documentComplement").value("A"));
    }

    @Test
    void rejectsRequestsWithoutACompleteGatewayIdentity() throws Exception {
        mvc.perform(get("/api/v1/patients/" + PATIENT_ID)
                        .header("X-Organization-Id", ORGANIZATION_ID)
                        .header("X-Permissions", "patients.patient.read"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void internalSummariesDoNotLeakUnneededFields() throws Exception {
        when(service.getSchedulingSummary(PATIENT_ID))
                .thenReturn(new SchedulingPatientSummary(PATIENT_ID, "Ficticia Prueba Maria Elena",
                        PatientStatus.ACTIVE));
        when(service.getBillingSummary(PATIENT_ID))
                .thenReturn(new BillingPatientSummary(PATIENT_ID, "Ficticia Prueba Maria Elena",
                        DocumentType.CI, "123", "", PatientStatus.ACTIVE));

        mvc.perform(get("/internal/v1/patients/" + PATIENT_ID + "/scheduling-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentNumber").doesNotExist())
                .andExpect(jsonPath("$.phoneE164").doesNotExist());

        mvc.perform(get("/internal/v1/patients/" + PATIENT_ID + "/billing-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentNumber").value("123"))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    private PatientResponse response(String complement, UUID patientId) {
        OffsetDateTime now = OffsetDateTime.of(2026, 8, 25, 14, 0, 0, 0, ZoneOffset.UTC);
        return new PatientResponse(
                patientId, null, DocumentType.CI, "123", complement, Department.LA_PAZ,
                "Maria Elena", "Ficticia", "Prueba", "Ficticia Prueba Maria Elena",
                LocalDate.of(1995, 4, 10), Sex.F, MaritalStatus.SINGLE, BloodType.O_POSITIVE,
                "Arquitecta", "+59170000001", "+59170000002",
                "paciente.demo@example.invalid", "AQIDBA==", "Cochabamba", Department.COCHABAMBA,
                "Persona Ficticia", "+59170000003", "Madre", null, null, null,
                PatientStatus.ACTIVE, null, now, now, true);
    }

    private String validRequest(String complement) {
        return """
                {
                  "documentType": "CI",
                  "documentNumber": "123",
                  "documentComplement": "%s",
                  "issuedDepartment": "LA_PAZ",
                  "givenNames": "Maria Elena",
                  "paternalSurname": "Ficticia",
                  "maternalSurname": "Prueba",
                  "birthDate": "1995-04-10",
                  "sex": "F",
                  "maritalStatus": "SINGLE",
                  "bloodType": "O+",
                  "occupation": "Arquitecta",
                  "phoneE164": "+59170000001",
                  "alternatePhone": "+59170000002",
                  "email": "paciente.demo@example.invalid",
                  "encryptedAddressBase64": "AQIDBA==",
                  "city": "Cochabamba",
                  "department": "COCHABAMBA",
                  "emergencyContactName": "Persona Ficticia",
                  "emergencyContactPhone": "+59170000003",
                  "emergencyContactRelationship": "Madre"
                }
                """.formatted(complement);
    }
}
