package com.medicalsys.marketing.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.medicalsys.marketing.domain.enums.PatientStatus;
import com.medicalsys.marketing.domain.enums.Sex;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Proyeccion demografica de un paciente, tal como ms-marketing la acepta.
 *
 * <p>Este record es deliberadamente estrecho, y esa estrechez es una decision de privacidad, no de
 * comodidad. La ficha que publica ms-pacientes trae bastante mas -- documento, correo, contacto de
 * emergencia, direccion cifrada --; aqui se descarta todo salvo los cinco campos que la seccion 5.12
 * autoriza como ejes de segmentacion. {@code @JsonIgnoreProperties(ignoreUnknown = true)} hace que
 * un campo nuevo del propietario ni siquiera llegue a existir en esta JVM.
 *
 * @param patientId  UUID publico del paciente en patients_db
 * @param fullName   nombre completo, para la vista previa de la audiencia
 * @param birthDate  fecha de nacimiento, base del eje de edad
 * @param sex        sexo registrado
 * @param city       ciudad de residencia
 * @param phoneE164  telefono en formato E.164, que ms-notificaciones usara para entregar
 * @param status     estado del registro: un paciente no activo no entra en ninguna audiencia
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientDemographics(
        UUID patientId,
        String fullName,
        LocalDate birthDate,
        Sex sex,
        String city,
        String phoneE164,
        PatientStatus status) {
}
