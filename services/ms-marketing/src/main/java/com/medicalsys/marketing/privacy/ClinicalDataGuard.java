package com.medicalsys.marketing.privacy;

import com.medicalsys.marketing.common.error.BusinessRuleException;
import com.medicalsys.marketing.common.error.ErrorCode;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Barrera de entrada: ningun dato clinico cruza hacia marketing_db.
 *
 * <p>El aislamiento de base y de red hace <em>imposible</em> que ms-marketing lea el expediente. Este
 * guardian cubre la unica via que queda abierta: que alguien <em>empuje</em> un dato clinico hacia
 * aqui, en el criterio de un segmento o en el cuerpo de un evento entrante. Sin el, un productor
 * descuidado podria mandar {@code diagnosticoCie10} dentro de un evento de agenda y ese dato
 * terminaria persistido en una base que promete no tenerlo.
 *
 * <p>Aplica dos reglas, en este orden:
 * <ol>
 *   <li><b>Deteccion de vocabulario clinico.</b> Un campo cuyo nombre evoque patologia, diagnostico,
 *       tratamiento o expediente se rechaza con {@link ErrorCode#CLINICAL_DATA_NOT_ALLOWED} y se
 *       registra en el log. Es lo que convierte un incidente silencioso en un 422 visible.</li>
 *   <li><b>Lista blanca.</b> Cualquier otro campo desconocido se rechaza con
 *       {@link ErrorCode#SEGMENT_CRITERIA_INVALID}. Se distingue del caso anterior a proposito: un
 *       nombre mal escrito no es lo mismo que un intento de cruzar patologia con publicidad, y la
 *       diferencia importa para quien lea el log.</li>
 * </ol>
 *
 * <p>La deteccion normaliza el nombre antes de compararlo -- minusculas, sin acentos y sin
 * separadores -- de modo que {@code diagnostico_principal}, {@code diagnosticoCIE10} y
 * {@code DIAGNÓSTICO} caen todos en la misma raiz.
 */
@Component
public class ClinicalDataGuard {

    private static final Logger log = LoggerFactory.getLogger(ClinicalDataGuard.class);

    /**
     * Raices de vocabulario clinico, ya normalizadas.
     *
     * <p>La lista no pretende ser exhaustiva -- una lista negra nunca lo es -- y por eso no es la
     * unica defensa: es el complemento explicito de la lista blanca de
     * {@link SegmentCriteriaField}, que ya rechaza todo lo que no reconoce. Su valor esta en el
     * diagnostico: nombra el problema en vez de decir "campo desconocido".
     */
    private static final List<String> CLINICAL_ROOTS = List.of(
            "cie10", "icd10", "diagnos", "patolog", "patholog", "morbilidad", "comorbilidad",
            "sintoma", "symptom", "anamnes", "narrativ", "expediente", "medicalrecord",
            "historiaclinica", "encuentroclinico", "clinicalencounter", "alergia", "allerg",
            "tratamiento", "treatment", "medicacion", "medication", "farmaco", "receta",
            "prescription", "laboratorio", "labresult", "resultadoestudio", "studyresult",
            "biopsia", "embarazo", "pregnan", "vih", "hiv", "saludmental", "mentalhealth",
            "discapacidad", "disability", "signosvitales", "vitalsign", "cirugia", "surgery",
            "internacion", "hospitalizacion", "triaje", "triage", "cifrado", "encrypted");

    /**
     * Valida el criterio de un segmento.
     *
     * @param criteria criterio recibido tal cual, sin vincular todavia a ningun tipo
     * @throws BusinessRuleException si aparece un campo clinico o un campo fuera de la lista blanca
     */
    public void assertSegmentCriteriaAllowed(Map<String, Object> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.SEGMENT_CRITERIA_INVALID,
                    "El segmento necesita al menos un criterio. Ejes permitidos: "
                            + SegmentCriteriaField.documentedNames());
        }
        Set<String> unknown = new TreeSet<>();
        for (String field : criteria.keySet()) {
            rejectIfClinical(field, "criterio de segmentacion");
            if (SegmentCriteriaField.ofJsonName(field).isEmpty()) {
                unknown.add(field);
            }
        }
        if (!unknown.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.SEGMENT_CRITERIA_INVALID,
                    "El criterio '" + String.join("', '", unknown) + "' no es un eje de segmentacion "
                            + "reconocido. Ejes permitidos: " + SegmentCriteriaField.documentedNames());
        }
    }

    /**
     * Valida el cuerpo de un evento entrante antes de proyectarlo.
     *
     * <p>A diferencia del criterio, aqui no se exige lista blanca: un productor puede incluir campos
     * que a marketing no le sirven y se ignoran sin drama. Lo que no puede es incluir un dato
     * clinico. La revision es recursiva porque los eventos anidan objetos.
     *
     * @param payload cuerpo del evento
     * @param origin  descripcion del origen, para que el log diga que productor lo envio
     */
    public void assertEventPayloadAllowed(Map<String, Object> payload, String origin) {
        if (payload == null) {
            return;
        }
        inspect(payload, origin, 0);
    }

    private void inspect(Object node, String origin, int depth) {
        // Un evento con mas de ocho niveles de anidamiento no es un evento de esta plataforma;
        // el limite impide que un cuerpo hostil agote la pila.
        if (depth > 8) {
            throw new BusinessRuleException(ErrorCode.EVENT_NOT_SUPPORTED,
                    "El evento de '" + origin + "' anida demasiados niveles para ser inspeccionado");
        }
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                rejectIfClinical(String.valueOf(entry.getKey()), origin);
                inspect(entry.getValue(), origin, depth + 1);
            }
        } else if (node instanceof Iterable<?> items) {
            for (Object item : items) {
                inspect(item, origin, depth + 1);
            }
        }
    }

    /** Rechaza un nombre de campo cuyo vocabulario sea clinico. */
    private void rejectIfClinical(String field, String origin) {
        String normalized = normalize(field);
        for (String root : CLINICAL_ROOTS) {
            if (normalized.contains(root)) {
                // El log nombra el campo, nunca su valor: registrar el valor seria filtrar
                // exactamente el dato que se esta rechazando.
                log.warn("Frontera de privacidad aplicada: se rechazo el campo clinico '{}' recibido en {}",
                        field, origin);
                throw new BusinessRuleException(ErrorCode.CLINICAL_DATA_NOT_ALLOWED,
                        "El campo '" + field + "' es un dato clinico y ms-marketing no lo acepta. "
                                + "Cruzar patologia con publicidad seria una filtracion de dato sensible "
                                + "de salud: la segmentacion solo usa datos administrativos y demograficos");
            }
        }
    }

    /** Minusculas, sin acentos y sin separadores: {@code Diagnóstico_CIE-10} pasa a {@code diagnosticocie10}. */
    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return withoutAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
