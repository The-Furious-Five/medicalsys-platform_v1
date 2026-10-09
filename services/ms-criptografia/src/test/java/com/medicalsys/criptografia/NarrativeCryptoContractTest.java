package com.medicalsys.criptografia;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medicalsys.criptografia.common.UuidBinary;
import com.medicalsys.criptografia.key.EncryptionKey;
import com.medicalsys.criptografia.key.EncryptionKeyRepository;
import com.medicalsys.criptografia.usage.KeyUsageRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NarrativeCryptoContractTest {

    private static final UUID TENANT_A = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    private static final UUID KEY_A = UUID.fromString("00000000-0000-4000-8000-0000000000c4");
    private static final UUID KEY_B = UUID.fromString("00000000-0000-4000-8000-0000000000c5");
    private static final String CORRELATION = "11111111-2222-4333-8444-555555555555";
    private static final String SHARED_SECRET = "test-shared-secret-not-real";

    @Autowired private MockMvc mockMvc;
    @Autowired private EncryptionKeyRepository keyRepository;
    @Autowired private KeyUsageRepository keyUsageRepository;

    @BeforeEach
    void seedTenantKeys() {
        keyUsageRepository.deleteAll();
        keyRepository.deleteAll();
        keyRepository.save(key(KEY_A, TENANT_A));
        keyRepository.save(key(KEY_B, TENANT_B));
    }

    @Test
    void historialContractEncryptsAndDecryptsForTheSameTenant() throws Exception {
        String encryptedJson = mockMvc.perform(post("/internal/v1/cifrado")
                        .header("X-Correlation-Id", CORRELATION)
                        .header("X-Service-Name", "ms-historial")
                        .header("X-Service-Secret", SHARED_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + TENANT_A + "\",\"plaintext\":\"Narrativa demo\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", CORRELATION))
                .andExpect(jsonPath("$.keyPublicId").value(KEY_A.toString()))
                .andExpect(jsonPath("$.ciphertext").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String keyPublicId = jsonValue(encryptedJson, "keyPublicId");
        String ciphertext = jsonValue(encryptedJson, "ciphertext");
        mockMvc.perform(post("/internal/v1/descifrado")
                        .header("X-Correlation-Id", CORRELATION)
                        .header("X-Service-Name", "ms-historial")
                        .header("X-Service-Secret", SHARED_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyPublicId\":\"" + keyPublicId
                                + "\",\"ciphertext\":\"" + ciphertext + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plaintext").value("Narrativa demo"));
    }

    @Test
    void rejectsCiphertextWithAKeyFromAnotherTenantAndUsesCommonError() throws Exception {
        String encryptedJson = mockMvc.perform(post("/internal/v1/cifrado")
                        .header("X-Correlation-Id", CORRELATION)
                        .header("X-Service-Name", "ms-historial")
                        .header("X-Service-Secret", SHARED_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + TENANT_A + "\",\"plaintext\":\"Narrativa demo\"}"))
                .andReturn().getResponse().getContentAsString();
        String ciphertext = jsonValue(encryptedJson, "ciphertext");

        mockMvc.perform(post("/internal/v1/descifrado")
                        .header("X-Correlation-Id", CORRELATION)
                        .header("X-Service-Name", "ms-historial")
                        .header("X-Service-Secret", SHARED_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyPublicId\":\"" + KEY_B + "\",\"ciphertext\":\"" + ciphertext + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("X-Correlation-Id", CORRELATION))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("DECRYPT_FAILED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").value(CORRELATION))
                .andExpect(jsonPath("$.path").value("/internal/v1/descifrado"));
    }

    @Test
    void rejectsCallersWithoutServiceIdentity() throws Exception {
        mockMvc.perform(post("/internal/v1/cifrado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + TENANT_A + "\",\"plaintext\":\"Narrativa demo\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SERVICE_AUTH_REQUIRED"));
    }

    @Test
    void rejectsCallersWithTheWrongSecret() throws Exception {
        mockMvc.perform(post("/internal/v1/cifrado")
                        .header("X-Service-Name", "ms-historial")
                        .header("X-Service-Secret", "no-es-el-secreto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + TENANT_A + "\",\"plaintext\":\"Narrativa demo\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SERVICE_AUTH_INVALID"));
    }

    private static EncryptionKey key(UUID keyId, UUID organizationId) {
        return new EncryptionKey(UuidBinary.toBytes(keyId), UuidBinary.toBytes(organizationId),
                EncryptionKey.Purpose.MEDICAL_RECORD, (short) 1, "env://DEMO_MASTER_KEY", new byte[32]);
    }

    private static String jsonValue(String json, String field) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\\"" + field + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
                .matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("Falta el campo " + field + " en la respuesta: " + json);
        }
        return matcher.group(1);
    }
}
