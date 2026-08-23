package com.medicalsys.historial.service;

import com.medicalsys.historial.domain.model.ClinicalEncounter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Calcula el SHA-256 que sella el contenido clinico al firmar.
 *
 * <p>El hash se construye sobre el ciphertext persistido y los datos estructurales del encuentro,
 * nunca sobre el texto en claro: asi puede recalcularse y verificarse sin descifrar nada, y
 * cualquier alteracion posterior de la narrativa rompe el sello.
 */
@Component
public class ContentHasher {

    private static final String ALGORITHM = "SHA-256";
    private static final char FIELD_SEPARATOR = '|';

    /** Sello del encuentro completo en el momento de la firma. */
    public String hashEncounter(ClinicalEncounter encounter) {
        StringBuilder payload = new StringBuilder()
                .append(encounter.getPublicId()).append(FIELD_SEPARATOR)
                .append(encounter.getOrganizationPublicId()).append(FIELD_SEPARATOR)
                .append(encounter.getMedicalRecord().getPublicId()).append(FIELD_SEPARATOR)
                .append(encounter.getHealthProfessionalPublicId()).append(FIELD_SEPARATOR)
                .append(encounter.getType()).append(FIELD_SEPARATOR)
                .append(encounter.getStartsAt()).append(FIELD_SEPARATOR)
                .append(encounter.getEndsAt()).append(FIELD_SEPARATOR)
                .append(encounter.getEncryptionKeyPublicId()).append(FIELD_SEPARATOR);
        appendCiphertext(payload, encounter.getEncrypted());
        appendCiphertext(payload, encounter.getEncryptedSubjectiveNotes());
        appendCiphertext(payload, encounter.getEncryptedObjectiveNotes());
        appendCiphertext(payload, encounter.getEncryptedAssessment());
        appendCiphertext(payload, encounter.getEncryptedPlan());
        return hash(payload.toString());
    }

    /** Sello de un texto ya cifrado, usado por los addendums. */
    public String hashCiphertext(byte[] ciphertext) {
        StringBuilder payload = new StringBuilder();
        appendCiphertext(payload, ciphertext);
        return hash(payload.toString());
    }

    private void appendCiphertext(StringBuilder payload, byte[] value) {
        payload.append(value == null ? "" : Base64.getEncoder().encodeToString(value)).append(FIELD_SEPARATOR);
    }

    private String hash(String payload) {
        try {
            byte[] digest = MessageDigest.getInstance(ALGORITHM).digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no ofrece " + ALGORITHM, e);
        }
    }
}
