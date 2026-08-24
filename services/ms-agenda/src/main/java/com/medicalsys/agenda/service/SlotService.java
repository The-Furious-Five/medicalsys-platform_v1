package com.medicalsys.agenda.service;

import com.medicalsys.agenda.config.SchedulingProperties;
import com.medicalsys.agenda.domain.AppointmentStatus;
import com.medicalsys.agenda.domain.ScheduleAvailability;
import com.medicalsys.agenda.domain.ScheduleBlock;
import com.medicalsys.agenda.repository.AppointmentRepository;
import com.medicalsys.agenda.repository.ScheduleAvailabilityRepository;
import com.medicalsys.agenda.repository.ScheduleBlockRepository;
import com.medicalsys.agenda.web.dto.DaySlotsResponse;
import com.medicalsys.agenda.web.dto.SlotResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calcula los huecos libres de un profesional en un dia.
 *
 * <p>Un hueco existe si hay una regla semanal vigente, no lo pisa ningun bloqueo y aun queda cupo
 * frente a las citas que ya ocupan la agenda. Es solo una consulta: la reserva efectiva sigue
 * decidiendose en la base con la restriccion de antisolapamiento.
 */
@Service
public class SlotService {

    private final ScheduleAvailabilityRepository availabilities;
    private final ScheduleBlockRepository blocks;
    private final AppointmentRepository appointments;
    private final SchedulingProperties properties;

    public SlotService(ScheduleAvailabilityRepository availabilities, ScheduleBlockRepository blocks,
                       AppointmentRepository appointments, SchedulingProperties properties) {
        this.availabilities = availabilities;
        this.blocks = blocks;
        this.appointments = appointments;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public DaySlotsResponse freeSlots(UUID organizationPublicId, UUID professionalOrganizationPublicId,
                                      UUID healthProfessionalPublicId, UUID branchPublicId, LocalDate date) {
        LocalDate today = LocalDate.now(properties.getZone());
        if (date.isBefore(today)) {
            throw ApiException.badRequest("No se consultan huecos de una fecha pasada");
        }
        if (date.isAfter(today.plusDays(properties.getSlots().getMaxDaysAhead()))) {
            throw ApiException.badRequest(
                    "La fecha excede los " + properties.getSlots().getMaxDaysAhead() + " dias de anticipacion");
        }

        short dayOfWeek = (short) date.getDayOfWeek().getValue();
        List<ScheduleAvailability> rules =
                availabilities.findEffectiveFor(professionalOrganizationPublicId, dayOfWeek, date);

        OffsetDateTime dayStart = date.atStartOfDay(properties.getZone()).toOffsetDateTime();
        OffsetDateTime dayEnd = date.plusDays(1).atStartOfDay(properties.getZone()).toOffsetDateTime();
        List<ScheduleBlock> dayBlocks = blocks.findAffecting(organizationPublicId, healthProfessionalPublicId,
                branchPublicId, dayStart, dayEnd);

        OffsetDateTime now = OffsetDateTime.now();
        List<SlotResponse> slots = new ArrayList<>();
        for (ScheduleAvailability rule : rules) {
            LocalTime cursor = rule.getStartTime();
            while (!cursor.plusMinutes(rule.getSlotDurationMinutes()).isAfter(rule.getEndTime())) {
                LocalTime slotEndTime = cursor.plusMinutes(rule.getSlotDurationMinutes());
                OffsetDateTime slotStart = date.atTime(cursor).atZone(properties.getZone()).toOffsetDateTime();
                OffsetDateTime slotEnd = date.atTime(slotEndTime).atZone(properties.getZone()).toOffsetDateTime();
                cursor = slotEndTime;

                if (!slotEnd.isAfter(now)) {
                    continue;
                }
                if (dayBlocks.stream().anyMatch(block -> block.overlaps(slotStart, slotEnd))) {
                    continue;
                }
                int taken = appointments
                        .findOccupying(healthProfessionalPublicId, slotStart, slotEnd, AppointmentStatus.BLOCKING)
                        .size();
                int remaining = rule.getSimultaneousCapacity() - taken;
                if (remaining > 0) {
                    slots.add(new SlotResponse(slotStart, slotEnd, rule.getModality(), remaining));
                }
            }
        }

        slots.sort((left, right) -> left.startsAt().compareTo(right.startsAt()));
        return new DaySlotsResponse(date, professionalOrganizationPublicId, healthProfessionalPublicId,
                slots.size(), slots);
    }
}
