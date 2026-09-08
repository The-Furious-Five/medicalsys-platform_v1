package com.medicalsys.marketing.privacy;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lista blanca de los ejes por los que ms-marketing puede segmentar.
 *
 * <p>Esta enumeracion <b>es</b> la frontera de privacidad del criterio C3, escrita como codigo. La
 * seccion 5.12 del documento de descomposicion lo fija asi: "la segmentacion se evalua solo sobre
 * datos administrativos y demograficos: edad, sexo, sucursal, ultima visita, servicios facturados.
 * Ningun diagnostico CIE-10 ni campo cifrado del expediente participa jamas".
 *
 * <p>El diseno es deliberadamente cerrado: un criterio cuyo nombre no este aqui se rechaza, en vez
 * de ignorarse. Una lista negra dejaria pasar cualquier eje nuevo que nadie penso en prohibir; una
 * lista blanca obliga a que agregar un eje sea una decision explicita, revisable en el diff.
 *
 * <p>Notese que todos los ejes salen de dos tablas y solo de dos: {@code ref_patient} (demografia) y
 * {@code patient_activity} (actividad comercial). Ninguna otra tabla de esta base participa en la
 * segmentacion, y no existe ninguna tabla clinica que pudiera participar.
 */
public enum SegmentCriteriaField {

    /** Edad minima en anos cumplidos, calculada desde {@code ref_patient.birth_date}. */
    MIN_AGE("minAge", "Edad minima en anos cumplidos"),
    /** Edad maxima en anos cumplidos. */
    MAX_AGE("maxAge", "Edad maxima en anos cumplidos"),
    /** Sexos aceptados: lista de M, F, INTERSEX o UNDECLARED. */
    SEX("sex", "Sexos aceptados"),
    /** Ciudades de residencia aceptadas. */
    CITIES("cities", "Ciudades de residencia aceptadas"),
    /** Ultima visita anterior a esta fecha; es el eje de reactivacion. */
    LAST_VISIT_BEFORE("lastVisitBefore", "Ultima visita anterior a esta fecha"),
    /** Ultima visita posterior a esta fecha. */
    LAST_VISIT_AFTER("lastVisitAfter", "Ultima visita posterior a esta fecha"),
    /** Numero minimo de citas registradas en la organizacion. */
    MIN_APPOINTMENTS("minAppointments", "Numero minimo de citas atendidas"),
    /** Numero maximo de inasistencias toleradas. */
    MAX_NO_SHOWS("maxNoShows", "Numero maximo de inasistencias"),
    /** Gasto minimo acumulado en bolivianos. */
    MIN_TOTAL_BILLED_BOB("minTotalBilledBob", "Gasto acumulado minimo en bolivianos"),
    /** Ultima prestacion comprada, por UUID publico de tenancy_db. */
    LAST_SERVICE_OFFERING_ID("lastServiceOfferingId", "UUID de la ultima prestacion facturada"),
    /** Cumple anos en el mes indicado, de 1 a 12. */
    BIRTHDAY_MONTH("birthdayMonth", "Mes de cumpleanos, de 1 a 12"),
    /**
     * Control sugerido con fecha anterior a la indicada.
     *
     * <p>Es la fecha que llega en {@code EncuentroFirmado}, nunca el motivo. Marketing sabe cuando
     * volver a llamar, no de que padece el paciente.
     */
    CONTROL_DUE_BEFORE("controlDueBefore", "Control sugerido anterior a esta fecha"),
    /** Excluye a quien ya recibio la campana indicada, por UUID publico. */
    EXCLUDE_CAMPAIGN_ID("excludeCampaignId", "Excluye a los destinatarios de esta campana");

    private final String jsonName;
    private final String description;

    SegmentCriteriaField(String jsonName, String description) {
        this.jsonName = jsonName;
        this.description = description;
    }

    public String jsonName() {
        return jsonName;
    }

    public String description() {
        return description;
    }

    /** Nombres JSON aceptados, en el orden en que se documentan. */
    public static Set<String> allowedJsonNames() {
        return Arrays.stream(values())
                .map(SegmentCriteriaField::jsonName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Resuelve un nombre de campo recibido; vacio si no pertenece a la lista blanca. */
    public static Optional<SegmentCriteriaField> ofJsonName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String normalized = name.trim();
        return Arrays.stream(values())
                .filter(field -> field.jsonName.equalsIgnoreCase(normalized))
                .findFirst();
    }

    /** Lista legible para el mensaje de error, para que el cliente sepa que si puede pedir. */
    public static String documentedNames() {
        return Arrays.stream(values())
                .map(field -> field.jsonName + " (" + field.description.toLowerCase(Locale.ROOT) + ")")
                .collect(Collectors.joining(", "));
    }
}
