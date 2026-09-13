package com.medicalsys.documental.service;

import com.medicalsys.documental.domain.DocumentType;
import com.medicalsys.documental.exception.ApiException;
import com.medicalsys.documental.exception.ErrorCode;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DocumentFilePolicy {

    private static final Set<String> PDF = Set.of("application/pdf");
    private static final Set<String> IMAGES = Set.of(
            "image/jpeg", "image/png", "image/tiff", "application/dicom", "application/octet-stream");
    private static final Set<String> XML = Set.of("application/xml", "text/xml");
    private static final Set<String> OTHER = Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/tiff", "application/dicom",
            "application/octet-stream", "application/xml", "text/xml", "text/plain", "text/csv");

    public String validateAndNormalize(DocumentType type, String contentType) {
        String normalized = contentType == null ? "" : contentType.split(";", 2)[0]
                .trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || !allowedFor(type).contains(normalized)) {
            throw new ApiException(ErrorCode.FILE_TYPE_NOT_ALLOWED,
                    "El tipo de contenido no esta permitido para la clasificacion " + type);
        }
        return normalized;
    }

    private Set<String> allowedFor(DocumentType type) {
        return switch (type) {
            case X_RAY, CT_SCAN, MRI, ULTRASOUND, DICOM_STUDY, CLINICAL_PHOTO, SIGNATURE_IMAGE -> IMAGES;
            case LAB_RESULT, MEDICAL_REPORT, INFORMED_CONSENT, PRESCRIPTION_PDF, INVOICE_PDF -> PDF;
            case INVOICE_XML -> XML;
            case IDENTITY_DOCUMENT -> Set.of("application/pdf", "image/jpeg", "image/png", "image/tiff");
            case OTHER -> OTHER;
        };
    }
}
