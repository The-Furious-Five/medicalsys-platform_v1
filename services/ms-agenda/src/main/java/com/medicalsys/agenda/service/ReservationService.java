package com.medicalsys.agenda.service;

import com.medicalsys.agenda.domain.Appointment;
import com.medicalsys.agenda.domain.PhysicalResource;
import com.medicalsys.agenda.domain.ReservationStatus;
import com.medicalsys.agenda.domain.ResourceReservation;
import com.medicalsys.agenda.repository.ResourceReservationRepository;
import com.medicalsys.agenda.repository.ScheduleBlockRepository;
import com.medicalsys.agenda.web.dto.CreateReservationRequest;
import com.medicalsys.agenda.web.dto.ReservationResponse;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {

    private final ResourceReservationRepository reservations;
    private final ScheduleBlockRepository blocks;
    private final PhysicalResourceService resourceService;
    private final SchedulingMapper mapper;

    public ReservationService(ResourceReservationRepository reservations, ScheduleBlockRepository blocks,
                              PhysicalResourceService resourceService, SchedulingMapper mapper) {
        this.reservations = reservations;
        this.blocks = blocks;
        this.resourceService = resourceService;
        this.mapper = mapper;
    }

    /**
     * Reserva el recurso de una cita. El intervalo incluye los minutos de preparacion del recurso,
     * porque el consultorio no queda libre en el instante en que termina la atencion.
     */
    @Transactional
    public ResourceReservation reserveForAppointment(Appointment appointment, PhysicalResource resource,
                                                     UUID createdByUserPublicId) {
        OffsetDateTime endsAt = appointment.getEndsAt()
                .plusMinutes(resource.getPreparationMinutes() == null ? 0 : resource.getPreparationMinutes());
        return persist(appointment.getOrganizationPublicId(), resource, appointment,
                appointment.getStartsAt(), endsAt, "Cita " + appointment.getPublicId(), createdByUserPublicId);
    }

    @Transactional
    public ReservationResponse create(CreateReservationRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw ApiException.badRequest("endsAt debe ser posterior a startsAt");
        }
        PhysicalResource resource = resourceService.require(request.physicalResourcePublicId());
        return mapper.toReservationResponse(persist(request.organizationPublicId(), resource, null,
                request.startsAt(), request.endsAt(), request.purpose(), request.createdByUserPublicId()));
    }

    private ResourceReservation persist(UUID organizationPublicId, PhysicalResource resource, Appointment appointment,
                                        OffsetDateTime startsAt, OffsetDateTime endsAt, String purpose,
                                        UUID createdByUserPublicId) {
        if (!resource.isBookable()) {
            throw ApiException.conflict(
                    "El recurso " + resource.getCode() + " no admite reservas: estado " + resource.getStatus());
        }
        if (!blocks.findAffectingResource(resource.getId(), startsAt, endsAt).isEmpty()) {
            throw ApiException.conflict(
                    "El recurso " + resource.getCode() + " tiene un bloqueo de agenda en ese intervalo");
        }

        ResourceReservation reservation = new ResourceReservation();
        reservation.setOrganizationPublicId(organizationPublicId);
        reservation.setPhysicalResource(resource);
        reservation.setAppointment(appointment);
        reservation.setStartsAt(startsAt);
        reservation.setEndsAt(endsAt);
        reservation.setPurpose(purpose);
        reservation.setCreatedByUserPublicId(createdByUserPublicId);
        return OverlapTranslator.translate(() -> reservations.saveAndFlush(reservation));
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> search(UUID organizationPublicId, UUID physicalResourcePublicId,
                                            OffsetDateTime from, OffsetDateTime to) {
        Long resourceId = physicalResourcePublicId == null
                ? null
                : resourceService.require(physicalResourcePublicId).getId();
        Specification<ResourceReservation> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("organizationPublicId"), organizationPublicId));
            if (resourceId != null) {
                predicates.add(builder.equal(root.get("physicalResource").get("id"), resourceId));
            }
            if (from != null) {
                predicates.add(builder.greaterThan(root.get("endsAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThan(root.get("startsAt"), to));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        return reservations.findAll(specification, Sort.by("startsAt").ascending()).stream()
                .map(mapper::toReservationResponse)
                .toList();
    }

    @Transactional
    public ReservationResponse changeStatus(UUID organizationPublicId, Long id, ReservationStatus status) {
        ResourceReservation reservation = reservations.findById(id)
                .filter(candidate -> candidate.getOrganizationPublicId().equals(organizationPublicId))
                .orElseThrow(() -> ApiException.notFound("No existe la reserva " + id));
        reservation.setStatus(status);
        return mapper.toReservationResponse(reservation);
    }

    /** Al cancelar o reprogramar una cita, sus reservas dejan de ocupar el recurso. */
    @Transactional
    public void releaseForAppointment(Appointment appointment, ReservationStatus status) {
        Specification<ResourceReservation> specification = (root, query, builder) ->
                builder.and(
                        builder.equal(root.get("appointment").get("id"), appointment.getId()),
                        root.get("status").in(ReservationStatus.RESERVED, ReservationStatus.IN_USE));
        reservations.findAll(specification).forEach(reservation -> reservation.setStatus(status));
    }
}
