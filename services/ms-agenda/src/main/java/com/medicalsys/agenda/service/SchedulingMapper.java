package com.medicalsys.agenda.service;

import com.medicalsys.agenda.domain.Appointment;
import com.medicalsys.agenda.domain.AppointmentStatusHistory;
import com.medicalsys.agenda.domain.PhysicalResource;
import com.medicalsys.agenda.domain.ResourceReservation;
import com.medicalsys.agenda.domain.ScheduleAvailability;
import com.medicalsys.agenda.domain.ScheduleBlock;
import com.medicalsys.agenda.web.dto.AppointmentHistoryResponse;
import com.medicalsys.agenda.web.dto.AppointmentResponse;
import com.medicalsys.agenda.web.dto.AvailabilityResponse;
import com.medicalsys.agenda.web.dto.BlockResponse;
import com.medicalsys.agenda.web.dto.InternalAppointmentResponse;
import com.medicalsys.agenda.web.dto.ReservationResponse;
import com.medicalsys.agenda.web.dto.ResourceResponse;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SchedulingMapper {

    public ResourceResponse toResourceResponse(PhysicalResource resource) {
        return new ResourceResponse(
                resource.getPublicId(),
                resource.getOrganizationPublicId(),
                resource.getBranchPublicId(),
                resource.getCode(),
                resource.getName(),
                resource.getType(),
                resource.getFloor(),
                resource.getPeopleCapacity(),
                resource.getPreparationMinutes(),
                resource.isRequiresSterilization(),
                resource.getEquipment(),
                resource.getStatus(),
                resource.getCreatedAt(),
                resource.getUpdatedAt());
    }

    public AvailabilityResponse toAvailabilityResponse(ScheduleAvailability availability) {
        return new AvailabilityResponse(
                availability.getId(),
                availability.getProfessionalOrganizationPublicId(),
                availability.getDayOfWeek(),
                availability.getStartTime(),
                availability.getEndTime(),
                availability.getSlotDurationMinutes(),
                availability.getSimultaneousCapacity(),
                availability.getModality(),
                availability.getPreferredResource() == null ? null : availability.getPreferredResource().getPublicId(),
                availability.getValidFrom(),
                availability.getValidUntil(),
                availability.isActive());
    }

    public BlockResponse toBlockResponse(ScheduleBlock block) {
        return new BlockResponse(
                block.getId(),
                block.getOrganizationPublicId(),
                block.getHealthProfessionalPublicId(),
                block.getPhysicalResource() == null ? null : block.getPhysicalResource().getPublicId(),
                block.getBranchPublicId(),
                block.getType(),
                block.getStartsAt(),
                block.getEndsAt(),
                block.getReason(),
                block.getCreatedByUserPublicId(),
                block.getCreatedAt());
    }

    public AppointmentResponse toAppointmentResponse(Appointment appointment) {
        List<com.medicalsys.agenda.domain.AppointmentStatus> transitions =
                appointment.getStatus().allowedTransitions().stream()
                        .sorted(Comparator.comparing(Enum::name))
                        .toList();
        return new AppointmentResponse(
                appointment.getPublicId(),
                appointment.getOrganizationPublicId(),
                appointment.getBranchPublicId(),
                appointment.getPatientPublicId(),
                appointment.getHealthProfessionalPublicId(),
                appointment.getServiceOfferingPublicId(),
                appointment.getStartsAt(),
                appointment.getEndsAt(),
                appointment.getModality(),
                appointment.getStatus(),
                transitions,
                appointment.getChannelOrigin(),
                appointment.getReasonForVisit(),
                appointment.isRequiresConsent(),
                appointment.getEstimatedPriceBob(),
                appointment.getTeleconsultationLink(),
                appointment.getSourceAppointment() == null ? null : appointment.getSourceAppointment().getPublicId(),
                appointment.getCreatedByUserPublicId(),
                appointment.getConfirmedAt(),
                appointment.getArrivedAt(),
                appointment.getCareStartedAt(),
                appointment.getCareEndedAt(),
                appointment.getCancelledAt(),
                appointment.getCancelledByUserPublicId(),
                appointment.getCancellationReason(),
                appointment.getCreatedAt(),
                appointment.getUpdatedAt());
    }

    public InternalAppointmentResponse toInternalResponse(Appointment appointment) {
        return new InternalAppointmentResponse(
                appointment.getPublicId(),
                appointment.getOrganizationPublicId(),
                appointment.getBranchPublicId(),
                appointment.getPatientPublicId(),
                appointment.getHealthProfessionalPublicId(),
                appointment.getServiceOfferingPublicId(),
                appointment.getStartsAt(),
                appointment.getEndsAt(),
                appointment.getStatus());
    }

    public AppointmentHistoryResponse toHistoryResponse(AppointmentStatusHistory history) {
        return new AppointmentHistoryResponse(
                history.getId(),
                history.getPreviousStatus(),
                history.getNewStatus(),
                history.getReason(),
                history.getUserAccountPublicId(),
                history.getOrigin(),
                history.getOccurredAt());
    }

    public ReservationResponse toReservationResponse(ResourceReservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getOrganizationPublicId(),
                reservation.getPhysicalResource().getPublicId(),
                reservation.getPhysicalResource().getCode(),
                reservation.getAppointment() == null ? null : reservation.getAppointment().getPublicId(),
                reservation.getStartsAt(),
                reservation.getEndsAt(),
                reservation.getPurpose(),
                reservation.getStatus(),
                reservation.getCreatedByUserPublicId(),
                reservation.getCreatedAt());
    }
}
