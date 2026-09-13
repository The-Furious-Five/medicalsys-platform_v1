package com.medicalsys.historial.support;

import com.medicalsys.historial.client.NarrativeCipher;
import com.medicalsys.historial.mapper.ClinicalEncounterMapper;
import com.medicalsys.historial.mapper.MedicalRecordMapper;
import com.medicalsys.historial.mapper.PrescriptionMapper;
import com.medicalsys.historial.mapper.StudyOrderMapper;
import com.medicalsys.historial.service.NarrativeAccessPolicy;

/**
 * Mappers reales para las pruebas unitarias de servicio.
 *
 * <p>Se usan los de verdad en vez de dobles: son deterministas y asi cada prueba de servicio cubre
 * tambien la traduccion al contrato y la politica de acceso a la narrativa.
 */
public final class TestMappers {

    private TestMappers() {
    }

    public static MedicalRecordMapper medicalRecord(NarrativeCipher cipher) {
        return new MedicalRecordMapper(policy(cipher));
    }

    public static ClinicalEncounterMapper clinicalEncounter(NarrativeCipher cipher) {
        return new ClinicalEncounterMapper(policy(cipher));
    }

    public static PrescriptionMapper prescription(NarrativeCipher cipher) {
        return new PrescriptionMapper(policy(cipher));
    }

    public static StudyOrderMapper studyOrder(NarrativeCipher cipher) {
        return new StudyOrderMapper(policy(cipher));
    }

    private static NarrativeAccessPolicy policy(NarrativeCipher cipher) {
        return new NarrativeAccessPolicy(cipher);
    }
}
