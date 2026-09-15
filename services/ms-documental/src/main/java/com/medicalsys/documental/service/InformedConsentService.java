package com.medicalsys.documental.service;

import com.medicalsys.documental.api.dto.GenerateInformedConsentRequest;
import com.medicalsys.documental.api.dto.InformedConsentResponse;
import com.medicalsys.documental.api.dto.RejectInformedConsentRequest;
import com.medicalsys.documental.api.dto.SignInformedConsentRequest;
import com.medicalsys.documental.config.StorageProperties;
import com.medicalsys.documental.domain.ConsentEvent;
import com.medicalsys.documental.domain.ConsentEventType;
import com.medicalsys.documental.domain.ConsentTemplate;
import com.medicalsys.documental.domain.ConsentTemplateStatus;
import com.medicalsys.documental.domain.DocumentType;
import com.medicalsys.documental.domain.InformedConsent;
import com.medicalsys.documental.domain.SignatureMethod;
import com.medicalsys.documental.domain.StoredDocument;
import com.medicalsys.documental.exception.ApiException;
import com.medicalsys.documental.exception.ErrorCode;
import com.medicalsys.documental.repository.ConsentEventRepository;
import com.medicalsys.documental.repository.ConsentTemplateRepository;
import com.medicalsys.documental.repository.DocumentRepository;
import com.medicalsys.documental.repository.InformedConsentRepository;
import com.medicalsys.documental.storage.ObjectStorage;
import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta el ciclo de vida del consentimiento informado: generar el PDF, pedir un codigo OTP por
 * WhatsApp y firmarlo con ese codigo. El paciente firma en su propio telefono, pero quien opera la
 * pantalla es personal de la clinica (medico, recepcion o administracion) — no existe hoy un vinculo
 * verificable entre una cuenta de usuario con rol PATIENT y su patientId, asi que un autoservicio
 * real del paciente queda para otra sesion.
 */
@Service
public class InformedConsentService {

    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final int MAX_OTP_ATTEMPTS = 5;

    private final InformedConsentRepository consents;
    private final ConsentTemplateRepository templates;
    private final ConsentEventRepository events;
    private final DocumentRepository documents;
    private final ObjectStorage storage;
    private final StorageProperties storageProperties;
    private final HashingService hashingService;
    private final FilenameSanitizer filenameSanitizer;
    private final ConsentPdfRenderer pdfRenderer;
    private final PatientsDirectoryClient patientsDirectory;
    private final NotificationsClient notificationsClient;
    private final ConsentMapper mapper;
    private final SecureRandom random = new SecureRandom();

    public InformedConsentService(InformedConsentRepository consents, ConsentTemplateRepository templates,
            ConsentEventRepository events, DocumentRepository documents, ObjectStorage storage,
            StorageProperties storageProperties, HashingService hashingService,
            FilenameSanitizer filenameSanitizer, ConsentPdfRenderer pdfRenderer,
            PatientsDirectoryClient patientsDirectory, NotificationsClient notificationsClient,
            ConsentMapper mapper) {
        this.consents = consents;
        this.templates = templates;
        this.events = events;
        this.documents = documents;
        this.storage = storage;
        this.storageProperties = storageProperties;
        this.hashingService = hashingService;
        this.filenameSanitizer = filenameSanitizer;
        this.pdfRenderer = pdfRenderer;
        this.patientsDirectory = patientsDirectory;
        this.notificationsClient = notificationsClient;
        this.mapper = mapper;
    }

