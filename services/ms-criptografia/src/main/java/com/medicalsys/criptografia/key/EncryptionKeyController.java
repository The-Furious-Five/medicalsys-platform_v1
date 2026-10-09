package com.medicalsys.criptografia.key;

import com.medicalsys.criptografia.common.ServiceAuthInterceptor;
import com.medicalsys.criptografia.common.UuidBinary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API interna legacy en ingles. El contrato congelado con Historial vive en
 * {@link NarrativeCryptoController}.
 */
@Validated
@RestController
@RequestMapping("/internal/v1/crypto")
public class EncryptionKeyController {

    private final EncryptionKeyService service;

    public EncryptionKeyController(EncryptionKeyService service) {
        this.service = service;
    }

    @PostMapping("/encrypt")
    public ResponseEntity<Map<String, String>> encrypt(@Valid @RequestBody EncryptRequest req,
            HttpServletRequest servletRequest) {
        String ciphertext = service.encrypt(
                UuidBinary.toBytes(UuidBinary.parseFlexible(req.organizationId())),
                parsePurpose(req.purpose()),
                req.plaintext(),
                verifiedService(servletRequest),
                parseUuid(req.correlationId())
        );
        return ResponseEntity.ok(Map.of("ciphertext", ciphertext));
    }

    @PostMapping("/decrypt")
    public ResponseEntity<Map<String, String>> decrypt(@Valid @RequestBody DecryptRequest req,
            HttpServletRequest servletRequest) {
        String plaintext = service.decrypt(
                UuidBinary.toBytes(UuidBinary.parseFlexible(req.organizationId())),
                parsePurpose(req.purpose()),
                req.ciphertext(),
                verifiedService(servletRequest),
                parseUuid(req.correlationId())
        );
        return ResponseEntity.ok(Map.of("plaintext", plaintext));
    }

    @GetMapping("/keys/active")
    public ResponseEntity<EncryptionKeyDto> findActiveKey(
            @RequestParam String purpose,
            @RequestParam(required = false) String organizationId) {
        return ResponseEntity.ok(service.findActiveKey(
                UuidBinary.toBytes(UuidBinary.parseFlexible(organizationId)),
                parsePurpose(purpose)));
    }

    public record EncryptRequest(
            String organizationId,
            @NotBlank String purpose,
            @NotBlank String plaintext,
            @NotBlank String correlationId
    ) {
    }

    public record DecryptRequest(
            String organizationId,
            @NotBlank String purpose,
            @NotBlank String ciphertext,
            @NotBlank String correlationId
    ) {
    }

    private static String verifiedService(HttpServletRequest servletRequest) {
        return (String) servletRequest.getAttribute(ServiceAuthInterceptor.VERIFIED_SERVICE_ATTRIBUTE);
    }

    private static EncryptionKey.Purpose parsePurpose(String purpose) {
        try {
            return EncryptionKey.Purpose.valueOf(purpose);
        } catch (RuntimeException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        }
    }
}
