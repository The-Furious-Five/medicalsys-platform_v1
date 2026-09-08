package com.medicalsys.marketing.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Clave de {@link PatientActivity}: el paciente dentro de una organizacion.
 *
 * <p>La actividad es por tenant, no global: el mismo paciente puede atenderse en dos clinicas de la
 * plataforma y cada una solo debe ver, y segmentar sobre, su propia relacion comercial con el.
 *
 * @param patientPublicId      paciente de patients_db
 * @param organizationPublicId tenant propietario de la relacion comercial
 */
@Embeddable
public record PatientActivityId(
        @Column(name = "patient_public_id", nullable = false, columnDefinition = "BINARY(16)")
        UUID patientPublicId,

        @Column(name = "organization_public_id", nullable = false, columnDefinition = "BINARY(16)")
        UUID organizationPublicId) implements Serializable {

    public PatientActivityId {
        Objects.requireNonNull(patientPublicId, "patientPublicId");
        Objects.requireNonNull(organizationPublicId, "organizationPublicId");
    }
}
