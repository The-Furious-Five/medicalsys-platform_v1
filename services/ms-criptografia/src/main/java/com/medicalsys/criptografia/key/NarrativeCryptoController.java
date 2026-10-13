package com.medicalsys.criptografia.key;

import com.medicalsys.criptografia.common.CorrelationIdFilter;
import com.medicalsys.criptografia.common.InternalEndpoint;
import com.medicalsys.criptografia.common.ServiceAuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
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
    @InternalEndpoint
    public ResponseEntity<EncryptResponse> encrypt(@Valid @RequestBody EncryptRequest request,
            HttpServletRequest servletRequest) {
        CipherPayload payload = service.encryptForOrganization(
                request.organizationId(),
                request.plaintext(),
                verifiedService(servletRequest),
                correlationId());
        return ResponseEntity.ok(new EncryptResponse(payload.keyPublicId(), payload.ciphertext()));
    }

    @PostMapping("/internal/v1/descifrado")
    @InternalEndpoint
    public ResponseEntity<DecryptResponse> decrypt(@Valid @RequestBody DecryptRequest request,
            HttpServletRequest servletRequest) {
        String plaintext = service.decryptByKeyPublicId(
                request.keyPublicId(),
                request.ciphertext(),
                verifiedService(servletRequest),
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

    private static String verifiedService(HttpServletRequest servletRequest) {
        return (String) servletRequest.getAttribute(ServiceAuthInterceptor.VERIFIED_SERVICE_ATTRIBUTE);
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
