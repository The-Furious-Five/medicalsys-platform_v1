package com.medicalsys.historial.service;

import com.medicalsys.historial.client.NarrativeCipher;
import com.medicalsys.historial.common.tenant.RequestContext;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Decide quien puede leer la narrativa clinica descifrada.
 *
 * <p>Una recepcionista necesita ver que existe un encuentro y su estado, pero no su contenido
 * clinico. El aislamiento por organizacion ya se aplico en la consulta; esta politica agrega el
 * filtro sobre el contenido sensible con el permiso {@value #NARRATIVE_PERMISSION}, que ms-identidad
 * asigna a cada rol (RBAC-03) en lugar de una lista fija de roles en el codigo.
 */
@Component
public class NarrativeAccessPolicy {

    public static final String NARRATIVE_PERMISSION = "clinical.narrative.read";

    private final NarrativeCipher cipher;

    public NarrativeAccessPolicy(NarrativeCipher cipher) {
        this.cipher = cipher;
    }

    /** Indica si la identidad en curso puede leer contenido clinico descifrado. */
    public boolean canReadNarrative(RequestContext context) {
        return context.hasPermission(NARRATIVE_PERMISSION);
    }

    /**
     * Descifra un valor solo si el permiso lo autoriza.
     *
     * @return el texto en claro, o {@code null} si el permiso no autoriza o no hay contenido
     */
    public String reveal(RequestContext context, UUID keyPublicId, byte[] ciphertext) {
        if (ciphertext == null || ciphertext.length == 0 || !canReadNarrative(context)) {
            return null;
        }
        return cipher.decrypt(keyPublicId, ciphertext);
    }
}
