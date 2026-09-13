package com.medicalsys.criptografia.key;

import com.medicalsys.criptografia.common.CorrelationIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrato congelado con ms-historial (docs/contracts + contracts.md de Historial).
 *
 * <pre>
 * POST /internal/v1/cifrado    {"organizationId": uuid, "plaintext": string}
 *                           -&gt; {"keyPublicId": uuid, "ciphertext": base64}
 * POST /internal/v1/descifrado {"keyPublicId": uuid, "ciphertext": base64}
 *                           -&gt; {"plaintext": string}
 * </pre>
 */
@Validated
@RestController
public class NarrativeCryptoController {

    private final EncryptionKeyService service;

    public NarrativeCryptoController(EncryptionKeyService service) {
        this.service = service;
    }

    @PostMapping("/internal/v1/cifrado")
    public ResponseEntity<EncryptResponse> encrypt(@Valid @RequestBody EncryptRequest request) {
        CipherPayload payload = service.encryptForOrganization(
                request.organizationId(),
                request.plaintext(),
                "ms-historial",
                correlationId());
        return ResponseEntity.ok(new EncryptResponse(payload.keyPublicId(), payload.ciphertext()));
    }

    @PostMapping("/internal/v1/descifrado")
    public ResponseEntity<DecryptResponse> decrypt(@Valid @RequestBody DecryptRequest request) {
        String plaintext = service.decryptByKeyPublicId(
                request.keyPublicId(),
                request.ciphertext(),
                "ms-historial",
                correlationId());
        return ResponseEntity.ok(new DecryptResponse(plaintext));
    }

    public record EncryptRequest(@NotNull UUID organizationId, @NotBlank String plaintext) {
    }

    public record EncryptResponse(UUID keyPublicId, String ciphertext) {
    }

    public record DecryptRequest(@NotNull UUID keyPublicId, @NotBlank String ciphertext) {
    }

    public record DecryptResponse(String plaintext) {
    }

    private static UUID correlationId() {
        String value = MDC.get(CorrelationIdFilter.MDC_KEY);
        try {
            return value == null ? UUID.randomUUID() : UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return UUID.nameUUIDFromBytes(value.getBytes());
        }
    }
}
