package com.medicalsys.marketing.repository;

import com.medicalsys.marketing.common.persistence.Uuids;
import com.medicalsys.marketing.domain.enums.Sex;
import com.medicalsys.marketing.privacy.SegmentCriteria;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Traduce un {@link SegmentCriteria} a la consulta que materializa la audiencia.
 *
 * <p><b>Esta clase es la frontera de privacidad en su forma mas concreta.</b> Lea el FROM: dos
 * tablas, {@code ref_patient} y {@code patient_activity}. Nada mas. No hay un tercer JOIN posible
 * porque en esta base no existe una tercera tabla con datos de la persona -- ni diagnostico, ni
 * expediente, ni encuentro. En el monolito, segmentar exigia JOIN contra {@code cita} y
 * {@code factura}, que vivian en el mismo esquema que el historial, y bastaba un JOIN de mas para
 * romper la promesa. Aqui ese JOIN no compila: la tabla no esta.
 *
 * <p>La consulta se arma con SQL nativo y no con la API de criterios de JPA. Es una decision
 * deliberada: el criterio es dinamico y el SQL resultante es la evidencia que un revisor tiene que
 * poder leer de un vistazo para convencerse de que no toca nada clinico. Una cadena de
 * {@code Predicate} anidados esconderia justo lo que interesa mostrar.
 *
 * <p>Todo valor viaja como parametro con nombre. Ningun dato del criterio se concatena en el SQL:
 * los unicos fragmentos que se concatenan son constantes de esta clase.
 *
 * <p>La audiencia se acota siempre por {@code patient_activity} del tenant. Un paciente al que esta
 * clinica nunca atendio no es alcanzable desde sus campanas, aunque exista en la replica porque otra
 * clinica de la plataforma si lo atendio. Es aislamiento entre tenants, no una limitacion.
 */
@Repository
public class AudienceQueryRepository {

    /**
     * Origen de datos de toda segmentacion.
     *
     * <p>Es constante y esta declarado una sola vez a proposito: {@code AudiencePrivacyTest} lo lee
     * y falla si alguna vez aparece aqui una tabla que no sea una de estas dos.
     */
    static final String AUDIENCE_FROM = """
            FROM ref_patient p
            JOIN patient_activity a
              ON a.patient_public_id = p.public_id
             AND a.organization_public_id = :organizationId
            """;

    /** Un paciente inactivo o fallecido nunca entra en una audiencia comercial. */
    private static final String BASE_WHERE = "WHERE p.status = 'ACTIVE'";

    private final JdbcClient jdbcClient;

    public AudienceQueryRepository(DataSource dataSource) {
        this.jdbcClient = JdbcClient.create(dataSource);
    }

    /**
     * Candidato de la audiencia.
     *
     * <p>Se devuelve el opt-out junto al paciente, en vez de filtrarlo en la consulta, porque la
     * campana necesita <em>dejar constancia</em> de a quien excluyo y por que. Un excluido que
     * simplemente no aparece no prueba que la exclusion se haya evaluado.
     *
     * @param patientPublicId paciente que cumple el criterio
     * @param fullName        nombre para la vista previa de la audiencia
     * @param marketingOptOut si pidio no recibir comunicaciones comerciales
     */
    public record AudienceCandidate(UUID patientPublicId, String fullName, boolean marketingOptOut) {
    }

    /**
     * Cuenta los pacientes alcanzables: los que cumplen el criterio y no pidieron el opt-out.
     *
     * @param organizationId tenant que segmenta
     * @param criteria       criterio ya validado por el guardian de privacidad
     * @param today          fecha de referencia para las edades
     */
    public long countReachable(UUID organizationId, SegmentCriteria criteria, LocalDate today) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("organizationId", Uuids.toBytes(organizationId));
        String where = buildWhere(criteria, today, params);

