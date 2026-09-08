package com.medicalsys.pacientes.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import com.medicalsys.pacientes.domain.enums.ConsentSource;
import com.medicalsys.pacientes.domain.enums.DocumentType;
import com.medicalsys.pacientes.domain.enums.NotificationCategory;
import com.medicalsys.pacientes.domain.enums.NotificationChannel;
import com.medicalsys.pacientes.domain.model.NotificationPreference;
import com.medicalsys.pacientes.domain.model.Patient;
import com.medicalsys.pacientes.mapper.PatientMapper;
import com.medicalsys.pacientes.messaging.NotificationPreferenceEventPublisher;
import com.medicalsys.pacientes.repository.NotificationPreferenceRepository;
import com.medicalsys.pacientes.web.dto.NotificationPreferenceRequest;
import com.medicalsys.pacientes.web.dto.NotificationPreferenceResponse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

    @Mock
    private NotificationPreferenceRepository repository;
    @Mock
    private PatientService patients;
    @Mock
    private NotificationPreferenceEventPublisher events;

    private NotificationPreferenceService service;

    @BeforeEach
    void setUp() {
        service = new NotificationPreferenceService(repository, patients, new PatientMapper(), events);
    }

    @Test
    void recordsOptInTimestampAndConsentSource() {
        Patient patient = Patient.register(DocumentType.CI, "123", "");
        UUID patientId = patient.getPublicId();
        when(patients.requireEntity(patientId)).thenReturn(patient);
        when(repository.findByPatient_PublicIdAndChannelAndCategory(
                patientId, NotificationChannel.WHATSAPP, NotificationCategory.CLINICAL))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any(NotificationPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationPreferenceResponse response = service.upsert(patientId, NotificationChannel.WHATSAPP,
                NotificationCategory.CLINICAL, new NotificationPreferenceRequest(true, ConsentSource.RECEPTION));

        assertEquals(patientId, response.patientId());
        assertEquals(true, response.optIn());
        assertEquals(ConsentSource.RECEPTION, response.consentSource());
        assertNotNull(response.optInAt());
    }
}
