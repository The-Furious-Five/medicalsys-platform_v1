package com.medicalsys.profesionales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.medicalsys.profesionales.domain.*;
import com.medicalsys.profesionales.repository.HealthProfessionalRepository;
import com.medicalsys.profesionales.web.CreateProfessionalRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProfessionalServiceTest {
    @Mock HealthProfessionalRepository repository;
    @InjectMocks ProfessionalService service;

    @Test
    void createsProfessionalWithActiveOrganizationAndOnePrimarySpecialty() {
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var request = request(List.of(new CreateProfessionalRequest.SpecialtyRequest("MEDICINA_GENERAL", true, (short) 2020)));
        var response = service.create(request);
        assertThat(response.id()).isNotNull();
        assertThat(response.status()).isEqualTo(ProfessionalStatus.ACTIVE);
        assertThat(response.specialties()).singleElement().extracting("primary").isEqualTo(true);
        assertThat(response.organizations()).singleElement().extracting("status").isEqualTo(OrganizationStatus.ACTIVE);
    }

    @Test
    void rejectsRequestWithoutExactlyOnePrimarySpecialty() {
        var request = request(List.of(new CreateProfessionalRequest.SpecialtyRequest("MEDICINA_GENERAL", false, null)));
        assertThatThrownBy(() -> service.create(request)).isInstanceOf(ResponseStatusException.class);
    }

    private CreateProfessionalRequest request(List<CreateProfessionalRequest.SpecialtyRequest> specialties) {
        return new CreateProfessionalRequest(UUID.randomUUID(), DocumentType.CI, "123456", "", "Ana", "Pérez", null,
                ProfessionalType.PHYSICIAN, "MED-123", "Dra.", null, specialties, UUID.randomUUID(), null,
                OrganizationModality.STAFF, null, LocalDate.now());
    }
}
