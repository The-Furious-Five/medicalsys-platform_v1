package com.medicalsys.pacientes.service;

import com.medicalsys.pacientes.domain.enums.NotificationCategory;
import com.medicalsys.pacientes.domain.enums.NotificationChannel;
import com.medicalsys.pacientes.domain.model.NotificationPreference;
import com.medicalsys.pacientes.domain.model.Patient;
import com.medicalsys.pacientes.mapper.PatientMapper;
import com.medicalsys.pacientes.messaging.NotificationPreferenceEventPublisher;
import com.medicalsys.pacientes.repository.NotificationPreferenceRepository;
import com.medicalsys.pacientes.web.dto.NotificationPreferenceRequest;
import com.medicalsys.pacientes.web.dto.NotificationPreferenceResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso de consentimiento de contacto, cuya autoridad es ms-pacientes. */
@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferences;
    private final PatientService patients;
    private final PatientMapper mapper;
    private final NotificationPreferenceEventPublisher events;

    public NotificationPreferenceService(NotificationPreferenceRepository preferences, PatientService patients,
            PatientMapper mapper, NotificationPreferenceEventPublisher events) {
        this.preferences = preferences;
        this.patients = patients;
        this.mapper = mapper;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<NotificationPreferenceResponse> list(UUID patientId) {
        patients.requireEntity(patientId);
        return preferences.findAllByPatient_PublicIdOrderByChannelAscCategoryAsc(patientId).stream()
                .map(mapper::toPreferenceResponse)
                .toList();
    }

    @Transactional
    public NotificationPreferenceResponse upsert(UUID patientId, NotificationChannel channel,
            NotificationCategory category, NotificationPreferenceRequest request) {
        Patient patient = patients.requireEntity(patientId);
        NotificationPreference preference = preferences
                .findByPatient_PublicIdAndChannelAndCategory(patientId, channel, category)
                .orElseGet(() -> NotificationPreference.create(patient, channel, category));
        preference.changeConsent(request.optIn(), request.consentSource(), Instant.now());
        NotificationPreference saved = preferences.saveAndFlush(preference);
        events.publish(saved);
        return mapper.toPreferenceResponse(saved);
    }

}
