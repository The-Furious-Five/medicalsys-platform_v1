package com.medicalsys.historial.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.medicalsys.historial.common.correlation.CorrelationId;
import com.medicalsys.historial.common.error.BusinessRuleException;
import com.medicalsys.historial.common.error.ErrorCode;
import com.medicalsys.historial.common.error.NotFoundException;
import com.medicalsys.historial.common.tenant.TenantContextFilter;
import com.medicalsys.historial.domain.enums.EncounterStatus;
import com.medicalsys.historial.domain.enums.EncounterType;
import com.medicalsys.historial.service.ClinicalEncounterService;
import com.medicalsys.historial.support.TestFixtures;
import com.medicalsys.historial.web.controller.ClinicalEncounterController;
import com.medicalsys.historial.web.dto.ClinicalEncounterResponse;
import com.medicalsys.historial.web.dto.EncounterNarrativeResponse;
import com.medicalsys.historial.web.dto.OpenEncounterRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato HTTP del encuentro: formato de error comun, autorizacion por rol, correlationId y
 * politica de narrativa.
 */
@WebMvcTest(ClinicalEncounterController.class)
class ClinicalEncounterControllerTest {

    private static final String ROLES_HEADER = TenantContextFilter.ROLES_HEADER;
    private static final String ORGANIZATION_HEADER = TenantContextFilter.ORGANIZATION_HEADER;
    private static final String USER_HEADER = TenantContextFilter.USER_HEADER;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ClinicalEncounterService encounterService;

    private UUID encounterId;

    @BeforeEach
    void setUp() {
        encounterId = UUID.fromString("11111111-2222-4333-8444-555555555555");
    }