        Long count = jdbcClient
                .sql("SELECT COUNT(*) " + AUDIENCE_FROM + where + " AND p.marketing_opt_out = 0")
                .params(params)
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }

    /**
     * Devuelve los pacientes que cumplen el criterio, incluidos los excluidos por opt-out.
     *
     * @param organizationId tenant que segmenta
     * @param criteria       criterio ya validado por el guardian de privacidad
     * @param today          fecha de referencia para las edades
     * @param maxResults     tope de la audiencia; protege de materializar una campana ilimitada
     */
    public List<AudienceCandidate> findCandidates(UUID organizationId, SegmentCriteria criteria, LocalDate today,
            int maxResults) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("organizationId", Uuids.toBytes(organizationId));
        String where = buildWhere(criteria, today, params);
        params.put("maxResults", maxResults);

        return jdbcClient
                .sql("SELECT p.public_id, p.full_name, p.marketing_opt_out " + AUDIENCE_FROM + where
                        + " ORDER BY p.public_id LIMIT :maxResults")
                .params(params)
                .query((rs, rowNum) -> new AudienceCandidate(
                        Uuids.fromBytes(rs.getBytes("public_id")),
                        rs.getString("full_name"),
                        rs.getBoolean("marketing_opt_out")))
                .list();
    }

    /**
     * Arma el WHERE a partir del criterio.
     *
     * <p>Cada eje de {@code SegmentCriteriaField} tiene aqui exactamente una condicion, y ninguna
     * mas existe. Los valores se acumulan en {@code params} y jamas se interpolan en el texto.
     */
    private String buildWhere(SegmentCriteria criteria, LocalDate today, Map<String, Object> params) {
        List<String> conditions = new ArrayList<>();

        if (criteria.minAge() != null) {
            // Tiene al menos minAge anos si nacio en o antes de hoy menos minAge anos.
            conditions.add("p.birth_date <= :bornOnOrBefore");
            params.put("bornOnOrBefore", today.minusYears(criteria.minAge()));
        }
        if (criteria.maxAge() != null) {
            // Tiene como mucho maxAge anos si nacio despues de hoy menos (maxAge + 1) anos.
            conditions.add("p.birth_date > :bornAfter");
            params.put("bornAfter", today.minusYears(criteria.maxAge() + 1L));
        }
        if (criteria.sex() != null) {
            conditions.add("p.sex IN (:sexes)");
            params.put("sexes", criteria.sex().stream().map(Sex::name).toList());
        }
        if (criteria.cities() != null) {
            conditions.add("p.city IN (:cities)");
            params.put("cities", criteria.cities());
        }
        if (criteria.birthdayMonth() != null) {
            conditions.add("MONTH(p.birth_date) = :birthdayMonth");
            params.put("birthdayMonth", criteria.birthdayMonth());
        }
        if (criteria.lastVisitBefore() != null) {
            conditions.add("a.last_visit_on IS NOT NULL AND a.last_visit_on < :lastVisitBefore");
            params.put("lastVisitBefore", criteria.lastVisitBefore());
        }
        if (criteria.lastVisitAfter() != null) {
            conditions.add("a.last_visit_on IS NOT NULL AND a.last_visit_on > :lastVisitAfter");
            params.put("lastVisitAfter", criteria.lastVisitAfter());
        }
        if (criteria.minAppointments() != null) {
            conditions.add("a.total_appointments >= :minAppointments");
            params.put("minAppointments", criteria.minAppointments());
        }
        if (criteria.maxNoShows() != null) {
            conditions.add("a.total_no_shows <= :maxNoShows");
            params.put("maxNoShows", criteria.maxNoShows());
        }
        if (criteria.minTotalBilledBob() != null) {
            conditions.add("a.total_billed_bob >= :minTotalBilled");
            params.put("minTotalBilled", criteria.minTotalBilledBob());
        }
        if (criteria.lastServiceOfferingId() != null) {
            conditions.add("a.last_service_offering_public_id = :lastServiceOffering");
            params.put("lastServiceOffering", Uuids.toBytes(criteria.lastServiceOfferingId()));
        }
        if (criteria.controlDueBefore() != null) {
            // La fecha del control, nunca su motivo: el motivo no esta en esta base.
            conditions.add("a.suggested_next_visit_on IS NOT NULL "
                    + "AND a.suggested_next_visit_on <= :controlDueBefore");
            params.put("controlDueBefore", criteria.controlDueBefore());
        }
        if (criteria.excludeCampaignId() != null) {
            conditions.add("""
                    NOT EXISTS (SELECT 1 FROM campaign_recipient r
                                  JOIN campaign c ON c.id = r.campaign_id
                                 WHERE c.public_id = :excludeCampaign
                                   AND r.patient_public_id = p.public_id)""");
            params.put("excludeCampaign", Uuids.toBytes(criteria.excludeCampaignId()));
        }

        StringBuilder where = new StringBuilder(BASE_WHERE);
        for (String condition : conditions) {
            where.append(" AND (").append(condition).append(')');
        }
        return where.toString();
    }
}
