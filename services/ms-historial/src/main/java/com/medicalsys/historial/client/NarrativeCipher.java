package com.medicalsys.historial.client;

import java.util.UUID;

/**
 * Puerto de cifrado de narrativa clinica.
 *
 * <p>ms-historial nunca persiste texto clinico en claro: guarda ciphertext y el UUID de la clave que
 * lo produjo. La operacion de descifrado pertenece a ms-criptografia; historial solo la invoca.
 */
public interface NarrativeCipher {

    /**
     * Resuelve la clave vigente de la organizacion y devuelve una sesion reutilizable.
     *
     * <p>Un encuentro cifra varios campos con la misma clave, por lo que la resolucion se hace una
     * sola vez por operacion.
     *
     * @param organizationId tenant propietario del dato
     */
    CipherSession open(UUID organizationId);

    /**
     * Descifra un valor previamente producido por este puerto.
     *
     * @param keyPublicId clave declarada en la fila persistida
     * @param ciphertext  valor cifrado; {@code null} devuelve {@code null}
     */
    String decrypt(UUID keyPublicId, byte[] ciphertext);

    /** Sesion de cifrado ligada a una clave concreta. */
    interface CipherSession {

        /** UUID de la clave usada; se persiste en la columna {@code encryption_key_public_id}. */
        UUID keyPublicId();

        /**
         * Cifra un texto clinico.
         *
         * @param plaintext texto en claro; {@code null} o vacio devuelve {@code null}
         */
        byte[] encrypt(String plaintext);
    }
}
