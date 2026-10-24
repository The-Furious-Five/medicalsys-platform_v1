package com.medicalsys.pacientes.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medicalsys.pacientes.common.error.GlobalExceptionHandler;
import com.medicalsys.pacientes.common.security.PermissionAuthorizationInterceptor;
import com.medicalsys.pacientes.common.tenant.TenantContextFilter;
import com.medicalsys.pacientes.domain.enums.DocumentType;
import com.medicalsys.pacientes.domain.model.Patient;
import com.medicalsys.pacientes.repository.PatientRepository;
import com.medicalsys.pacientes.service.PatientAccessService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SelfPatientAccessControllerTest {
    private final UUID user = UUID.randomUUID();
    private final PatientRepository patients = mock(PatientRepository.class);
    private final PatientAccessService access = mock(PatientAccessService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new SelfPatientAccessController(patients, access))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new TenantContextFilter())
            .addInterceptors(new PermissionAuthorizationInterceptor()).build();

    @Test
    void onlyTheAuthenticatedPatientsRequestsAreListed() throws Exception {
        Patient owner = Patient.register(DocumentType.CI, "123456", "");
        when(patients.findByUserAccountPublicId(user)).thenReturn(Optional.of(owner));
        when(access.listForOwner(owner)).thenReturn(List.of());
        mvc.perform(get("/api/v1/patients/me/access-grants").param("patientId", UUID.randomUUID().toString())
                .header("X-Organization-Id", UUID.randomUUID()).header("X-User-Id", user)
                .header("X-Permissions", "patients.self_access.read")).andExpect(status().isOk());
        verify(patients).findByUserAccountPublicId(user);
        verify(access).listForOwner(owner);
    }

    @Test
    void unlinkedAccountsAndAccountsWithoutThePermissionAreDenied() throws Exception {
        when(patients.findByUserAccountPublicId(user)).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/patients/me/access-grants")
                .header("X-Organization-Id", UUID.randomUUID()).header("X-User-Id", user)
                .header("X-Permissions", "patients.self_access.read")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/patients/me/access-grants")
                .header("X-Organization-Id", UUID.randomUUID()).header("X-User-Id", user)
                .header("X-Permissions", "patients.patient.read")).andExpect(status().isForbidden());
    }

    @Test
    void confirmationAndRevocationAlwaysResolveTheOwnerFromTheSession() throws Exception {
        Patient owner = Patient.register(DocumentType.CI, "123456", "");
        when(patients.findByUserAccountPublicId(user)).thenReturn(Optional.of(owner));
        UUID grant = UUID.randomUUID();
        mvc.perform(post("/api/v1/patients/me/access-grants/" + grant + "/confirmacion")
                .contentType("application/json").content("{\"otpCode\":\"123456\"}")
                .header("X-Organization-Id", UUID.randomUUID()).header("X-User-Id", user)
                .header("X-Permissions", "patients.self_access.confirm")).andExpect(status().isOk());
        verify(access).confirmAccess(owner, grant, "123456");
        mvc.perform(post("/api/v1/patients/me/access-grants/" + grant + "/revocacion")
                .header("X-Organization-Id", UUID.randomUUID()).header("X-User-Id", user)
                .header("X-Permissions", "patients.self_access.revoke")).andExpect(status().isOk());
        verify(access).revokeForOwner(owner, grant);
    }
}