    @Transactional
    public InformedConsentResponse generate(GenerateInformedConsentRequest request, UUID organizationId,
            UUID userId) {
        ConsentTemplate template = templates
                .findFirstByOrganizationPublicIdAndCodeAndStatusOrderByVersionDesc(
                        organizationId, request.templateCode(), ConsentTemplateStatus.PUBLISHED)
                .orElseThrow(() -> new ApiException(ErrorCode.CONSENT_TEMPLATE_NOT_PUBLISHED,
                        "No hay una plantilla publicada con el codigo " + request.templateCode()));

        PatientsDirectoryClient.PatientContact contact = patientsDirectory.require(request.patientId());

        Map<String, Object> variables = new LinkedHashMap<>(ConsentPdfRenderer.baseVariables(contact.fullName()));
        if (request.variables() != null) {
            variables.putAll(request.variables());
        }
        byte[] pdf = pdfRenderer.render(template.getTitle(), template.getMarkdownBody(), variables);
        String documentHash = hashingService.sha256(new ByteArrayInputStream(pdf));

        UUID consentId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        String filename = filenameSanitizer.safe(template.getCode() + "-" + consentId + ".pdf");
        String objectKey = organizationId + "/" + documentId + "/v1/" + filename;

        ObjectStorage.StoredObject storedObject = storage.put(objectKey, new ByteArrayInputStream(pdf),
                pdf.length, "application/pdf",
                Map.of("sha256", documentHash, "informed-consent-id", consentId.toString()));

        try {
            StoredDocument document = StoredDocument.available(documentId, organizationId, request.patientId(),
                    DocumentType.INFORMED_CONSENT, filename, "application/pdf", pdf.length,
                    storageProperties.bucket(), objectKey, storedObject.versionId(), documentHash, false,
                    Map.of("informedConsentId", consentId.toString()), userId, null);
            StoredDocument savedDocument = documents.saveAndFlush(document);

            InformedConsent consent = InformedConsent.generate(consentId, organizationId, template,
                    request.patientId(), request.medicalRecordId(), request.appointmentId(),
                    request.clinicalEncounterId(), request.healthProfessionalId(), request.serviceOfferingId(),
                    documentHash, savedDocument, documentHash);
            consent.addEvent(new ConsentEvent(consent, ConsentEventType.GENERATED, userId, null, null, null));
            return mapper.toResponse(consents.saveAndFlush(consent));
        } catch (RuntimeException exception) {
            storage.delete(objectKey);
            throw exception;
        }
    }

    @Transactional
    public InformedConsentResponse requestOtp(UUID consentPublicId, UUID organizationId, UUID userId, String ip,
            String userAgent) {
        InformedConsent consent = find(consentPublicId, organizationId);
        requireSignable(consent);

        PatientsDirectoryClient.PatientContact contact = patientsDirectory.require(consent.getPatientPublicId());
        if (contact.whatsappNumber() == null || contact.whatsappNumber().isBlank()) {
            throw new ApiException(ErrorCode.PATIENT_CONTACT_UNAVAILABLE,
                    "El paciente no tiene un numero de WhatsApp registrado");
        }

        String code = generateCode();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("codeHash", hashOtpCode(code, consent.getPublicId()));
        detail.put("expiresAt", Instant.now().plus(OTP_TTL).toString());
        detail.put("attempts", 0);

        byte[] ipBytes = ipBytes(ip);
        consent.addEvent(new ConsentEvent(consent, ConsentEventType.OTP_REQUESTED, userId, ipBytes, userAgent,
                detail));
        consents.saveAndFlush(consent);

        notificationsClient.sendWhatsappText(organizationId.toString(), contact.whatsappNumber(),
                "Tu codigo para firmar el consentimiento informado es " + code
                        + ". Vence en 10 minutos. No lo compartas con nadie.");

        consent.addEvent(new ConsentEvent(consent, ConsentEventType.SENT_WHATSAPP, userId, ipBytes, userAgent,
                null));
        consent.markSent();
        return mapper.toResponse(consents.saveAndFlush(consent));
    }

