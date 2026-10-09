package com.medicalsys.marketing.domain.model;

import com.medicalsys.marketing.common.Money;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Proyeccion comercial del paciente en una organizacion ({@code patient_activity}).
 *
 * <p>Es la tabla nueva del sprint y la pieza que hace viable la separacion. En el monolito,
 * segmentar exigia un JOIN contra {@code cita} y {@code factura}; al no existir esas tablas en esta
 * base, se mantiene aqui una proyeccion alimentada por eventos.
 *
 * <p>Conviene leer con cuidado que guarda: cuando vino por primera y ultima vez, cuantas veces vino,
 * cuantas falto, cuanto gasto y que compro. Es toda la "historia" que marketing conoce, y esta hecha
 * de hechos administrativos. <b>Nunca por que vino.</b>
 *
 * <p>{@code suggestedNextVisitOn} merece una nota. Llega en el evento {@code EncuentroFirmado}, que
 * transporta la fecha de control y no el motivo clinico. Marketing sabe cuando volver a llamar; de
 * que padece el paciente no se entera, y esa asimetria es intencional.
 */
@Entity
@Table(name = "patient_activity")
public class PatientActivity {

    @EmbeddedId
    private PatientActivityId id;

    @Column(name = "first_visit_on")
    private LocalDate firstVisitOn;

    @Column(name = "last_visit_on")
    private LocalDate lastVisitOn;

    @Column(name = "total_appointments", nullable = false, columnDefinition = "INT UNSIGNED")
    private long totalAppointments;

    @Column(name = "total_no_shows", nullable = false, columnDefinition = "INT UNSIGNED")
    private long totalNoShows;

    @Column(name = "total_billed_bob", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalBilledBob;

    @Column(name = "last_service_offering_public_id", columnDefinition = "BINARY(16)")
    private UUID lastServiceOfferingPublicId;

    @Column(name = "suggested_next_visit_on")
    private LocalDate suggestedNextVisitOn;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected PatientActivity() {
        // Requerido por JPA.
    }

    /** Abre la proyeccion en blanco de un paciente que todavia no tiene actividad registrada. */
    public static PatientActivity open(UUID patientPublicId, UUID organizationPublicId) {
        PatientActivity activity = new PatientActivity();
        activity.id = new PatientActivityId(patientPublicId, organizationPublicId);
        activity.totalAppointments = 0L;
        activity.totalNoShows = 0L;
        activity.totalBilledBob = Money.ZERO;
        return activity;
    }

    /**
     * Registra una cita atendida.
     *
     * <p>Adelanta {@code lastVisitOn} solo si la fecha es posterior a la ya conocida: los eventos
     * pueden llegar desordenados y una reposicion de historico no debe hacer parecer reciente a
     * quien no ha vuelto.
     */
    public void registerAttendedAppointment(LocalDate visitedOn) {
        totalAppointments++;
        if (visitedOn == null) {
            return;
        }
        if (firstVisitOn == null || visitedOn.isBefore(firstVisitOn)) {
            firstVisitOn = visitedOn;
        }
        if (lastVisitOn == null || visitedOn.isAfter(lastVisitOn)) {
            lastVisitOn = visitedOn;
        }
    }

    /** Registra una inasistencia: cuenta para segmentar, nunca para explicar. */
    public void registerNoShow() {
        totalNoShows++;
    }

    /** Suma el importe facturado y recuerda la ultima prestacion comprada. */
    public void registerBilledAmount(BigDecimal amountBob, UUID serviceOfferingPublicId) {
        totalBilledBob = Money.add(totalBilledBob, amountBob);
        if (serviceOfferingPublicId != null) {
            lastServiceOfferingPublicId = serviceOfferingPublicId;
        }
    }

    /**
     * Fija la fecha de control sugerida.
     *
     * <p>El parametro es una fecha y solo una fecha. La firma de este metodo es parte de la
     * frontera: no hay ningun argumento donde pudiera colarse el motivo del control.
     */
    public void setSuggestedNextVisit(LocalDate suggestedNextVisitOn) {
        this.suggestedNextVisitOn = suggestedNextVisitOn;
    }

    public PatientActivityId getId() {
        return id;
    }

    public UUID getPatientPublicId() {
        return id.patientPublicId();
    }

    public UUID getOrganizationPublicId() {
        return id.organizationPublicId();
    }

    public LocalDate getFirstVisitOn() {
        return firstVisitOn;
    }

    public LocalDate getLastVisitOn() {
        return lastVisitOn;
    }

    public long getTotalAppointments() {
        return totalAppointments;
    }

    public long getTotalNoShows() {
        return totalNoShows;
    }

    public BigDecimal getTotalBilledBob() {
        return totalBilledBob;
    }

    public UUID getLastServiceOfferingPublicId() {
        return lastServiceOfferingPublicId;
    }

    public LocalDate getSuggestedNextVisitOn() {
        return suggestedNextVisitOn;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
