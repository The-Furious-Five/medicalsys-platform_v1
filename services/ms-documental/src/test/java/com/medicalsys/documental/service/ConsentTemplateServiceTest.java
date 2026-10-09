package com.medicalsys.documental.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.medicalsys.documental.api.dto.ConsentTemplateResponse;
import com.medicalsys.documental.api.dto.CreateConsentTemplateRequest;
import com.medicalsys.documental.domain.ConsentTemplate;
import com.medicalsys.documental.domain.ConsentTemplateStatus;
import com.medicalsys.documental.exception.ApiException;
import com.medicalsys.documental.exception.ErrorCode;
import com.medicalsys.documental.repository.ConsentTemplateRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsentTemplateServiceTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000f1");

    @Mock private ConsentTemplateRepository repository;

    private ConsentTemplateService service;

    @BeforeEach
    void setUp() {
        service = new ConsentTemplateService(repository, new HashingService(), new ConsentMapper());
        lenient().when(repository.saveAndFlush(any(ConsentTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void firstVersionOfANewCodeIsOne() {
        when(repository.findFirstByOrganizationPublicIdAndCodeAndStatusOrderByVersionDesc(
                ORGANIZATION_ID, "SURGERY", ConsentTemplateStatus.PUBLISHED)).thenReturn(Optional.empty());

        ConsentTemplateResponse response = service.create(request(), ORGANIZATION_ID, USER_ID);

        assertEquals(1, response.version());
        assertEquals("DRAFT", response.status());
    }

    @Test
    void newVersionFollowsTheLatestPublishedOne() {
        ConsentTemplate existing = ConsentTemplate.draft(UUID.randomUUID(), ORGANIZATION_ID, "SURGERY", 2,
                "Anterior", null, "cuerpo", null, false, true, LocalDate.now().minusDays(10), null, "hash", USER_ID);
        existing.publish();
        when(repository.findFirstByOrganizationPublicIdAndCodeAndStatusOrderByVersionDesc(
                ORGANIZATION_ID, "SURGERY", ConsentTemplateStatus.PUBLISHED)).thenReturn(Optional.of(existing));

        ConsentTemplateResponse response = service.create(request(), ORGANIZATION_ID, USER_ID);

        assertEquals(3, response.version());
    }

    @Test
    void publishingTwiceIsRejected() {
        ConsentTemplate template = ConsentTemplate.draft(UUID.randomUUID(), ORGANIZATION_ID, "SURGERY", 1, "T",
                null, "cuerpo", null, false, true, LocalDate.now(), null, "hash", USER_ID);
        template.publish();
        when(repository.findByPublicIdAndOrganizationPublicId(template.getPublicId(), ORGANIZATION_ID))
                .thenReturn(Optional.of(template));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.publish(template.getPublicId(), ORGANIZATION_ID));
        assertEquals(ErrorCode.CONSENT_TEMPLATE_NOT_PUBLISHABLE, exception.code());
    }

    private CreateConsentTemplateRequest request() {
        return new CreateConsentTemplateRequest("SURGERY", "Consentimiento de cirugia", null,
                "Yo, {{patientName}}, autorizo.", null, false, true, LocalDate.now(), null);
    }
}
