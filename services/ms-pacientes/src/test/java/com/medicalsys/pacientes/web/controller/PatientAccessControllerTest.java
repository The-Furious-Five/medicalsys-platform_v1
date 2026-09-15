package com.medicalsys.pacientes.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medicalsys.pacientes.common.correlation.CorrelationIdFilter;
import com.medicalsys.pacientes.common.error.GlobalExceptionHandler;
import com.medicalsys.pacientes.common.security.RoleAuthorizationInterceptor;
import com.medicalsys.pacientes.common.tenant.TenantContextFilter;
import com.medicalsys.pacientes.domain.enums.BloodType;
import com.medicalsys.pacientes.domain.enums.DocumentType;
import com.medicalsys.pacientes.domain.enums.MaritalStatus;
import com.medicalsys.pacientes.domain.enums.Sex;
import com.medicalsys.pacientes.domain.model.Patient;
import com.medicalsys.pacientes.service.PatientAccessService;
import com.medicalsys.pacientes.service.PatientService;
import com.medicalsys.pacientes.web.dto.PatientAccessGrantResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PatientAccessControllerTest {

    private static final String ORGANIZATION_ID = "00000000-0000-4000-8000-0000000000a9";
    private static final String USER_ID = "00000000-0000-4000-8000-0000000000f1";
    private static final UUID PATIENT_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b1");

    @Mock private PatientService patients;
    @Mock private PatientAccessService access;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PatientAccessController(patients, access))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new RoleAuthorizationInterceptor())
                .addFilters(new CorrelationIdFilter(), new TenantContextFilter())
                .build();
    }

    @Test
    void patientRoleCannotRequestAccess() throws Exception {
        mvc.perform(post("/api/v1/patients/" + PATIENT_ID + "/acceso/solicitudes")
                        .header("X-Organization-Id", ORGANIZATION_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-Roles", "PATIENT"))
                .andExpect(status().isForbidden());
    }

    @Test
    void receptionistCanRequestAccess() throws Exception {
        when(patients.requireEntity(PATIENT_ID)).thenReturn(examplePatient());
        when(access.requestAccess(any(), eq(UUID.fromString(ORGANIZATION_ID)), eq(UUID.fromString(USER_ID))))
                .thenReturn(new PatientAccessGrantResponse(UUID.randomUUID(), PATIENT_ID,
                        UUID.fromString(ORGANIZATION_ID), "REQUESTED", OffsetDateTime.now(), null, null));

        mvc.perform(post("/api/v1/patients/" + PATIENT_ID + "/acceso/solicitudes")
                        .header("X-Organization-Id", ORGANIZATION_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-Roles", "RECEPTIONIST"))
                .andExpect(status().isCreated());
    }

    private Patient examplePatient() {
        Patient patient = Patient.register(DocumentType.CI, "12345678", "");
        patient.updatePersonalData(null, "Ana", "Gomez", null, LocalDate.of(1990, 1, 1), Sex.F,
                MaritalStatus.SINGLE, BloodType.O_POSITIVE, null);
        return patient;
    }
}
