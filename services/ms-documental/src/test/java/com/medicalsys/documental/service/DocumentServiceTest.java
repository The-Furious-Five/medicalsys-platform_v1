package com.medicalsys.documental.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalsys.documental.api.dto.DocumentLinkRequest;
import com.medicalsys.documental.api.dto.DocumentResponse;
import com.medicalsys.documental.api.dto.UploadDocumentRequest;
import com.medicalsys.documental.config.StorageProperties;
import com.medicalsys.documental.domain.DocumentType;
import com.medicalsys.documental.domain.LinkedEntityType;
import com.medicalsys.documental.domain.StoredDocument;
import com.medicalsys.documental.exception.ApiException;
import com.medicalsys.documental.exception.ErrorCode;
import com.medicalsys.documental.repository.DocumentLinkRepository;
import com.medicalsys.documental.repository.DocumentRepository;
import com.medicalsys.documental.storage.ObjectStorage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000f1");
    private static final UUID PATIENT_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    private static final byte[] PDF = "%PDF-1.4\nMedicalSys demo".getBytes(StandardCharsets.UTF_8);

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentLinkRepository linkRepository;
    @Mock
    private ObjectStorage storage;

    private DocumentService service;
    private PreparedDownload preparedDownload;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties(
                "http://localhost:19000", "demo", "secret", "medicalsys-documents-local", 1000);
        service = new DocumentService(
                documentRepository,
                linkRepository,
                storage,
                properties,
                new HashingService(),
                new FilenameSanitizer(),
                new DocumentFilePolicy(),
                new DocumentMapper());
    }

    @AfterEach
    void cleanDownload() throws Exception {
        if (preparedDownload != null) {
            Files.deleteIfExists(preparedDownload.temporaryFile());
        }
    }

    @Test
    void uploadsToGeneratedKeyAndPersistsIntegrityMetadata() {
        when(storage.put(anyString(), any(), anyLong(), anyString(), anyMap()))
                .thenReturn(new ObjectStorage.StoredObject("version-1"));
        when(documentRepository.saveAndFlush(any(StoredDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DocumentResponse response = service.upload(pdf(), request(), ORGANIZATION_ID, USER_ID);

        assertEquals(DocumentType.LAB_RESULT.name(), response.type());
        assertEquals(PATIENT_ID, response.patientId());
        assertEquals("AVAILABLE", response.status());
        assertEquals(64, response.sha256Hash().length());
        assertEquals(1, response.links().size());

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).put(key.capture(), any(), anyLong(), anyString(), anyMap());
        assertTrue(key.getValue().startsWith(ORGANIZATION_ID + "/" + response.documentId() + "/v1/"));
        assertTrue(key.getValue().endsWith("resultado_demo.pdf"));
    }

    @Test
    void rejectsEmptyFileBeforeCallingStorage() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.upload(empty, request(), ORGANIZATION_ID, USER_ID));

        assertEquals(ErrorCode.EMPTY_FILE, exception.code());
        verify(storage, never()).put(anyString(), any(), anyLong(), anyString(), anyMap());
    }

    @Test
    void rejectsFileLargerThanConfiguredLimit() {
        byte[] content = new byte[1001];
        MockMultipartFile large = new MockMultipartFile("file", "large.pdf", "application/pdf", content);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.upload(large, request(), ORGANIZATION_ID, USER_ID));

        assertEquals(ErrorCode.FILE_TOO_LARGE, exception.code());
    }

    @Test
    void rejectsMimeTypeThatDoesNotMatchClassification() {
        MockMultipartFile image = new MockMultipartFile("file", "result.png", "image/png", PDF);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.upload(image, request(), ORGANIZATION_ID, USER_ID));

        assertEquals(ErrorCode.FILE_TYPE_NOT_ALLOWED, exception.code());
    }

    @Test
    void removesObjectWhenDatabaseInsertFails() {
        when(storage.put(anyString(), any(), anyLong(), anyString(), anyMap()))
                .thenReturn(new ObjectStorage.StoredObject(null));
        when(documentRepository.saveAndFlush(any(StoredDocument.class)))
                .thenThrow(new DataIntegrityViolationException("demo"));

        assertThrows(DataIntegrityViolationException.class,
                () -> service.upload(pdf(), request(), ORGANIZATION_ID, USER_ID));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).delete(key.capture());
        assertTrue(key.getValue().startsWith(ORGANIZATION_ID.toString()));
    }

    @Test
    void hidesDocumentThatBelongsToAnotherOrganization() {
        UUID id = UUID.randomUUID();
        when(documentRepository.findDetailedByPublicIdAndOrganization(id, ORGANIZATION_ID))
                .thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class,
                () -> service.get(id, ORGANIZATION_ID));

        assertEquals(ErrorCode.DOCUMENT_NOT_FOUND, exception.code());
    }

    @Test
    void downloadsOnlyAfterRecalculatingMatchingHash() throws Exception {
        StoredDocument document = storedDocument();
        when(documentRepository.findByPublicIdAndOrganizationPublicId(document.getPublicId(), ORGANIZATION_ID))
                .thenReturn(Optional.of(document));
        when(storage.get(document.getObjectKey())).thenReturn(new ByteArrayInputStream(PDF));

        preparedDownload = service.prepareDownload(document.getPublicId(), ORGANIZATION_ID);

        assertEquals(new String(PDF, StandardCharsets.UTF_8), Files.readString(preparedDownload.temporaryFile()));
        assertEquals(document.getSha256Hash(), preparedDownload.sha256Hash());
    }

    @Test
    void rejectsDownloadWhenStoredBytesNoLongerMatchHash() {
        StoredDocument document = storedDocument();
        when(documentRepository.findByPublicIdAndOrganizationPublicId(document.getPublicId(), ORGANIZATION_ID))
                .thenReturn(Optional.of(document));
        when(storage.get(document.getObjectKey()))
                .thenReturn(new ByteArrayInputStream("altered".getBytes(StandardCharsets.UTF_8)));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.prepareDownload(document.getPublicId(), ORGANIZATION_ID));

        assertEquals(ErrorCode.DOCUMENT_INTEGRITY_FAILURE, exception.code());
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "Resultado Demo.pdf", "application/pdf", PDF);
    }

    private UploadDocumentRequest request() {
        return new UploadDocumentRequest(
                DocumentType.LAB_RESULT,
                PATIENT_ID,
                false,
                Map.of("laboratory", "demo"),
                LocalDate.now().plusYears(5),
                new DocumentLinkRequest(LinkedEntityType.STUDY_RESULT, UUID.randomUUID(), "Resultado final"));
    }

    private StoredDocument storedDocument() {
        String hash = new HashingService().sha256(new ByteArrayInputStream(PDF));
        UUID documentId = UUID.randomUUID();
        return StoredDocument.available(
                documentId,
                ORGANIZATION_ID,
                PATIENT_ID,
                DocumentType.LAB_RESULT,
                "resultado-demo.pdf",
                "application/pdf",
                PDF.length,
                "medicalsys-documents-local",
                ORGANIZATION_ID + "/" + documentId + "/v1/resultado-demo.pdf",
                null,
                hash,
                false,
                Map.of("laboratory", "demo"),
                USER_ID,
                LocalDate.now().plusYears(5));
    }
}