    @Transactional
    public InformedConsentResponse sign(UUID consentPublicId, UUID organizationId, UUID userId,
            SignInformedConsentRequest request, String ip, String userAgent) {
        InformedConsent consent = find(consentPublicId, organizationId);
        requireSignable(consent);

        ConsentEvent otpEvent = events
                .findFirstByInformedConsentAndTypeOrderByOccurredAtDesc(consent, ConsentEventType.OTP_REQUESTED)
                .orElseThrow(() -> new ApiException(ErrorCode.OTP_NOT_REQUESTED,
                        "Todavia no se pidio un codigo de verificacion para este consentimiento"));

        Map<String, Object> detail = new LinkedHashMap<>(otpEvent.getDetail());
        Instant expiresAt = Instant.parse((String) detail.get("expiresAt"));
        int attempts = ((Number) detail.get("attempts")).intValue();

        if (Instant.now().isAfter(expiresAt)) {
            throw new ApiException(ErrorCode.OTP_EXPIRED, "El codigo vencio; pide uno nuevo");
        }
        if (attempts >= MAX_OTP_ATTEMPTS) {
            throw new ApiException(ErrorCode.OTP_ATTEMPTS_EXCEEDED,
                    "Se agotaron los intentos para este codigo; pide uno nuevo");
        }

        String expectedHash = (String) detail.get("codeHash");
        if (!expectedHash.equals(hashOtpCode(request.otpCode(), consent.getPublicId()))) {
            detail.put("attempts", attempts + 1);
            otpEvent.updateDetail(detail);
            events.saveAndFlush(otpEvent);
            throw new ApiException(ErrorCode.OTP_INVALID,
                    "Codigo incorrecto; intentos restantes: " + (MAX_OTP_ATTEMPTS - attempts - 1));
        }

        byte[] ipBytes = ipBytes(ip);
        consent.addEvent(new ConsentEvent(consent, ConsentEventType.OTP_VALIDATED, userId, ipBytes, userAgent,
                null));
        try {
            consent.sign(SignatureMethod.OTP_WHATSAPP, request.signerType(), consent.getPatientPublicId(),
                    request.signerDocumentNumber(), ipBytes, userAgent, Instant.now());
        } catch (IllegalStateException exception) {
            throw new ApiException(ErrorCode.INFORMED_CONSENT_INVALID_STATUS, exception.getMessage());
        }
        consent.addEvent(new ConsentEvent(consent, ConsentEventType.SIGNED, userId, ipBytes, userAgent, null));
        return mapper.toResponse(consents.saveAndFlush(consent));
    }

    @Transactional
    public InformedConsentResponse reject(UUID consentPublicId, UUID organizationId, UUID userId,
            RejectInformedConsentRequest request) {
        InformedConsent consent = find(consentPublicId, organizationId);
        try {
            consent.reject(request.reason());
        } catch (IllegalStateException exception) {
            throw new ApiException(ErrorCode.INFORMED_CONSENT_INVALID_STATUS, exception.getMessage());
        }
        consent.addEvent(new ConsentEvent(consent, ConsentEventType.REJECTED, userId, null, null,
                Map.of("reason", request.reason())));
        return mapper.toResponse(consents.saveAndFlush(consent));
    }

    @Transactional(readOnly = true)
    public InformedConsentResponse get(UUID consentPublicId, UUID organizationId) {
        return mapper.toResponse(find(consentPublicId, organizationId));
    }

    @Transactional(readOnly = true)
    public List<InformedConsentResponse> listByPatient(UUID patientId, UUID organizationId) {
        return consents.findAllByOrganizationPublicIdAndPatientPublicIdOrderByGeneratedAtDesc(
                organizationId, patientId).stream().map(mapper::toResponse).toList();
    }

    private void requireSignable(InformedConsent consent) {
        if (!consent.isSignable()) {
            throw new ApiException(ErrorCode.INFORMED_CONSENT_INVALID_STATUS,
                    "El consentimiento no admite esta operacion en su estado actual: " + consent.getStatus());
        }
    }

    private InformedConsent find(UUID consentPublicId, UUID organizationId) {
        return consents.findDetailedByPublicIdAndOrganization(consentPublicId, organizationId)
                .orElseThrow(() -> new ApiException(ErrorCode.INFORMED_CONSENT_NOT_FOUND,
                        "No existe un consentimiento visible con el identificador indicado"));
    }

    private String generateCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private String hashOtpCode(String code, UUID consentPublicId) {
        String salted = code + ":" + consentPublicId;
        return hashingService.sha256(new ByteArrayInputStream(salted.getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] ipBytes(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        try {
            return InetAddress.getByName(ip).getAddress();
        } catch (UnknownHostException exception) {
            return null;
        }
    }
}
