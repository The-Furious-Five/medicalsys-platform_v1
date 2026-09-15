package com.medicalsys.documental.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalsys.documental.api.dto.GenerateInformedConsentRequest;
import com.medicalsys.documental.api.dto.InformedConsentResponse;
import com.medicalsys.documental.api.dto.SignInformedConsentRequest;
import com.medicalsys.documental.config.StorageProperties;
import com.medicalsys.documental.domain.ConsentEvent;
import com.medicalsys.documental.domain.ConsentEventType;
import com.medicalsys.documental.domain.ConsentTemplate;
import com.medicalsys.documental.domain.ConsentTemplateStatus;
import com.medicalsys.documental.domain.InformedConsent;
import com.medicalsys.documental.domain.SignerType;
import com.medicalsys.documental.domain.StoredDocument;
import com.medicalsys.documental.domain.DocumentType;
import com.medicalsys.documental.exception.ApiException;
import com.medicalsys.documental.exception.ErrorCode;
import com.medicalsys.documental.repository.ConsentEventRepository;
import com.medicalsys.documental.repository.ConsentTemplateRepository;
import com.medicalsys.documental.repository.DocumentRepository;
import com.medicalsys.documental.repository.InformedConsentRepository;
import com.medicalsys.documental.storage.ObjectStorage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InformedConsentServiceTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000f1");
    private static final UUID PATIENT_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    private static final UUID MEDICAL_RECORD_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    private static final UUID PROFESSIONAL_ID = UUID.fromString("00000000-0000-4000-8000-0000000000d1");

    @Mock private InformedConsentRepository consents;
    @Mock private ConsentTemplateRepository templates;
    @Mock private ConsentEventRepository events;
    @Mock private DocumentRepository documents;
    @Mock private ObjectStorage storage;
    @Mock private PatientsDirectoryClient patientsDirectory;
    @Mock private NotificationsClient notificationsClient;

    private HashingService hashingService;
    private InformedConsentService service;

    @BeforeEach
    void setUp() {
        hashingService = new HashingService();
        StorageProperties storageProperties = new StorageProperties(
                "http://localhost:19000", "demo", "secret", "medicalsys-documents-local", 10_000_000);
        service = new InformedConsentService(consents, templates, events, documents, storage, storageProperties,
                hashingService, new FilenameSanitizer(), new ConsentPdfRenderer(), patientsDirectory,
                notificationsClient, new ConsentMapper());

        lenient().when(storage.put(anyString(), any(), anyLong(), anyString(), anyMap()))
                .thenReturn(new ObjectStorage.StoredObject("v1"));
        lenient().when(documents.saveAndFlush(any(StoredDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(consents.saveAndFlush(any(InformedConsent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void generatesConsentAndUploadsRenderedPdf() {
        ConsentTemplate template = publishedTemplate();
        when(templates.findFirstByOrganizationPublicIdAndCodeAndStatusOrderByVersionDesc(
                ORGANIZATION_ID, "SURGERY", ConsentTemplateStatus.PUBLISHED)).thenReturn(Optional.of(template));
        when(patientsDirectory.require(PATIENT_ID)).thenReturn(
                new PatientsDirectoryClient.PatientContact(PATIENT_ID, "Ana", "Ana Gomez", "+59170000000"));

        InformedConsentResponse response = service.generate(generateRequest(), ORGANIZATION_ID, USER_ID);

        assertEquals("GENERATED", response.status());
        assertEquals("SURGERY", response.templateCode());
        assertEquals(1, response.events().size());
        assertEquals("GENERATED", response.events().get(0).type());
        verify(storage).put(anyString(), any(), anyLong(), anyString(), anyMap());
    }

    @Test
    void refusesToGenerateWithoutAPublishedTemplate() {
        when(templates.findFirstByOrganizationPublicIdAndCodeAndStatusOrderByVersionDesc(
                ORGANIZATION_ID, "SURGERY", ConsentTemplateStatus.PUBLISHED)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class,
                () -> service.generate(generateRequest(), ORGANIZATION_ID, USER_ID));
        assertEquals(ErrorCode.CONSENT_TEMPLATE_NOT_PUBLISHED, exception.code());
        verify(storage, never()).put(anyString(), any(), anyLong(), anyString(), anyMap());
    }

    @Test
    void requestingOtpRequiresAWhatsappNumberOnFile() {
        InformedConsent consent = generatedConsent();
        when(consents.findDetailedByPublicIdAndOrganization(consent.getPublicId(), ORGANIZATION_ID))
                .thenReturn(Optional.of(consent));
        when(patientsDirectory.require(PATIENT_ID)).thenReturn(
                new PatientsDirectoryClient.PatientContact(PATIENT_ID, "Ana", "Ana Gomez", null));

        ApiException exception = assertThrows(ApiException.class, () -> service.requestOtp(
                consent.getPublicId(), ORGANIZATION_ID, USER_ID, "127.0.0.1", "junit"));
        assertEquals(ErrorCode.PATIENT_CONTACT_UNAVAILABLE, exception.code());
        verify(notificationsClient, never()).sendWhatsappText(anyString(), anyString(), anyString());
    }

    @Test
    void signingWithTheWrongCodeIsRejectedAndCountsAsAnAttempt() {
        InformedConsent consent = generatedConsent();
        when(consents.findDetailedByPublicIdAndOrganization(consent.getPublicId(), ORGANIZATION_ID))
                .thenReturn(Optional.of(consent));
        ConsentEvent otpEvent = otpRequestedEvent(consent, "999999", Instant.now().plus(5, ChronoUnit.MINUTES), 0);
        when(events.findFirstByInformedConsentAndTypeOrderByOccurredAtDesc(consent, ConsentEventType.OTP_REQUESTED))
                .thenReturn(Optional.of(otpEvent));

        ApiException exception = assertThrows(ApiException.class, () -> service.sign(consent.getPublicId(),
                ORGANIZATION_ID, USER_ID, new SignInformedConsentRequest("000000", "12345678", SignerType.PATIENT),
                "127.0.0.1", "junit"));
        assertEquals(ErrorCode.OTP_INVALID, exception.code());
        assertEquals(1, otpEvent.getDetail().get("attempts"));
    }

    @Test
    void signingWithAnExpiredCodeIsRejected() {
        InformedConsent consent = generatedConsent();
        when(consents.findDetailedByPublicIdAndOrganization(consent.getPublicId(), ORGANIZATION_ID))
                .thenReturn(Optional.of(consent));
        ConsentEvent otpEvent = otpRequestedEvent(consent, "123456", Instant.now().minus(1, ChronoUnit.MINUTES), 0);
        when(events.findFirstByInformedConsentAndTypeOrderByOccurredAtDesc(consent, ConsentEventType.OTP_REQUESTED))
                .thenReturn(Optional.of(otpEvent));

        ApiException exception = assertThrows(ApiException.class, () -> service.sign(consent.getPublicId(),
                ORGANIZATION_ID, USER_ID, new SignInformedConsentRequest("123456", "12345678", SignerType.PATIENT),
                "127.0.0.1", "junit"));
        assertEquals(ErrorCode.OTP_EXPIRED, exception.code());
    }

    @Test
    void signsWhenTheCodeMatches() {
        InformedConsent consent = generatedConsent();
        when(consents.findDetailedByPublicIdAndOrganization(consent.getPublicId(), ORGANIZATION_ID))
                .thenReturn(Optional.of(consent));
        ConsentEvent otpEvent = otpRequestedEvent(consent, "123456", Instant.now().plus(5, ChronoUnit.MINUTES), 0);
        when(events.findFirstByInformedConsentAndTypeOrderByOccurredAtDesc(consent, ConsentEventType.OTP_REQUESTED))
                .thenReturn(Optional.of(otpEvent));

        InformedConsentResponse response = service.sign(consent.getPublicId(), ORGANIZATION_ID, USER_ID,
                new SignInformedConsentRequest("123456", "12345678", SignerType.PATIENT), "127.0.0.1", "junit");

        assertEquals("SIGNED", response.status());
        assertEquals("OTP_WHATSAPP", response.signatureMethod());
    }

    private ConsentTemplate publishedTemplate() {
        ConsentTemplate template = ConsentTemplate.draft(UUID.randomUUID(), ORGANIZATION_ID, "SURGERY", 1,
                "Consentimiento de cirugia", null, "Yo, {{patientName}}, autorizo el procedimiento.",
                null, false, true, LocalDate.now().minusDays(1), null, "hash", USER_ID);
        template.publish();
        return template;
    }

    private InformedConsent generatedConsent() {
        ConsentTemplate template = publishedTemplate();
        StoredDocument pdfDocument = StoredDocument.available(UUID.randomUUID(), ORGANIZATION_ID, PATIENT_ID,
                DocumentType.INFORMED_CONSENT, "consent.pdf", "application/pdf", 10, "bucket", "key", "v1",
                "hash", false, null, USER_ID, null);
        return InformedConsent.generate(UUID.randomUUID(), ORGANIZATION_ID, template, PATIENT_ID,
                MEDICAL_RECORD_ID, null, null, PROFESSIONAL_ID, null, "hash", pdfDocument, "hash");
    }

    private ConsentEvent otpRequestedEvent(InformedConsent consent, String code, Instant expiresAt, int attempts) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("codeHash", hashingService.sha256(
                new ByteArrayInputStream((code + ":" + consent.getPublicId()).getBytes(StandardCharsets.UTF_8))));
        detail.put("expiresAt", expiresAt.toString());
        detail.put("attempts", attempts);
        return new ConsentEvent(consent, ConsentEventType.OTP_REQUESTED, USER_ID, null, "junit", detail);
    }

    private GenerateInformedConsentRequest generateRequest() {
        return new GenerateInformedConsentRequest("SURGERY", PATIENT_ID, MEDICAL_RECORD_ID, null, null,
                PROFESSIONAL_ID, null, Map.of());
    }
}
