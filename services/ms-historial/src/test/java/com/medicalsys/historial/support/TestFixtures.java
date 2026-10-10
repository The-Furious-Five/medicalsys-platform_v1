package com.medicalsys.historial.support;

import com.medicalsys.historial.common.security.Role;
import com.medicalsys.historial.common.tenant.RequestContext;
import com.medicalsys.historial.domain.enums.EncounterType;
import com.medicalsys.historial.domain.model.ClinicalEncounter;
import com.medicalsys.historial.domain.model.MedicalRecord;
import com.medicalsys.historial.service.NarrativeAccessPolicy;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

/** Datos ficticios reutilizables por las pruebas. Ningun valor corresponde a una persona real. */
public final class TestFixtures {

    public static final UUID ORGANIZATION_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    public static final UUID OTHER_ORGANIZATION_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a9");
    public static final UUID PATIENT_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    public static final UUID BRANCH_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a2");
    public static final UUID PROFESSIONAL_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
    public static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000f1");
    public static final UUID APPOINTMENT_ID = UUID.fromString("00000000-0000-4000-8000-000000000a11");
    public static final UUID KEY_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c4");
    public static final UUID DOCUMENT_ID = UUID.fromString("00000000-0000-4000-8000-000000000d99");

    private TestFixtures() {
    }

    /** Contexto de un profesional autenticado del tenant de demostracion. */
    public static RequestContext physicianContext() {
        // Como en ms-identidad: el medico tiene clinical.narrative.read y la recepcion no.
        return new RequestContext(ORGANIZATION_ID, USER_ID, Set.of(Role.PHYSICIAN),
                Set.of(NarrativeAccessPolicy.NARRATIVE_PERMISSION));
    }

    /** Contexto de una recepcionista: ve el encuentro pero no su narrativa. */
    public static RequestContext receptionistContext() {
        return new RequestContext(ORGANIZATION_ID, USER_ID, Set.of(Role.RECEPTIONIST));
    }

    /** Expediente persistido, con identificador tecnico asignado. */
    public static MedicalRecord medicalRecord() {
        MedicalRecord record = MedicalRecord.open(ORGANIZATION_ID, PATIENT_ID, "EXP-2026-000001", BRANCH_ID,
                KEY_ID);
        ReflectionTestUtils.setField(record, "id", 1L);
        return record;
    }

    /** Encuentro en borrador ligado a la cita de demostracion. */
    public static ClinicalEncounter draftEncounter(MedicalRecord record) {
        ClinicalEncounter encounter = ClinicalEncounter.open(record, APPOINTMENT_ID, PROFESSIONAL_ID, BRANCH_ID,
                EncounterType.INITIAL_CONSULTATION, Instant.parse("2026-08-24T14:00:00Z"), KEY_ID);
        ReflectionTestUtils.setField(encounter, "id", 10L);
        return encounter;
    }
}
