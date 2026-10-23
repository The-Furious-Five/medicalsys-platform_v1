package com.medicalsys.documental.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medicalsys.documental.api.dto.DocumentPageResponse;
import com.medicalsys.documental.api.dto.DocumentResponse;
import com.medicalsys.documental.common.CorrelationIdFilter;
import com.medicalsys.documental.exception.GlobalExceptionHandler;
import com.medicalsys.documental.security.PermissionAuthorizationInterceptor;
import com.medicalsys.documental.service.DocumentService;
import com.medicalsys.documental.service.IntegrityCheckResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class DocumentsControllerTest {

    private static final String ORGANIZATION_ID = "00000000-0000-4000-8000-0000000000a1";
    private static final String USER_ID = "00000000-0000-4000-8000-0000000000f1";
    private static final UUID DOCUMENT_ID = UUID.fromString("00000000-0000-4000-8000-0000000000d1");

    @Mock
    private DocumentService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new DocumentsController(service),
                        new InternalDocumentsController(service),
                        new DocumentsPingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new PermissionAuthorizationInterceptor())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void pingIsPublic() throws Exception {
        mvc.perform(get("/api/v1/documents/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("ms-documental"));
    }

    @Test
    void uploadsMultipartDocumentAndReturnsLocation() throws Exception {
        when(service.upload(any(), any(), eq(UUID.fromString(ORGANIZATION_ID)), eq(UUID.fromString(USER_ID))))
                .thenReturn(response());
        MockMultipartFile file = new MockMultipartFile(
                "file", "resultado-demo.pdf", "application/pdf", "%PDF demo".getBytes());
        MockMultipartFile metadata = new MockMultipartFile(
                "metadata", "", MediaType.APPLICATION_JSON_VALUE,
                ("{\"type\":\"LAB_RESULT\",\"patientId\":\""
                        + UUID.randomUUID() + "\"}").getBytes());

        mvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .file(metadata)
                        .header("X-Organization-Id", ORGANIZATION_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-Permissions", "documents.document.upload"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/documents/" + DOCUMENT_ID))
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.documentId").value(DOCUMENT_ID.toString()))
                .andExpect(jsonPath("$.sha256Hash").value("a".repeat(64)));
    }

    @Test
    void rejectsProtectedEndpointWithoutIdentity() throws Exception {
        mvc.perform(get("/api/v1/documents/" + DOCUMENT_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void rejectsRoleWithoutDocumentPermission() throws Exception {
        mvc.perform(get("/api/v1/documents/" + DOCUMENT_ID)
                        .header("X-Organization-Id", ORGANIZATION_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-Permissions", "marketing.advertisement.view"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void validatesMultipartMetadataBeforeService() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resultado-demo.pdf", "application/pdf", "%PDF demo".getBytes());
        MockMultipartFile metadata = new MockMultipartFile(
                "metadata", "", MediaType.APPLICATION_JSON_VALUE, "{}".getBytes());

        mvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .file(metadata)
                        .header("X-Organization-Id", ORGANIZATION_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-Permissions", "documents.document.upload"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("type"));
    }

    @Test
    void listsDocumentsWithBoundedPagination() throws Exception {
        when(service.search(eq(UUID.fromString(ORGANIZATION_ID)), any(), any(), any(), eq(0), eq(20)))
                .thenReturn(new DocumentPageResponse(List.of(), 0, 20, 0, 0));

        mvc.perform(get("/api/v1/documents")
                        .header("X-Organization-Id", ORGANIZATION_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-Permissions", "documents.document.read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void reportsIntegrityMismatchWithoutFailingTheRequest() throws Exception {
        when(service.checkIntegrity(eq(DOCUMENT_ID), eq(UUID.fromString(ORGANIZATION_ID))))
                .thenReturn(new IntegrityCheckResult(DOCUMENT_ID, "a".repeat(64), "b".repeat(64), false));

        mvc.perform(get("/api/v1/documents/" + DOCUMENT_ID + "/integrity")
                        .header("X-Organization-Id", ORGANIZATION_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-Roles", "PHYSICIAN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(DOCUMENT_ID.toString()))
                .andExpect(jsonPath("$.matches").value(false))
                .andExpect(jsonPath("$.expectedHash").value("a".repeat(64)))
                .andExpect(jsonPath("$.actualHash").value("b".repeat(64)));
    }

    private DocumentResponse response() {
        return new DocumentResponse(
                DOCUMENT_ID,
                UUID.randomUUID(),
                "LAB_RESULT",
                "resultado-demo.pdf",
                "application/pdf",
                100,
                "a".repeat(64),
                false,
                Map.of("laboratory", "demo"),
                "AVAILABLE",
                "NOT_APPLICABLE",
                UUID.fromString(USER_ID),
                Instant.parse("2026-08-25T14:00:00Z"),
                null,
                List.of());
    }
}
