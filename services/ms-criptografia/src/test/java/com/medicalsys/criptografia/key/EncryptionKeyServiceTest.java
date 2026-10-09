package com.medicalsys.criptografia.key;

import com.medicalsys.criptografia.usage.KeyUsageAudit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.AdditionalMatchers.aryEq;

@ExtendWith(MockitoExtension.class)
class EncryptionKeyServiceTest {

    @Mock
    private EncryptionKeyRepository keyRepository;

    @Mock
    private KeyUsageAudit usageAudit;

    @Mock
    private EncryptionKey activeKey;

    private EncryptionKeyService service;

    @BeforeEach
    void setUp() {
        activeKey = new EncryptionKey(
                HexFormat.of().parseHex("000000000000400080000000000000c4"),
                null,
                EncryptionKey.Purpose.MEDICAL_RECORD,
                (short) 1,
                "env://DEMO_MASTER_KEY",
                new byte[32]);
        service = new EncryptionKeyService(keyRepository, usageAudit, "test-demo-key-not-real");
    }

    @Test
    void encryptsAndDecryptsUsingAesGcmAndAuditsBothOperations() {
        when(keyRepository.findFirstByOrganizationPublicIdIsNullAndPurposeAndStatus(
                eq(EncryptionKey.Purpose.MEDICAL_RECORD), eq(EncryptionKey.Status.ACTIVE)))
                .thenReturn(Optional.of(activeKey));
        UUID correlationId = UUID.randomUUID();
        String plaintext = "Narrativa clínica de prueba";

        String ciphertext = service.encrypt(null, EncryptionKey.Purpose.MEDICAL_RECORD,
                plaintext, "ms-historial", correlationId);
        String decrypted = service.decrypt(null, EncryptionKey.Purpose.MEDICAL_RECORD,
                ciphertext, "ms-historial", correlationId);

        assertThat(ciphertext).isNotEqualTo(plaintext);
        assertThat(decrypted).isEqualTo(plaintext);
        verify(usageAudit).recordSuccess(eq(activeKey), eq(com.medicalsys.criptografia.usage.KeyUsage.Operation.ENCRYPT),
                eq("ms-historial"), eq(correlationId));
        verify(usageAudit).recordSuccess(eq(activeKey), eq(com.medicalsys.criptografia.usage.KeyUsage.Operation.DECRYPT),
                eq("ms-historial"), eq(correlationId));
    }

    @Test
    void keepsTenantKeysSeparatedAndRejectsDecryptionWithAnotherTenantKey() {
        UUID organizationA = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
        UUID organizationB = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
        EncryptionKey tenantAKey = key("00000000-0000-4000-8000-0000000000c4", organizationA);
        EncryptionKey tenantBKey = key("00000000-0000-4000-8000-0000000000c5", organizationB);
        UUID correlationId = UUID.randomUUID();

        when(keyRepository.findFirstByOrganizationPublicIdAndPurposeAndStatus(
                aryEq(HexFormat.of().parseHex("000000000000400080000000000000a1")),
                eq(EncryptionKey.Purpose.MEDICAL_RECORD), eq(EncryptionKey.Status.ACTIVE)))
                .thenReturn(Optional.of(tenantAKey));
        when(keyRepository.findFirstByOrganizationPublicIdAndPurposeAndStatus(
                aryEq(HexFormat.of().parseHex("000000000000400080000000000000b1")),
                eq(EncryptionKey.Purpose.MEDICAL_RECORD), eq(EncryptionKey.Status.ACTIVE)))
                .thenReturn(Optional.of(tenantBKey));
        when(keyRepository.findFirstByPublicId(aryEq(HexFormat.of().parseHex("000000000000400080000000000000c4"))))
                .thenReturn(Optional.of(tenantAKey));
        when(keyRepository.findFirstByPublicId(aryEq(HexFormat.of().parseHex("000000000000400080000000000000c5"))))
                .thenReturn(Optional.of(tenantBKey));

        CipherPayload encryptedA = service.encryptForOrganization(organizationA, "Narrativa del tenant A", "ms-historial", correlationId);
        CipherPayload encryptedB = service.encryptForOrganization(organizationB, "Narrativa del tenant A", "ms-historial", correlationId);

        assertThat(encryptedA.keyPublicId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-0000000000c4"));
        assertThat(encryptedB.keyPublicId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-0000000000c5"));
        assertThat(encryptedA.ciphertext()).isNotEqualTo(encryptedB.ciphertext());
        assertThat(service.decryptByKeyPublicId(encryptedA.keyPublicId(), encryptedA.ciphertext(), "ms-historial", correlationId))
                .isEqualTo("Narrativa del tenant A");
        assertThatThrownBy(() -> service.decryptByKeyPublicId(encryptedB.keyPublicId(), encryptedA.ciphertext(),
                "ms-historial", correlationId))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private EncryptionKey key(String keyId, UUID organizationId) {
        return new EncryptionKey(
                HexFormat.of().parseHex(keyId.replace("-", "")),
                HexFormat.of().parseHex(organizationId.toString().replace("-", "")),
                EncryptionKey.Purpose.MEDICAL_RECORD,
                (short) 1,
                "env://DEMO_MASTER_KEY",
                new byte[32]);
    }
}
