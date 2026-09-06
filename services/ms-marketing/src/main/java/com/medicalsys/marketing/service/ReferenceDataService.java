package com.medicalsys.marketing.service;

import com.medicalsys.marketing.client.PatientDemographics;
import com.medicalsys.marketing.client.PatientsClient;
import com.medicalsys.marketing.client.ServiceCatalogClient;
import com.medicalsys.marketing.client.ServiceOfferingRef;
import com.medicalsys.marketing.common.error.ErrorCode;
import com.medicalsys.marketing.common.error.NotFoundException;
import com.medicalsys.marketing.domain.enums.PatientStatus;
import com.medicalsys.marketing.domain.enums.Sex;
import com.medicalsys.marketing.domain.model.PatientActivity;
import com.medicalsys.marketing.domain.model.RefOrganization;
import com.medicalsys.marketing.domain.model.RefPatient;
import com.medicalsys.marketing.domain.model.RefServiceOffering;
import com.medicalsys.marketing.repository.PatientActivityRepository;
import com.medicalsys.marketing.repository.RefOrganizationRepository;
import com.medicalsys.marketing.repository.RefPatientRepository;
import com.medicalsys.marketing.repository.RefServiceOfferingRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mantiene las tres replicas {@code ref_*} y la proyeccion {@code patient_activity}.
 *
 * <p>Es el unico punto por el que entran datos ajenos a marketing, y por eso conviene que sea
 * pequeno y explicito. Dos caminos lo alimentan:
 * <ul>
 *   <li><b>Eventos</b>, que es el camino normal: ms-pacientes y ms-tenencia publican y aqui se
 *       proyecta.</li>
 *   <li><b>Hidratacion bajo demanda</b>, cuando llega un identificador que la replica no conoce.
 *       Sin ella, un evento de facturacion de un paciente cuyo evento de alta todavia no llego se
 *       perderia -- y con el, su gasto acumulado. El orden de llegada entre productores distintos no
 *       lo garantiza nadie.</li>
 * </ul>
 *
 * <p>Todo lo que entra por aqui es demografia o actividad comercial. No hay ningun metodo que acepte
 * un dato clinico porque no hay ninguna columna donde ponerlo.
 */
