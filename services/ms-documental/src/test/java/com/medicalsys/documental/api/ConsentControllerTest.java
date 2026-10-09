package com.medicalsys.documental.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medicalsys.documental.api.dto.ConsentTemplateResponse;
import com.medicalsys.documental.api.dto.InformedConsentResponse;
import com.medicalsys.documental.common.CorrelationIdFilter;
import com.medicalsys.documental.exception.GlobalExceptionHandler;
import com.medicalsys.documental.security.RoleAuthorizationInterceptor;
import com.medicalsys.documental.service.ConsentTemplateService;
import com.medicalsys.documental.service.InformedConsentService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
class ConsentControllerTest {

    private static final String ORGANIZATION_ID = "00000000-0000-4000-8000-0000000000a1";
    private static final String USER_ID = "00000000-0000-4000-8000-0000000000f1";

    @Mock private ConsentTemplateService templateService;
    @Mock private InformedConsentService consentService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new ConsentTemplateController(templateService),
                        new InformedConsentController(consentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new RoleAuthorizationInterceptor())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void creatingATemplateWithoutAnAllowedRoleIsForbidden() throws Exception {
        mvc.perform(post("/api/v1/consentimientos/plantillas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Organization-Id", ORGANIZATION_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-Roles", "RECEPTIONIST")
                        .content(templateJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void creatingATemplateWithoutIdentityIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/consentimientos/plantillas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(templateJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void administratorCanCreateATemplate() throws Exception {
        when(templateService.create(any(), eq(UUID.fromString(ORGANIZATION_ID)), eq(UUID.fromString(USER_ID))))
                .thenReturn(templateResponse());

        mvc.perform(post("/api/v1/consentimientos/plantillas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Organization-Id", ORGANIZATION_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-Roles", "ADMINISTRATOR")
                        .content(templateJson()))
                .andExpect(status().isCreated());
    }

    @Test
    void receptionistCanGenerateAConsent() throws Exception {
        when(consentService.generate(any(), eq(UUID.fromString(ORGANIZATION_ID)), eq(UUID.fromString(USER_ID))))
                .thenReturn(consentResponse());

        mvc.perform(post("/api/v1/consentimientos/generar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Organization-Id", ORGANIZATION_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-Roles", "RECEPTIONIST")
                        .content(generateConsentJson()))
                .andExpect(status().isCreated());
    }

    @Test
    void patientRoleCannotOperateConsents() throws Exception {
        mvc.perform(get("/api/v1/consentimientos")
                        .param("patientId", UUID.randomUUID().toString())
                        .header("X-Organization-Id", ORGANIZATION_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-Roles", "PATIENT"))
                .andExpect(status().isForbidden());
    }

    private String templateJson() {
        return """
                {"code":"SURGERY","title":"Consentimiento de cirugia",
                 "markdownBody":"Yo, {{patientName}}, autorizo.",
                 "requiresWitness":false,"requiresGuardianForMinor":true,
                 "validFrom":"%s"}
                """.formatted(LocalDate.now());
    }

    private String generateConsentJson() {
        return """
                {"templateCode":"SURGERY","patientId":"%s","medicalRecordId":"%s",
                 "healthProfessionalId":"%s"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    private ConsentTemplateResponse templateResponse() {
        return new ConsentTemplateResponse(UUID.randomUUID(), UUID.fromString(ORGANIZATION_ID), "SURGERY", 1,
                "Consentimiento de cirugia", null, "cuerpo", null, false, true, LocalDate.now(), null, "DRAFT",
                Instant.now());
    }

    private InformedConsentResponse consentResponse() {
        return new InformedConsentResponse(UUID.randomUUID(), UUID.fromString(ORGANIZATION_ID), "SURGERY", 1,
                UUID.randomUUID(), UUID.randomUUID(), null, null, UUID.randomUUID(), null, "GENERATED", null,
                null, null, UUID.randomUUID(), Instant.now(), null, null, null, List.of());
    }
}