    @Test
    @DisplayName("sin identidad propagada por el gateway responde 401 con el formato de error comun")
    void rejectsAnonymousRequests() throws Exception {
        mockMvc.perform(post("/api/v1/encuentros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/v1/encuentros"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    @DisplayName("una recepcionista no puede abrir un encuentro clinico")
    void rejectsForbiddenRole() throws Exception {
        mockMvc.perform(post("/api/v1/encuentros")
                        .header(ORGANIZATION_HEADER, TestFixtures.ORGANIZATION_ID)
                        .header(USER_HEADER, TestFixtures.USER_ID)
                        .header(ROLES_HEADER, "RECEPTIONIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("un cuerpo incompleto responde 400 con el detalle del campo")
    void reportsValidationDetails() throws Exception {
        String body = """
                {"appointmentId":null,"professionalId":null,"branchId":null}
                """;

        mockMvc.perform(post("/api/v1/encuentros")
                        .header(ORGANIZATION_HEADER, TestFixtures.ORGANIZATION_ID)
                        .header(ROLES_HEADER, "PHYSICIAN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[?(@.field == 'patientId')]").exists());
    }

    @Test
    @DisplayName("propaga el correlationId recibido en la respuesta y en el error")
    void propagatesCorrelationId() throws Exception {
        String correlationId = "corr-de-prueba-1234";

        mockMvc.perform(post("/api/v1/encuentros")
                        .header(CorrelationId.HEADER, correlationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(header().string(CorrelationId.HEADER, correlationId))
                .andExpect(jsonPath("$.correlationId").value(correlationId));
    }

    @Test
    @DisplayName("un profesional abre el encuentro y recibe 201 con la ubicacion del recurso")
    void opensEncounter() throws Exception {
        when(encounterService.open(any(), any())).thenReturn(response(null));

        mockMvc.perform(post("/api/v1/encuentros")
                        .header(ORGANIZATION_HEADER, TestFixtures.ORGANIZATION_ID)
                        .header(USER_HEADER, TestFixtures.USER_ID)
                        .header(ROLES_HEADER, "PHYSICIAN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/encuentros/" + encounterId))
                .andExpect(jsonPath("$.id").value(encounterId.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.encrypted").value(true));
    }

    @Test
    @DisplayName("la narrativa descifrada viaja cuando el servicio la incluye")
    void serializesNarrativeWhenPresent() throws Exception {
        when(encounterService.get(any(), any())).thenReturn(response(
                new EncounterNarrativeResponse("Cefalea", "Relato del paciente ficticio", null, null, null)));

        mockMvc.perform(get("/api/v1/encuentros/{id}", encounterId)
                        .header(ORGANIZATION_HEADER, TestFixtures.ORGANIZATION_ID)
                        .header(ROLES_HEADER, "PHYSICIAN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.narrative.subjective").value("Relato del paciente ficticio"));
    }

    @Test
    @DisplayName("sin narrativa autorizada el campo no aparece en el JSON")
    void omitsNarrativeWhenAbsent() throws Exception {
        when(encounterService.get(any(), any())).thenReturn(response(null));

        mockMvc.perform(get("/api/v1/encuentros/{id}", encounterId)
                        .header(ORGANIZATION_HEADER, TestFixtures.ORGANIZATION_ID)
                        .header(ROLES_HEADER, "RECEPTIONIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(encounterId.toString()))
                .andExpect(jsonPath("$.narrative").doesNotExist());
    }

    @Test
    @DisplayName("una regla de negocio incumplida responde 422 con su codigo estable")
    void mapsBusinessRuleToUnprocessableEntity() throws Exception {
        when(encounterService.get(any(), any())).thenThrow(
                new BusinessRuleException(ErrorCode.ENCOUNTER_NOT_EDITABLE, "El encuentro ya fue firmado"));

        mockMvc.perform(get("/api/v1/encuentros/{id}", UUID.randomUUID())
                        .header(ORGANIZATION_HEADER, TestFixtures.ORGANIZATION_ID)
                        .header(ROLES_HEADER, "PHYSICIAN"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ENCOUNTER_NOT_EDITABLE"));
    }

    @Test
    @DisplayName("un encuentro de otro tenant se responde como inexistente")
    void mapsNotFound() throws Exception {
        when(encounterService.get(any(), any())).thenThrow(
                new NotFoundException(ErrorCode.CLINICAL_ENCOUNTER_NOT_FOUND, "No existe"));

        mockMvc.perform(get("/api/v1/encuentros/{id}", UUID.randomUUID())
                        .header(ORGANIZATION_HEADER, TestFixtures.OTHER_ORGANIZATION_ID)
                        .header(ROLES_HEADER, "PHYSICIAN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLINICAL_ENCOUNTER_NOT_FOUND"));
    }

    @Test
    @DisplayName("un identificador con formato invalido responde 400 y no 500")
    void rejectsMalformedIdentifier() throws Exception {
        mockMvc.perform(get("/api/v1/encuentros/{id}", "no-es-un-uuid")
                        .header(ORGANIZATION_HEADER, TestFixtures.ORGANIZATION_ID)
                        .header(ROLES_HEADER, "PHYSICIAN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    private ClinicalEncounterResponse response(EncounterNarrativeResponse narrative) {
        OffsetDateTime startsAt = OffsetDateTime.of(2026, 8, 24, 14, 0, 0, 0, ZoneOffset.UTC);
        return new ClinicalEncounterResponse(encounterId, UUID.randomUUID(), TestFixtures.PATIENT_ID,
                TestFixtures.APPOINTMENT_ID, TestFixtures.PROFESSIONAL_ID, TestFixtures.BRANCH_ID,
                EncounterType.INITIAL_CONSULTATION, EncounterStatus.DRAFT, startsAt, null, null, narrative,
                true, TestFixtures.KEY_ID, null, null, null, null, List.of(), startsAt, startsAt);
    }

    private OpenEncounterRequest validRequest() {
        return new OpenEncounterRequest(TestFixtures.PATIENT_ID, TestFixtures.APPOINTMENT_ID,
                TestFixtures.PROFESSIONAL_ID, TestFixtures.BRANCH_ID, null, null);
    }
}
