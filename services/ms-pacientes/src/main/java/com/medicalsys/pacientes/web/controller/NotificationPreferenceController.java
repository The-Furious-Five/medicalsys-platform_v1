package com.medicalsys.pacientes.web.controller;

import com.medicalsys.pacientes.common.security.RequiresPermission;
import com.medicalsys.pacientes.domain.enums.NotificationCategory;
import com.medicalsys.pacientes.domain.enums.NotificationChannel;
import com.medicalsys.pacientes.service.NotificationPreferenceService;
import com.medicalsys.pacientes.web.dto.NotificationPreferenceRequest;
import com.medicalsys.pacientes.web.dto.NotificationPreferenceResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Preferencias y evidencia de consentimiento administradas por ms-pacientes. */
@RestController
@RequestMapping("/api/v1/patients/{patientId}/notification-preferences")
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferences;

    public NotificationPreferenceController(NotificationPreferenceService preferences) {
        this.preferences = preferences;
    }

    @GetMapping
    @RequiresPermission("patients.notification_preference.read")
    public List<NotificationPreferenceResponse> list(@PathVariable UUID patientId) {
        return preferences.list(patientId);
    }

    @PutMapping("/{channel}/{category}")
    @RequiresPermission("patients.notification_preference.update")
    public NotificationPreferenceResponse upsert(
            @PathVariable UUID patientId,
            @PathVariable NotificationChannel channel,
            @PathVariable NotificationCategory category,
            @Valid @RequestBody NotificationPreferenceRequest request) {
        return preferences.upsert(patientId, channel, category, request);
    }

}
