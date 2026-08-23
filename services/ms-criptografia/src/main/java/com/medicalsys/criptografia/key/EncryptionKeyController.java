package com.medicalsys.criptografia.key;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoints internos de criptografía.
 * Solo el gateway y otros microservicios deben acceder a /internal/v1/.
 *
 * POST /internal/v1/crypto/encrypt           → cifra texto plano
 * POST /internal/v1/crypto/decrypt           → descifra texto cifrado
 * GET  /internal/v1/crypto/keys/active       → metadatos de clave activa
 */
@RestController
@RequestMapping("/internal/v1/crypto")
public class EncryptionKeyController {

    private final EncryptionKeyService service;

    public EncryptionKeyController(EncryptionKeyService service) {
        this.service = service;
    }

    /**
     * Cifra texto plano.
     * Body: { "organizationId": "hex-uuid|null", "purpose": "MEDICAL_RECORD",
     *         "plaintext": "...", "requestingService": "ms-historial",
     *         "correlationId": "uuid" }
     * Respuesta: { "ciphertext": "base64..." }
     */
    @PostMapping("/encrypt")
    public ResponseEntity<Map<String, String>> encrypt(
            @RequestBody EncryptRequest req) {

        String ciphertext = service.encrypt(
                parseOrgId(req.organizationId()),
                EncryptionKey.Purpose.valueOf(req.purpose()),
                req.plaintext(),
                req.requestingService(),
                UUID.fromString(req.correlationId())
        );
        return ResponseEntity.ok(Map.of("ciphertext", ciphertext));
    }

    /**
     * Descifra texto cifrado.
     * Body: { "organizationId": "hex-uuid|null", "purpose": "MEDICAL_RECORD",
     *         "ciphertext": "base64...", "requestingService": "ms-historial",
     *         "correlationId": "uuid" }
     * Respuesta: { "plaintext": "..." }
     */
    @PostMapping("/decrypt")
    public ResponseEntity<Map<String, String>> decrypt(
            @RequestBody DecryptRequest req) {

        String plaintext = service.decrypt(
                parseOrgId(req.organizationId()),
                EncryptionKey.Purpose.valueOf(req.purpose()),
                req.ciphertext(),
                req.requestingService(),
                UUID.fromString(req.correlationId())
        );
        return ResponseEntity.ok(Map.of("plaintext", plaintext));
    }

    /**
     * Metadatos de la clave activa (SIN material de clave).
     * GET /internal/v1/crypto/keys/active?purpose=MEDICAL_RECORD&organizationId=hex
     */
    @GetMapping("/keys/active")
    public ResponseEntity<EncryptionKeyDto> findActiveKey(
            @RequestParam String purpose,
            @RequestParam(required = false) String organizationId) {

        EncryptionKeyDto dto = service.findActiveKey(
                parseOrgId(organizationId),
                EncryptionKey.Purpose.valueOf(purpose)
        );
        return ResponseEntity.ok(dto);
    }

    // ---------------------------------------------------------------
    // Request records
    // ---------------------------------------------------------------

    public record EncryptRequest(
            String organizationId,
            String purpose,
            String plaintext,
            String requestingService,
            String correlationId
    ) {}

    public record DecryptRequest(
            String organizationId,
            String purpose,
            String ciphertext,
            String requestingService,
            String correlationId
    ) {}

    // ---------------------------------------------------------------
    // Utilidades privadas
    // ---------------------------------------------------------------

    private static byte[] parseOrgId(String hexOrNull) {
        if (hexOrNull == null || hexOrNull.isBlank()
                || "null".equalsIgnoreCase(hexOrNull)) {
            return null;
        }
        String clean = hexOrNull.replace("-", "");
        return HexFormat.of().parseHex(clean);
    }
}
