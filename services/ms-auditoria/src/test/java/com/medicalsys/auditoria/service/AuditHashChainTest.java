package com.medicalsys.auditoria.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * El encadenamiento solo sirve si es reproducible y sensible.
 *
 * <p>Reproducible: los mismos datos tienen que dar el mismo hash en cualquier ejecucion, o la
 * verificacion denunciaria manipulaciones inexistentes. Sensible: cambiar cualquier campo, incluido
 * el hash previo, tiene que cambiar el resultado, o la manipulacion pasaria inadvertida.
 */
class AuditHashChainTest {

    private static final UUID EVENT = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ORGANIZATION = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ACTOR = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final Instant OCCURRED = Instant.parse("2026-09-01T10:15:30.123456Z");
    private static final Instant RECORDED = Instant.parse("2026-09-01T10:15:31.000000Z");

    private final AuditHashChain chain = new AuditHashChain();

    @Test
    void producesTheSameHashForTheSameContent() {
        assertEquals(hash(null, "PATIENT_REGISTERED", "Recepcion Demo"),
                hash(null, "PATIENT_REGISTERED", "Recepcion Demo"));
    }

    @Test
    void changesTheHashWhenTheActionIsAltered() {
        assertNotEquals(hash(null, "PATIENT_REGISTERED", "Recepcion Demo"),
                hash(null, "PATIENT_DELETED", "Recepcion Demo"));
    }

    @Test
    void changesTheHashWhenTheActorIsAltered() {
        assertNotEquals(hash(null, "PATIENT_REGISTERED", "Recepcion Demo"),
                hash(null, "PATIENT_REGISTERED", "Otra Persona"));
    }

    /** Un evento identico en distinta posicion de la cadena no puede reutilizar su hash. */
    @Test
    void changesTheHashWhenThePreviousLinkChanges() {
        assertNotEquals(hash(null, "PATIENT_REGISTERED", "Recepcion Demo"),
                hash("a".repeat(64), "PATIENT_REGISTERED", "Recepcion Demo"));
    }

    /**
     * Un campo nulo y una cadena vacia describen situaciones distintas.
     *
     * <p>Si el hash los confundiera, cambiar "sin motivo declarado" por "motivo en blanco" seria una
     * edicion indetectable.
     */
    @Test
    void distinguishesNullFromEmptyText() {
        String withNullReason = chain.hash(null, EVENT, ORGANIZATION, 1, OCCURRED, RECORDED,
                "ms-pacientes", "PATIENT_REGISTERED", "PATIENT_DATA", "INFO", "SUCCESS", ACTOR,
                "Recepcion Demo", "RECEPTIONIST", "10.0.0.1", "PATIENT", null, null, null,
                "corr-1", "POST", "/api/v1/patients", 201, null, null);
        String withEmptyReason = chain.hash(null, EVENT, ORGANIZATION, 1, OCCURRED, RECORDED,
                "ms-pacientes", "PATIENT_REGISTERED", "PATIENT_DATA", "INFO", "SUCCESS", ACTOR,
                "Recepcion Demo", "RECEPTIONIST", "10.0.0.1", "PATIENT", null, null, null,
                "corr-1", "POST", "/api/v1/patients", 201, "", null);

        assertNotEquals(withNullReason, withEmptyReason);
    }

    @Test
    void producesASixtyFourCharacterHexDigest() {
        String hash = hash(null, "PATIENT_REGISTERED", "Recepcion Demo");

        assertEquals(64, hash.length());
        assertEquals(hash.toLowerCase(java.util.Locale.ROOT), hash);
    }

    private String hash(String previousHash, String action, String actorDisplayName) {
        return chain.hash(previousHash, EVENT, ORGANIZATION, 1, OCCURRED, RECORDED, "ms-pacientes",
                action, "PATIENT_DATA", "INFO", "SUCCESS", ACTOR, actorDisplayName, "RECEPTIONIST",
                "10.0.0.1", "PATIENT", null, null, null, "corr-1", "POST", "/api/v1/patients", 201,
                null, null);
    }
}
