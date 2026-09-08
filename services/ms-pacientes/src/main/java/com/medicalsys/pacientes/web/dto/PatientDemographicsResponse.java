package com.medicalsys.pacientes.web.dto;

import com.medicalsys.pacientes.domain.enums.PatientStatus;
import com.medicalsys.pacientes.domain.enums.Sex;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Proyeccion demografica para ms-marketing.
 *
 * <p>Es la frontera de privacidad del modulo de marketing escrita como tipo: edad, sexo, ciudad y
 * telefono, que son los ejes con los que se puede segmentar una campana. No viaja aqui ningun dato
 * clinico -ni diagnostico, ni alergia, ni medicacion-, y marketing tampoco tiene ruta de red hacia
 * ms-historial con la que obtenerlo.
 *
 * <p>Se separa de {@link BillingPatientSummary} y de {@link FiscalCustomerResponse} porque el
 * documento de identidad no es un eje de segmentacion: enviarlo seria exponer un identificador
 * fiscal a un servicio que no lo necesita para nada.
 *
 * @param patientId  UUID publico del paciente
 * @param fullName   nombre completo, para poder listar la audiencia
 * @param birthDate  fecha de nacimiento; de ella deriva el filtro por edad
 * @param sex        sexo declarado
 * @param city       ciudad de residencia
 * @param phoneE164  telefono en formato E.164; opcional
 * @param status     estado del registro; los inactivos y fallecidos no entran en campanas
 */
public record PatientDemographicsResponse(
        UUID patientId,
        String fullName,
        LocalDate birthDate,
        Sex sex,
        String city,
        String phoneE164,
        PatientStatus status) {
}
