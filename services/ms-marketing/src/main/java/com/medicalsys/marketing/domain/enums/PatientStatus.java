package com.medicalsys.marketing.domain.enums;

/** Estado del registro del paciente, replicado de patients_db. */
public enum PatientStatus {
    ACTIVE,
    INACTIVE,
    DECEASED;

    /** Solo un paciente activo entra en una audiencia: no se envia publicidad a un fallecido. */
    public boolean isTargetable() {
        return this == ACTIVE;
    }
}
