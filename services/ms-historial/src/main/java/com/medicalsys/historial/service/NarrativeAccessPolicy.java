package com.medicalsys.historial.service;

import com.medicalsys.historial.client.NarrativeCipher;
import com.medicalsys.historial.common.security.Role;
import com.medicalsys.historial.common.tenant.RequestContext;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Decide quien puede leer la narrativa clinica descifrada.
 *
 * <p>Una recepcionista necesita ver que existe un encuentro y su estado, pero no su contenido
 * clinico. El aislamiento por organizacion ya se aplico en la consulta; esta politica agrega el
 * filtro por rol sobre el contenido sensible.
 */
@Component
public class NarrativeAccessPolicy {

    private static final Set<Role> CLINICAL_READERS = Set.of(Role.PHYSICIAN, Role.ADMINISTRATOR,
            Role.PLATFORM_ADMIN);

    private final NarrativeCipher cipher;

    public NarrativeAccessPolicy(NarrativeCipher cipher) {
        this.cipher = cipher;
    }

    /** Indica si la identidad en curso puede leer contenido clinico descifrado. */
    public boolean canReadNarrative(RequestContext context) {
        return context.roles().stream().anyMatch(CLINICAL_READERS::contains);
    }

    /**
     * Descifra un valor solo si el rol lo permite.
     *
     * @return el texto en claro, o {@code null} si el rol no autoriza o no hay contenido
     */
    public String reveal(RequestContext context, UUID keyPublicId, byte[] ciphertext) {
        if (ciphertext == null || ciphertext.length == 0 || !canReadNarrative(context)) {
            return null;
        }
        return cipher.decrypt(keyPublicId, ciphertext);
    }
}