@Service
public class ReferenceDataService {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDataService.class);

    private final RefOrganizationRepository organizations;
    private final RefPatientRepository patients;
    private final RefServiceOfferingRepository offerings;
    private final PatientActivityRepository activities;
    private final PatientsClient patientsClient;
    private final ServiceCatalogClient serviceCatalogClient;

    public ReferenceDataService(RefOrganizationRepository organizations, RefPatientRepository patients,
            RefServiceOfferingRepository offerings, PatientActivityRepository activities,
            PatientsClient patientsClient, ServiceCatalogClient serviceCatalogClient) {
        this.organizations = organizations;
        this.patients = patients;
        this.offerings = offerings;
        this.activities = activities;
        this.patientsClient = patientsClient;
        this.serviceCatalogClient = serviceCatalogClient;
    }

    // ---------------------------------------------------------------------------- organizaciones

    /** Proyecta un evento de organizacion sobre la replica. */
    @Transactional
    public RefOrganization applyOrganization(UUID organizationId, String commercialName, Boolean includesMarketing,
            long sourceVersion) {
        Optional<RefOrganization> existing = organizations.findById(organizationId);
        if (existing.isPresent()) {
            RefOrganization organization = existing.get();
            if (!organization.apply(commercialName, includesMarketing, sourceVersion)) {
                log.debug("Se ignoro un evento de organizacion mas antiguo que la replica: {}", organizationId);
            }
            return organization;
        }
        return organizations.save(RefOrganization.of(organizationId,
                commercialName == null ? "ORGANIZACION " + organizationId : commercialName,
                includesMarketing == null || includesMarketing));
    }

    /** Devuelve la replica del tenant creandola si hace falta; nunca falla por ausencia. */
    @Transactional
    public RefOrganization requireOrganization(UUID organizationId) {
        return organizations.findById(organizationId)
                .orElseGet(() -> organizations.save(RefOrganization.of(organizationId,
                        "ORGANIZACION " + organizationId, true)));
    }

    // --------------------------------------------------------------------------------- pacientes

    /**
     * Proyecta un evento de paciente sobre la replica demografica.
     *
     * <p>La firma enumera lo que se acepta. Que no haya un parametro para el diagnostico no es un
     * olvido: es la frontera de privacidad expresada en el unico sitio donde un dato de paciente
     * puede entrar a esta base.
     */
    @Transactional
    public RefPatient applyPatient(UUID patientId, String fullName, LocalDate birthDate, Sex sex, String city,
            String phoneE164, PatientStatus status, long sourceVersion) {
        Optional<RefPatient> existing = patients.findById(patientId);
        if (existing.isPresent()) {
            RefPatient patient = existing.get();
            if (!patient.apply(fullName, birthDate, sex, city, phoneE164, status, sourceVersion)) {
                log.debug("Se ignoro un evento de paciente mas antiguo que la replica: {}", patientId);
            }
            return patient;
        }
        return patients.save(RefPatient.of(patientId,
                fullName == null ? "PACIENTE " + patientId : fullName,
                birthDate, sex, city, phoneE164, status));
    }

    /**
     * Devuelve el paciente replicado, hidratandolo desde ms-pacientes si hace falta.
     *
     * @throws NotFoundException si el propietario tampoco lo reconoce
     */
    @Transactional
    public RefPatient requirePatient(UUID organizationId, UUID patientId) {
        return patients.findById(patientId).orElseGet(() -> hydratePatient(organizationId, patientId));
    }

    /** Igual que {@link #requirePatient}, pero devuelve vacio en vez de lanzar. */
    @Transactional
    public Optional<RefPatient> findOrHydratePatient(UUID organizationId, UUID patientId) {
        Optional<RefPatient> replicated = patients.findById(patientId);
        if (replicated.isPresent()) {
            return replicated;
        }
        Optional<PatientDemographics> remote = patientsClient.demographics(organizationId, patientId);
        return remote.map(this::save);
    }

    private RefPatient hydratePatient(UUID organizationId, UUID patientId) {
        return patientsClient.demographics(organizationId, patientId)
                .map(this::save)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PATIENT_NOT_KNOWN,
                        "El paciente '" + patientId + "' no existe o no es visible para esta organizacion"));
    }

    private RefPatient save(PatientDemographics demographics) {
        log.debug("Se hidrato la demografia del paciente {} desde ms-pacientes", demographics.patientId());
        return patients.save(RefPatient.of(demographics.patientId(), demographics.fullName(),
                demographics.birthDate(), demographics.sex(), demographics.city(), demographics.phoneE164(),
                demographics.status()));
    }

    /** Registra o revoca la exclusion comercial de un paciente. */
    @Transactional
    public RefPatient setMarketingOptOut(UUID organizationId, UUID patientId, boolean optOut,
            java.time.Instant at) {
        RefPatient patient = requirePatient(organizationId, patientId);
        patient.setMarketingOptOut(optOut, at);
        return patient;
    }

    // ------------------------------------------------------------------------------ prestaciones

    /** Proyecta un evento de prestacion sobre la replica del catalogo. */
    @Transactional
    public RefServiceOffering applyServiceOffering(UUID serviceOfferingId, UUID organizationId, String name,
            java.math.BigDecimal basePriceBob, Boolean active, long sourceVersion) {
        requireOrganization(organizationId);
        Optional<RefServiceOffering> existing = offerings.findById(serviceOfferingId);
        if (existing.isPresent()) {
            RefServiceOffering offering = existing.get();
            if (!offering.apply(name, basePriceBob, active, sourceVersion)) {
                log.debug("Se ignoro un evento de prestacion mas antiguo que la replica: {}", serviceOfferingId);
            }
            return offering;
        }
        return offerings.save(RefServiceOffering.of(serviceOfferingId, organizationId,
                name == null ? "PRESTACION " + serviceOfferingId : name,
                basePriceBob, active == null || active));
    }

    /** Devuelve la prestacion replicada, hidratandola desde ms-tenencia si hace falta. */
    @Transactional
    public Optional<RefServiceOffering> findOrHydrateServiceOffering(UUID organizationId, UUID serviceOfferingId) {
        if (serviceOfferingId == null) {
            return Optional.empty();
        }
        Optional<RefServiceOffering> replicated = offerings.findById(serviceOfferingId);
        if (replicated.isPresent()) {
            return replicated;
        }
        Optional<ServiceOfferingRef> remote = serviceCatalogClient.offering(organizationId, serviceOfferingId);
        return remote.map(ref -> {
            requireOrganization(organizationId);
            log.debug("Se hidrato la prestacion {} desde ms-tenencia", ref.id());
            return offerings.save(RefServiceOffering.of(ref.id(), organizationId, ref.name(), ref.basePriceBob(),
                    ref.active()));
        });
    }

    // -------------------------------------------------------------------------------- actividad

    /**
     * Devuelve la proyeccion comercial del paciente en la organizacion, abriendola si no existe.
     *
     * <p>Abrirla es lo que hace "conocido" al paciente para ese tenant: la audiencia se acota siempre
     * por esta tabla, de modo que un paciente que solo atendio otra clinica no es alcanzable desde
     * aqui.
     */
    @Transactional
    public PatientActivity requireActivity(UUID organizationId, UUID patientId) {
        requireOrganization(organizationId);
        requirePatient(organizationId, patientId);
        return activities.findByIdPatientPublicIdAndIdOrganizationPublicId(patientId, organizationId)
                .orElseGet(() -> activities.save(PatientActivity.open(patientId, organizationId)));
    }
}
