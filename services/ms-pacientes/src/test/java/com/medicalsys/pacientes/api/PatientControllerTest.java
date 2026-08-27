package com.medicalsys.pacientes.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medicalsys.pacientes.api.dto.BillingPatientSummary;
import com.medicalsys.pacientes.api.dto.PatientResponse;
import com.medicalsys.pacientes.api.dto.SchedulingPatientSummary;
import com.medicalsys.pacientes.common.CorrelationIdFilter;
import com.medicalsys.pacientes.exception.GlobalExceptionHandler;
import com.medicalsys.pacientes.security.RoleAuthorizationInterceptor;
import com.medicalsys.pacientes.service.PatientService;
import java.time.Instant;
import java.time.LocalDate;
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
                .addInterceptors(new RoleAuthorizationInterceptor())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void createsPatientWithExpectedStatusLocationAndBody() throws Exception {
        when(service.create(any())).thenReturn(response());

        mvc.perform(post("/api/v1/patients")
                        .header("X-Organization-Id", ORGANIZATION_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-Roles", "RECEPTIONIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/patients/" + PATIENT_ID))
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.patientId").value(PATIENT_ID.toString()))
                .andExpect(jsonPath("$.documentNumber").value("DEMO-0001"));
    }

    @Test
    void rejectsInvalidRequestBeforeCallingService() throws Exception {
        mvc.perform(post("/api/v1/patients")
                        .header("X-Organization-Id", ORGANIZATION_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-Roles", "RECEPTIONIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentType\":\"CI\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void rejectsPublicOperationWithoutIdentityHeaders() throws Exception {
        mvc.perform(get("/api/v1/patients/" + PATIENT_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void internalSummariesExposeOnlyTheirProjection() throws Exception {
        when(service.getSchedulingSummary(PATIENT_ID))
                .thenReturn(new SchedulingPatientSummary(PATIENT_ID, "Ficticia Prueba Maria Elena", "ACTIVE"));
        when(service.getBillingSummary(PATIENT_ID))
                .thenReturn(new BillingPatientSummary(
                        PATIENT_ID, "Ficticia Prueba Maria Elena", "CI", "DEMO-0001", "", "ACTIVE"));

        mvc.perform(get("/internal/v1/patients/" + PATIENT_ID + "/scheduling-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(PATIENT_ID.toString()))
                .andExpect(jsonPath("$.documentNumber").doesNotExist())
                .andExpect(jsonPath("$.phoneE164").doesNotExist());

        mvc.perform(get("/internal/v1/patients/" + PATIENT_ID + "/billing-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentNumber").value("DEMO-0001"))
                .andExpect(jsonPath("$.phoneE164").doesNotExist());
    }

    private PatientResponse response() {
        Instant now = Instant.parse("2026-08-25T14:00:00Z");
        return new PatientResponse(
                PATIENT_ID,
                "CI",
                "DEMO-0001",
                "",
                "LA_PAZ",
                "Maria Elena",
                "Ficticia",
                "Prueba",
                "Ficticia Prueba Maria Elena",
                LocalDate.of(1995, 4, 10),
                "F",
                "+59170000001",
                "paciente.demo@example.invalid",
                "La Paz",
                "LA_PAZ",
                "ACTIVE",
                now,
                now);
    }

    private String validRequest() {
        return """
                {
                  "documentType": "CI",
                  "documentNumber": "DEMO-0001",
                  "documentComplement": "",
                  "issuedDepartment": "LA_PAZ",
                  "givenNames": "Maria Elena",
                  "paternalSurname": "Ficticia",
                  "maternalSurname": "Prueba",
                  "birthDate": "1995-04-10",
                  "sex": "F",
                  "phoneE164": "+59170000001",
                  "email": "paciente.demo@example.invalid",
                  "city": "La Paz",
                  "department": "LA_PAZ"
                }
                """;
    }
}
