package com.medicalsys.criptografia.usage;

import com.medicalsys.criptografia.common.UuidBinary;
import com.medicalsys.criptografia.key.EncryptionKey;
import com.medicalsys.criptografia.key.EncryptionKeyRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KeyUsageAudit {

    private final EncryptionKeyRepository keyRepository;
    private final KeyUsageRepository usageRepository;

    public KeyUsageAudit(EncryptionKeyRepository keyRepository, KeyUsageRepository usageRepository) {
        this.keyRepository = keyRepository;
        this.usageRepository = usageRepository;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordSuccess(EncryptionKey key, KeyUsage.Operation operation, String requestingService,
            UUID correlationId) {
        save(key, operation, requestingService, correlationId, true, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long encryptionKeyId, KeyUsage.Operation operation, String requestingService,
            UUID correlationId, String errorCode) {
        EncryptionKey key = keyRepository.getReferenceById(encryptionKeyId);
        save(key, operation, requestingService, correlationId, false, errorCode);
    }

    private void save(EncryptionKey key, KeyUsage.Operation operation, String requestingService,
            UUID correlationId, boolean success, String errorCode) {
        usageRepository.save(new KeyUsage(
                key,
                operation,
                requestingService == null || requestingService.isBlank() ? "unknown" : requestingService,
                "TEXT",
                null,
                UuidBinary.toBytes(correlationId == null ? UUID.randomUUID() : correlationId),
                success ? KeyUsage.Result.SUCCESS : KeyUsage.Result.FAILURE,
                errorCode,
                Instant.now()
        ));
    }
}
