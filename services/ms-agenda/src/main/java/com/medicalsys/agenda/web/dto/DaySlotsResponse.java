package com.medicalsys.agenda.web.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DaySlotsResponse(
        LocalDate date,
        UUID professionalOrganizationPublicId,
        UUID healthProfessionalPublicId,
        int totalSlots,
        List<SlotResponse> slots) {
}
