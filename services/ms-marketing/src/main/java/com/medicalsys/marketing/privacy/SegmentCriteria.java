package com.medicalsys.marketing.privacy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.medicalsys.marketing.domain.enums.Sex;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Criterio de segmentacion ya vinculado a tipos, con un campo por cada
 * {@link SegmentCriteriaField}.
 *
 * <p>Este record es la forma tipada de la lista blanca: si un eje no esta aqui, no existe manera de
 * expresarlo, y por tanto no hay manera de traducirlo a SQL. El JSON crudo se valida antes con
 * {@link ClinicalDataGuard} y solo despues se vincula a este tipo; las dos comprobaciones son
 * independientes a proposito, porque cada una falla por un motivo distinto.
 *
 * <p>Todos los campos son opcionales y se combinan con AND. Un criterio vacio no llega hasta aqui:
 * el guardian lo rechaza antes, para que nadie cree por accidente un segmento que sea "todos".
 *
 * @param minAge                edad minima en anos cumplidos
 * @param maxAge                edad maxima en anos cumplidos
 * @param sex                   sexos aceptados
 * @param cities                ciudades de residencia aceptadas
 * @param lastVisitBefore       ultima visita anterior a esta fecha; el eje de reactivacion
 * @param lastVisitAfter        ultima visita posterior a esta fecha
 * @param minAppointments       numero minimo de citas atendidas
 * @param maxNoShows            numero maximo de inasistencias toleradas
 * @param minTotalBilledBob     gasto acumulado minimo en bolivianos
 * @param lastServiceOfferingId ultima prestacion facturada
 * @param birthdayMonth         mes de cumpleanos, de 1 a 12
 * @param controlDueBefore      control sugerido anterior a esta fecha, sin su motivo
 * @param excludeCampaignId     excluye a quienes ya fueron destinatarios de esa campana
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SegmentCriteria(
        Integer minAge,
        Integer maxAge,
        List<Sex> sex,
        List<String> cities,
        LocalDate lastVisitBefore,
        LocalDate lastVisitAfter,
        Integer minAppointments,
        Integer maxNoShows,
        BigDecimal minTotalBilledBob,
        UUID lastServiceOfferingId,
        Integer birthdayMonth,
        LocalDate controlDueBefore,
        UUID excludeCampaignId) {

    /** Normaliza las listas para que la evaluacion no tenga que distinguir null de vacio. */
    public SegmentCriteria {
        sex = sex == null || sex.isEmpty() ? null : List.copyOf(sex);
        cities = cities == null || cities.isEmpty() ? null : List.copyOf(cities);
    }

    /**
     * Comprueba que los rangos sean coherentes.
     *
     * @return el motivo del rechazo, o {@code null} si el criterio es evaluable
     */
    public String validationError() {
        if (minAge != null && (minAge < 0 || minAge > 130)) {
            return "minAge debe estar entre 0 y 130";
        }
        if (maxAge != null && (maxAge < 0 || maxAge > 130)) {
            return "maxAge debe estar entre 0 y 130";
        }
        if (minAge != null && maxAge != null && minAge > maxAge) {
            return "minAge no puede ser mayor que maxAge";
        }
        if (birthdayMonth != null && (birthdayMonth < 1 || birthdayMonth > 12)) {
            return "birthdayMonth debe estar entre 1 y 12";
        }
        if (lastVisitBefore != null && lastVisitAfter != null && lastVisitAfter.isAfter(lastVisitBefore)) {
            return "lastVisitAfter no puede ser posterior a lastVisitBefore";
        }
        if (minAppointments != null && minAppointments < 0) {
            return "minAppointments no puede ser negativo";
        }
        if (maxNoShows != null && maxNoShows < 0) {
            return "maxNoShows no puede ser negativo";
        }
        if (minTotalBilledBob != null && minTotalBilledBob.signum() < 0) {
            return "minTotalBilledBob no puede ser negativo";
        }
        return null;
    }

    /** Indica si el criterio necesita cruzar {@code patient_activity} ademas de la demografia. */
    public boolean needsActivity() {
        return lastVisitBefore != null || lastVisitAfter != null || minAppointments != null
                || maxNoShows != null || minTotalBilledBob != null || lastServiceOfferingId != null
                || controlDueBefore != null;
    }
}
