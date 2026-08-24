package com.medicalsys.criptografia.key;

import com.medicalsys.criptografia.usage.KeyUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class EncryptionKeyServiceTest {

    @Mock
    private EncryptionKeyRepository keyRepository;

    @Mock
    private KeyUsageRepository usageRepository;

    @Mock
    private EncryptionKey activeKey;

    @InjectMocks
    private EncryptionKeyService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "demoMasterKey", "test-demo-key-not-real");
        when(keyRepository.findFirstByOrganizationPublicIdIsNullAndPurposeAndStatus(
                eq(EncryptionKey.Purpose.MEDICAL_RECORD), eq(EncryptionKey.Status.ACTIVE)))
                .thenReturn(Optional.of(activeKey));
    }

    @Test
    void encryptsAndDecryptsUsingAesGcmAndAuditsBothOperations() {
        UUID correlationId = UUID.randomUUID();
        String plaintext = "Narrativa clínica de prueba";

        String ciphertext = service.encrypt(null, EncryptionKey.Purpose.MEDICAL_RECORD,
                plaintext, "ms-historial", correlationId);
        String decrypted = service.decrypt(null, EncryptionKey.Purpose.MEDICAL_RECORD,
                ciphertext, "ms-historial", correlationId);

        assertThat(ciphertext).isNotEqualTo(plaintext);
        assertThat(decrypted).isEqualTo(plaintext);
        verify(usageRepository, times(2)).save(any());
    }
}
