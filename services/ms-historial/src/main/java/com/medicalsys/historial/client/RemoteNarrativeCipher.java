package com.medicalsys.historial.client;

import com.medicalsys.historial.common.error.DependencyUnavailableException;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Delega el cifrado de la narrativa en ms-criptografia.
 *
 * <p>Contrato consumido (rutas internas, red medicalsys-interna):
 * <pre>
 * POST /internal/v1/cifrado    {"organizationId": uuid, "plaintext": string}
 *                           -> {"keyPublicId": uuid, "ciphertext": base64}
 * POST /internal/v1/descifrado {"keyPublicId": uuid, "ciphertext": base64}
 *                           -> {"plaintext": string}
 * </pre>
 *
 * <p>Un fallo de la dependencia se traduce a 503 con el correlationId intacto: no se reintenta un
 * POST no idempotente ni se oculta el error tras un valor por defecto.
 */
@Component
@ConditionalOnProperty(prefix = "medicalsys.crypto", name = "mode", havingValue = "REMOTE")
public class RemoteNarrativeCipher implements NarrativeCipher {

    private static final String ENCRYPT_PATH = "/internal/v1/cifrado";
    private static final String DECRYPT_PATH = "/internal/v1/descifrado";

    private final RestClient restClient;

    public RemoteNarrativeCipher(@Qualifier("cryptoRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public CipherSession open(UUID organizationId) {
        return new RemoteSession(organizationId);
    }

    @Override
    public String decrypt(UUID keyPublicId, byte[] ciphertext) {
        if (ciphertext == null || ciphertext.length == 0) {
            return null;
        }
        DecryptResponse response = call(DECRYPT_PATH,
                new DecryptRequest(keyPublicId, Base64.getEncoder().encodeToString(ciphertext)),
                DecryptResponse.class);
        return response.plaintext();
    }

    private <T> T call(String path, Object body, Class<T> responseType) {
        try {
            T response = restClient.post().uri(path).body(body).retrieve().body(responseType);
            if (response == null) {
                throw new DependencyUnavailableException(
                        "ms-criptografia respondio sin cuerpo a una operacion de cifrado", null);
            }
            return response;
        } catch (RestClientException e) {
            throw new DependencyUnavailableException(
                    "ms-criptografia no esta disponible para operar la narrativa clinica", e);
        }
    }

    private record EncryptRequest(UUID organizationId, String plaintext) {
    }

    private record EncryptResponse(UUID keyPublicId, String ciphertext) {
    }

    private record DecryptRequest(UUID keyPublicId, String ciphertext) {
    }

    private record DecryptResponse(String plaintext) {
    }

    /**
     * Sesion que memoriza la clave devuelta por el primer cifrado.
     *
     * <p>Todos los campos del mismo encuentro deben compartir {@code encryption_key_public_id}, tal
     * como exige el diccionario.
     */
    private final class RemoteSession implements CipherSession {

        private final UUID organizationId;
        private UUID resolvedKeyPublicId;

        private RemoteSession(UUID organizationId) {
            this.organizationId = organizationId;
        }

        @Override
        public UUID keyPublicId() {
            if (resolvedKeyPublicId == null) {
                // Sin campos cifrados aun se necesita la clave vigente: se resuelve con una marca minima.
                encrypt("-");
            }
            return resolvedKeyPublicId;
        }

        @Override
        public byte[] encrypt(String plaintext) {
            if (plaintext == null || plaintext.isBlank()) {
                return null;
            }
            EncryptResponse response = call(ENCRYPT_PATH, new EncryptRequest(organizationId, plaintext),
                    EncryptResponse.class);
            resolvedKeyPublicId = response.keyPublicId();
            return Base64.getDecoder().decode(response.ciphertext());
        }
    }
}
