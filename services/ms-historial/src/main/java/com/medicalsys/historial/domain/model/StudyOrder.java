package com.medicalsys.historial.domain.model;

import com.medicalsys.historial.domain.enums.StudyOrderStatus;
import com.medicalsys.historial.domain.enums.StudyPriority;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Solicitud de examen o estudio complementario ({@code study_order}).
 *
 * <p>Su {@code public_id} es el orderId que consume ms-agenda para agendar la toma y ms-documental
 * para vincular el archivo del resultado. Nace en estado {@code REQUESTED} ("solicitada").
 */
@Entity
@Table(name = "study_order")
public class StudyOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @Column(name = "organization_public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID organizationPublicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinical_encounter_id", nullable = false, updatable = false)
    private ClinicalEncounter clinicalEncounter;

    @Column(name = "study_type_code", nullable = false, updatable = false, length = 30)
    private String studyTypeCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private StudyPriority priority;

    @Column(name = "instructions", length = 500)
    private String instructions;

    @Column(name = "icd10_code", length = 10)
    private String icd10Code;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StudyOrderStatus status;

    @Column(name = "requested_by_professional_public_id", nullable = false, updatable = false,
            columnDefinition = "BINARY(16)")
    private UUID requestedByProfessionalPublicId;

    @Generated(event = EventType.INSERT)
    @Column(name = "requested_at", insertable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "collection_appointment_public_id", columnDefinition = "BINARY(16)")
    private UUID collectionAppointmentPublicId;

    @OneToMany(mappedBy = "studyOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("parameter ASC")
    private List<StudyResult> results = new ArrayList<>();

    protected StudyOrder() {
        // Requerido por JPA.
    }

    public static StudyOrder request(ClinicalEncounter encounter, String studyTypeCode, StudyPriority priority,
            String instructions, String icd10Code, UUID requestedByProfessionalPublicId) {
        StudyOrder order = new StudyOrder();
        order.publicId = UUID.randomUUID();
        order.clinicalEncounter = Objects.requireNonNull(encounter, "clinicalEncounter");
        order.organizationPublicId = encounter.getOrganizationPublicId();
        order.studyTypeCode = Objects.requireNonNull(studyTypeCode, "studyTypeCode");
        order.priority = priority == null ? StudyPriority.ROUTINE : priority;
        order.instructions = instructions;
        order.icd10Code = icd10Code;
        order.status = StudyOrderStatus.REQUESTED;
        order.requestedByProfessionalPublicId = Objects.requireNonNull(requestedByProfessionalPublicId,
                "requestedByProfessionalPublicId");
        return order;
    }

    /**
     * Aplica una transicion de estado.
     *
     * @throws IllegalStateException si la transicion no esta permitida; el servicio la traduce a 422
     */
    public void changeStatus(StudyOrderStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Transicion de orden no permitida: " + status + " -> " + target);
        }
        this.status = target;
    }

    /** Vincula la cita de toma creada por ms-agenda y deja la orden agendada. */
    public void scheduleCollection(UUID collectionAppointmentPublicId) {
        changeStatus(StudyOrderStatus.SCHEDULED);
        this.collectionAppointmentPublicId = Objects.requireNonNull(collectionAppointmentPublicId,
                "collectionAppointmentPublicId");
    }

    /** Agrega un resultado al agregado. El resultado no reemplaza a otro con el mismo parametro. */
    public StudyResult addResult(StudyResult result) {
        results.add(result);
        return result;
    }

    /** Marca la orden como informada; equivale al estado "disponible" del guion de demostracion. */
    public void markReported() {
        if (status != StudyOrderStatus.REPORTED) {
            this.status = StudyOrderStatus.REPORTED;
        }
    }

    public boolean acceptsResults() {
        return status.acceptsResults();
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public UUID getOrganizationPublicId() {
        return organizationPublicId;
    }

    public ClinicalEncounter getClinicalEncounter() {
        return clinicalEncounter;
    }

    public String getStudyTypeCode() {
        return studyTypeCode;
    }

    public StudyPriority getPriority() {
        return priority;
    }

    public String getInstructions() {
        return instructions;
    }

    public String getIcd10Code() {
        return icd10Code;
    }

    public StudyOrderStatus getStatus() {
        return status;
    }

    public UUID getRequestedByProfessionalPublicId() {
        return requestedByProfessionalPublicId;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public UUID getCollectionAppointmentPublicId() {
        return collectionAppointmentPublicId;
    }

    public List<StudyResult> getResults() {
        return Collections.unmodifiableList(results);
    }
}
