package com.medicalsys.marketing.service;

import com.medicalsys.marketing.common.error.ErrorCode;
import com.medicalsys.marketing.common.error.NotFoundException;
import com.medicalsys.marketing.common.tenant.RequestContext;
import com.medicalsys.marketing.domain.model.PatientActivity;
import com.medicalsys.marketing.domain.model.RefPatient;
import com.medicalsys.marketing.domain.model.RefServiceOffering;
import com.medicalsys.marketing.mapper.MarketingMapper;
import com.medicalsys.marketing.repository.PatientActivityRepository;
import com.medicalsys.marketing.repository.RefPatientRepository;
import com.medicalsys.marketing.repository.RefServiceOfferingRepository;
import com.medicalsys.marketing.web.dto.PatientActivityResponse;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta de la proyeccion comercial y gestion del opt-out.
 *
 * <p>El opt-out vive aqui, y no en ms-pacientes, por dos razones practicas. La primera es de
 * disponibilidad: el paciente debe poder ejercerlo aunque ms-pacientes este caido, porque el
 * servicio que va a mandarle publicidad es este. La segunda es de aplicacion: la exclusion tiene que
 * evaluarse al materializar la audiencia, antes de que exista mensaje alguno, y ese momento ocurre
 * dentro de esta base.
 */
@Service
public class PatientActivityService {

    private static final Logger log = LoggerFactory.getLogger(PatientActivityService.class);

    private final PatientActivityRepository activities;
    private final RefPatientRepository patients;
    private final RefServiceOfferingRepository offerings;
    private final MarketingEntitlementService entitlement;
    private final ReferenceDataService referenceData;
    private final MarketingMapper mapper;
    private final Clock clock;

    public PatientActivityService(PatientActivityRepository activities, RefPatientRepository patients,
            RefServiceOfferingRepository offerings, MarketingEntitlementService entitlement,
            ReferenceDataService referenceData, MarketingMapper mapper, Clock clock) {
        this.activities = activities;
        this.patients = patients;
        this.offerings = offerings;
        this.entitlement = entitlement;
        this.referenceData = referenceData;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Devuelve la proyeccion comercial de un paciente en la organizacion. */
    @Transactional(readOnly = true)
    public PatientActivityResponse get(RequestContext context, UUID patientId) {
        UUID organizationId = context.organizationId();
        entitlement.requireEntitled(organizationId);

        PatientActivity activity = activities
                .findByIdPatientPublicIdAndIdOrganizationPublicId(patientId, organizationId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PATIENT_NOT_KNOWN,
                        "Esta organizacion no tiene actividad comercial registrada del paciente '"
                                + patientId + "'"));
        RefPatient patient = patients.findById(patientId).orElse(null);
        return mapper.toResponse(activity, patient, lastOfferingName(activity));
    }

    /** Lista la actividad comercial del tenant. */
    @Transactional(readOnly = true)
    public Page<PatientActivityResponse> list(RequestContext context, Pageable pageable) {
        UUID organizationId = context.organizationId();
        entitlement.requireEntitled(organizationId);

        Page<PatientActivity> page = activities.findByIdOrganizationPublicId(organizationId, pageable);
        Map<UUID, RefPatient> byId = patientsOf(page.getContent());
        Map<UUID, String> offeringNames = offeringNamesOf(page.getContent());

        return page.map(activity -> mapper.toResponse(activity, byId.get(activity.getPatientPublicId()),
                offeringName(offeringNames, activity.getLastServiceOfferingPublicId())));
    }

    /**
     * Registra o revoca la exclusion comercial de un paciente.
     *
     * <p>El opt-out no borra al paciente de las audiencias ya materializadas: quedan con estado
     * {@code EXCLUDED_OPT_OUT} en las nuevas, y las anteriores conservan lo que se evaluo entonces.
     * Reescribir el pasado haria imposible auditar que se envio y con que base.
     */
    @Transactional
    public PatientActivityResponse setOptOut(RequestContext context, UUID patientId, boolean optOut) {
        UUID organizationId = context.organizationId();
        entitlement.requireEntitled(organizationId);

        RefPatient patient = referenceData.setMarketingOptOut(organizationId, patientId, optOut, clock.instant());
        PatientActivity activity = referenceData.requireActivity(organizationId, patientId);
        log.info("El paciente {} quedo con marketingOptOut={} en el tenant {}", patientId, optOut,
                organizationId);

        return mapper.toResponse(activity, patient, lastOfferingName(activity));
    }

    /** Nombre de la ultima prestacion de una actividad concreta; nulo si todavia no compro nada. */
    private String lastOfferingName(PatientActivity activity) {
        UUID offeringId = activity.getLastServiceOfferingPublicId();
        return offeringId == null
                ? null
                : offerings.findById(offeringId).map(RefServiceOffering::getName).orElse(null);
    }

    /**
     * Nombre de la ultima prestacion comprada, o {@code null} si el paciente no compro ninguna.
     *
     * <p>La comprobacion de nulo no es defensiva de mas: un paciente sin compras tiene
     * {@code lastServiceOfferingPublicId} nulo, y los mapas inmutables de {@code Map.of()} lanzan
     * {@link NullPointerException} al consultarlos con una clave nula en vez de devolver nulo, que
     * es lo que hace un {@code HashMap}. Es justo el caso del paciente recien conocido.
     */
    private String offeringName(Map<UUID, String> names, UUID offeringId) {
        return offeringId == null ? null : names.get(offeringId);
    }

    /** Resuelve los pacientes de una pagina en una sola consulta, no una por fila. */
    private Map<UUID, RefPatient> patientsOf(List<PatientActivity> page) {
        List<UUID> ids = page.stream().map(PatientActivity::getPatientPublicId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return patients.findByPublicIdIn(ids).stream()
                .collect(Collectors.toMap(RefPatient::getPublicId, patient -> patient));
    }

    /** Resuelve los nombres de prestacion de una pagina en una sola consulta. */
    private Map<UUID, String> offeringNamesOf(List<PatientActivity> page) {
        List<UUID> ids = page.stream()
                .map(PatientActivity::getLastServiceOfferingPublicId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return offerings.findAllById(ids).stream()
                .collect(Collectors.toMap(RefServiceOffering::getPublicId, RefServiceOffering::getName));
    }
}
