package com.medicalsys.agenda.service;

import com.medicalsys.agenda.domain.Appointment;
import com.medicalsys.agenda.domain.AppointmentStatus;
import com.medicalsys.agenda.domain.AppointmentStatusHistory;
import com.medicalsys.agenda.domain.ChannelOrigin;
import com.medicalsys.agenda.domain.Modality;
import com.medicalsys.agenda.domain.PhysicalResource;
import com.medicalsys.agenda.domain.ReservationStatus;
import com.medicalsys.agenda.domain.StatusChangeOrigin;
import com.medicalsys.agenda.repository.AppointmentRepository;
import com.medicalsys.agenda.repository.AppointmentStatusHistoryRepository;
import com.medicalsys.agenda.web.dto.AppointmentHistoryResponse;
import com.medicalsys.agenda.web.dto.AppointmentResponse;
import com.medicalsys.agenda.web.dto.CreateAppointmentRequest;
import com.medicalsys.agenda.web.dto.PageResponse;
import com.medicalsys.agenda.web.dto.RescheduleAppointmentRequest;
import com.medicalsys.agenda.web.dto.StatusChangeRequest;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentService {

    private final AppointmentRepository appointments;
    private final AppointmentStatusHistoryRepository histories;
    private final ScheduleBlockService blockService;
    private final ReservationService reservationService;
    private final PhysicalResourceService resourceService;
    private final ExternalDirectoryClient directory;
    private final SchedulingMapper mapper;

    public AppointmentService(AppointmentRepository appointments, AppointmentStatusHistoryRepository histories,
                              ScheduleBlockService blockService, ReservationService reservationService,
                              PhysicalResourceService resourceService, ExternalDirectoryClient directory,
                              SchedulingMapper mapper) {
        this.appointments = appointments;
        this.histories = histories;
        this.blockService = blockService;
        this.reservationService = reservationService;
        this.resourceService = resourceService;
        this.directory = directory;
        this.mapper = mapper;
    }

    @Transactional
    public AppointmentResponse create(CreateAppointmentRequest request) {
        requireValidRange(request.startsAt(), request.endsAt());
        directory.requirePatient(request.patientPublicId());
        directory.requireProfessional(request.healthProfessionalPublicId());
        requireNotBlocked(request.organizationPublicId(), request.healthProfessionalPublicId(),
                request.branchPublicId(), request.startsAt(), request.endsAt());

        Appointment appointment = new Appointment();
        appointment.setOrganizationPublicId(request.organizationPublicId());
        appointment.setBranchPublicId(request.branchPublicId());
        appointment.setPatientPublicId(request.patientPublicId());
        appointment.setHealthProfessionalPublicId(request.healthProfessionalPublicId());
        appointment.setServiceOfferingPublicId(request.serviceOfferingPublicId());
        appointment.setStartsAt(request.startsAt());
        appointment.setEndsAt(request.endsAt());
        appointment.setModality(request.modality() == null ? Modality.IN_PERSON : request.modality());
        appointment.setChannelOrigin(
                request.channelOrigin() == null ? ChannelOrigin.RECEPTION : request.channelOrigin());
        appointment.setReasonForVisit(request.reasonForVisit());
        appointment.setRequiresConsent(Boolean.TRUE.equals(request.requiresConsent()));
        appointment.setEstimatedPriceBob(request.estimatedPriceBob());
        appointment.setTeleconsultationLink(request.teleconsultationLink());
        appointment.setCreatedByUserPublicId(request.createdByUserPublicId());
        appointment.setStatus(AppointmentStatus.REQUESTED);

        Appointment saved = OverlapTranslator.translate(() -> appointments.saveAndFlush(appointment));
        recordHistory(saved, null, AppointmentStatus.REQUESTED, "Cita creada",
                request.createdByUserPublicId(), StatusChangeOrigin.USER);

        // La cita y la reserva del consultorio se confirman juntas: si el recurso choca, no queda cita huerfana.
        if (request.physicalResourcePublicId() != null) {
            PhysicalResource resource = resourceService.require(request.physicalResourcePublicId());
            reservationService.reserveForAppointment(saved, resource, request.createdByUserPublicId());
        }
        return mapper.toAppointmentResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> search(UUID organizationPublicId, UUID branchPublicId,
                                                    UUID healthProfessionalPublicId, UUID patientPublicId,
                                                    AppointmentStatus status, OffsetDateTime from,
                                                    OffsetDateTime to, Pageable pageable) {
        Specification<Appointment> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("organizationPublicId"), organizationPublicId));
            if (branchPublicId != null) {
                predicates.add(builder.equal(root.get("branchPublicId"), branchPublicId));
            }
            if (healthProfessionalPublicId != null) {
                predicates.add(builder.equal(root.get("healthProfessionalPublicId"), healthProfessionalPublicId));
            }
            if (patientPublicId != null) {
                predicates.add(builder.equal(root.get("patientPublicId"), patientPublicId));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(builder.greaterThan(root.get("endsAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThan(root.get("startsAt"), to));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.of(appointments.findAll(specification, pageable), mapper::toAppointmentResponse);
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(UUID publicId) {
        return mapper.toAppointmentResponse(require(publicId));
    }

    @Transactional(readOnly = true)
    public Appointment require(UUID publicId) {
        return appointments.findByPublicId(publicId)
                .orElseThrow(() -> ApiException.notFound("No existe la cita " + publicId));
    }

    @Transactional(readOnly = true)
    public List<AppointmentHistoryResponse> history(UUID publicId) {
        Appointment appointment = require(publicId);
        return histories.findByAppointmentIdOrderByOccurredAtAsc(appointment.getId()).stream()
                .map(mapper::toHistoryResponse)
                .toList();
    }

    @Transactional
    public AppointmentResponse changeStatus(UUID publicId, AppointmentStatus target, StatusChangeRequest request) {
        Appointment appointment = require(publicId);
        AppointmentStatus previous = appointment.getStatus();
        if (previous == target) {
            throw ApiException.conflict("La cita ya se encuentra en estado " + target);
        }
        if (!previous.canMoveTo(target)) {
            throw ApiException.conflict(
                    "No se puede pasar de " + previous + " a " + target + ". Transiciones validas: "
                            + previous.allowedTransitions());
        }

        OffsetDateTime now = OffsetDateTime.now();
        switch (target) {
            case CONFIRMED -> appointment.setConfirmedAt(now);
            case WAITING -> appointment.setArrivedAt(now);
            case IN_PROGRESS -> appointment.setCareStartedAt(now);
            case ATTENDED -> appointment.setCareEndedAt(now);
            case CANCELLED -> {
                appointment.setCancelledAt(now);
                appointment.setCancelledByUserPublicId(request == null ? null : request.userAccountPublicId());
                appointment.setCancellationReason(request == null ? null : request.reason());
            }
            default -> {
            }
        }

        appointment.setStatus(target);
        if (target == AppointmentStatus.CANCELLED || target == AppointmentStatus.NO_SHOW) {
            reservationService.releaseForAppointment(appointment, ReservationStatus.RELEASED);
        }
        appointments.flush();

        recordHistory(appointment, previous, target,
                request == null ? null : request.reason(),
                request == null ? null : request.userAccountPublicId(),
                request == null ? null : request.origin());
        return mapper.toAppointmentResponse(appointment);
    }

    /**
     * Reprogramar no edita la cita original: la marca como RESCHEDULED y crea una nueva que
     * apunta a ella con source_appointment_id, tal como pide el diccionario de datos.
     */
    @Transactional
    public AppointmentResponse reschedule(UUID publicId, RescheduleAppointmentRequest request) {
        requireValidRange(request.startsAt(), request.endsAt());
        Appointment original = require(publicId);
        if (!original.getStatus().canMoveTo(AppointmentStatus.RESCHEDULED)) {
            throw ApiException.conflict(
                    "Una cita en estado " + original.getStatus() + " ya no se puede reprogramar");
        }

        UUID professional = request.healthProfessionalPublicId() == null
                ? original.getHealthProfessionalPublicId()
                : request.healthProfessionalPublicId();
        requireNotBlocked(original.getOrganizationPublicId(), professional, original.getBranchPublicId(),
                request.startsAt(), request.endsAt());

        // Liberar primero la franja original evita que la cita antigua choque con la nueva.
        AppointmentStatus previous = original.getStatus();
        original.setStatus(AppointmentStatus.RESCHEDULED);
        reservationService.releaseForAppointment(original, ReservationStatus.RELEASED);
        appointments.flush();
        recordHistory(original, previous, AppointmentStatus.RESCHEDULED, request.reason(),
                request.userAccountPublicId(), StatusChangeOrigin.USER);

        Appointment replacement = new Appointment();
        replacement.setOrganizationPublicId(original.getOrganizationPublicId());
        replacement.setBranchPublicId(original.getBranchPublicId());
        replacement.setPatientPublicId(original.getPatientPublicId());
        replacement.setHealthProfessionalPublicId(professional);
        replacement.setServiceOfferingPublicId(original.getServiceOfferingPublicId());
        replacement.setStartsAt(request.startsAt());
        replacement.setEndsAt(request.endsAt());
        replacement.setModality(original.getModality());
        replacement.setChannelOrigin(original.getChannelOrigin());
        replacement.setReasonForVisit(original.getReasonForVisit());
        replacement.setRequiresConsent(original.isRequiresConsent());
        replacement.setEstimatedPriceBob(original.getEstimatedPriceBob());
        replacement.setTeleconsultationLink(original.getTeleconsultationLink());
        replacement.setSourceAppointment(original);
        replacement.setCreatedByUserPublicId(request.userAccountPublicId());
        replacement.setStatus(AppointmentStatus.REQUESTED);

        Appointment saved = OverlapTranslator.translate(() -> appointments.saveAndFlush(replacement));
        recordHistory(saved, null, AppointmentStatus.REQUESTED,
                "Reprogramacion de la cita " + original.getPublicId(),
                request.userAccountPublicId(), StatusChangeOrigin.USER);

        if (request.physicalResourcePublicId() != null) {
            PhysicalResource resource = resourceService.require(request.physicalResourcePublicId());
            reservationService.reserveForAppointment(saved, resource, request.userAccountPublicId());
        }
        return mapper.toAppointmentResponse(saved);
    }

    private void recordHistory(Appointment appointment, AppointmentStatus previous, AppointmentStatus next,
                               String reason, UUID userAccountPublicId, StatusChangeOrigin origin) {
        histories.save(new AppointmentStatusHistory(appointment, previous, next, reason, userAccountPublicId, origin));
    }

    private void requireValidRange(OffsetDateTime startsAt, OffsetDateTime endsAt) {
        if (!endsAt.isAfter(startsAt)) {
            throw ApiException.badRequest("endsAt debe ser posterior a startsAt");
        }
    }

    private void requireNotBlocked(UUID organizationPublicId, UUID healthProfessionalPublicId, UUID branchPublicId,
                                   OffsetDateTime startsAt, OffsetDateTime endsAt) {
        List<com.medicalsys.agenda.domain.ScheduleBlock> affecting = blockService.findAffecting(
                organizationPublicId, healthProfessionalPublicId, branchPublicId, startsAt, endsAt);
        if (!affecting.isEmpty()) {
            throw ApiException.conflict("La agenda esta bloqueada en ese intervalo: "
                    + affecting.getFirst().getType()
                    + (affecting.getFirst().getReason() == null ? "" : " - " + affecting.getFirst().getReason()));
        }
    }
}
