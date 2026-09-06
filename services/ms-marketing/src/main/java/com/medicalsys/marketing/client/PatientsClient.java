package com.medicalsys.marketing.client;

import java.util.Optional;
import java.util.UUID;

/**
 * Resuelve la demografia de un paciente que marketing todavia no tiene replicado.
 *
 * <p>La replica se alimenta normalmente por eventos. Este cliente cubre el hueco: cuando llega un
 * evento de facturacion de un paciente que marketing no conoce, hidratarlo bajo demanda evita
 * perder la actividad comercial por un orden de llegada que nadie garantiza.
 */
public interface PatientsClient {

    /**
     * Devuelve la demografia del paciente, o vacio si el propietario no lo reconoce.
     *
     * @param organizationId tenant que pregunta; viaja como header de contexto
     * @param patientId      paciente consultado
     */
    Optional<PatientDemographics> demographics(UUID organizationId, UUID patientId);
}
