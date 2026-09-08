package com.medicalsys.notificaciones.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalsys.notificaciones.api.dto.OrganizationReferenceRequest;
import com.medicalsys.notificaciones.api.dto.PatientReferenceRequest;
import com.medicalsys.notificaciones.api.dto.PreferenceReferenceRequest;
import com.medicalsys.notificaciones.domain.NotificationCategory;
import com.medicalsys.notificaciones.domain.NotificationChannel;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.FetchSpec;
import org.springframework.r2dbc.core.RowsFetchSpec;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ReferenceSyncServiceTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PATIENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    @Mock DatabaseClient database;
    @Mock DatabaseClient.GenericExecuteSpec sql;
    @Mock FetchSpec<Map<String, Object>> fetch;
    @Mock RowsFetchSpec<Boolean> booleanRows;
    @Mock RowsFetchSpec<ReferenceSyncService.PatientTarget> patientRows;
    private ReferenceSyncService service;

    @BeforeEach
    void setUp() {
        service = new ReferenceSyncService(database);
        lenient().when(database.sql(anyString())).thenReturn(sql);
        lenient().when(sql.bind(anyString(), any())).thenReturn(sql);
        lenient().when(sql.bindNull(anyString(), any())).thenReturn(sql);
        lenient().when(sql.fetch()).thenReturn(fetch);
        lenient().when(fetch.rowsUpdated()).thenReturn(Mono.just(1L));
    }

    @Test
    void upsertsOrganizationReplica() {
        StepVerifier.create(service.upsertOrganization(
                new OrganizationReferenceRequest(ORGANIZATION_ID, "Clinica", true))).verifyComplete();
        verify(sql).bind("id", ORGANIZATION_ID.toString());
        verify(sql).bind("name", "Clinica");
        verify(sql).bind("active", true);
    }

    @Test
    void upsertsPatientWithWhatsapp() {
        StepVerifier.create(service.upsertPatient(new PatientReferenceRequest(PATIENT_ID, ORGANIZATION_ID,
                "Ana", "+59170000000", true))).verifyComplete();
        verify(sql).bind("whatsappNumber", "+59170000000");
    }

    @Test
    void upsertsPatientWithoutWhatsappAsSqlNull() {
        StepVerifier.create(service.upsertPatient(new PatientReferenceRequest(PATIENT_ID, ORGANIZATION_ID,
                "Ana", null, true))).verifyComplete();
        verify(sql).bindNull("whatsappNumber", String.class);
    }

    @Test
    void mapsServiceCategoryToUtilityPreference() {
        StepVerifier.create(service.upsertPreference(new PreferenceReferenceRequest(PATIENT_ID,
                NotificationChannel.WHATSAPP, NotificationCategory.SERVICE, true,
                LocalDateTime.of(2026, 9, 7, 20, 0), null))).verifyComplete();
        verify(sql).bind("category", "UTILITY");
        verify(sql).bindNull("optedOutAt", LocalDateTime.class);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void returnsStoredConsentDecision() {
        when(sql.map(any(BiFunction.class))).thenReturn((RowsFetchSpec) booleanRows);
        when(booleanRows.one()).thenReturn(Mono.just(true));
        StepVerifier.create(service.isAllowed(PATIENT_ID.toString(), NotificationChannel.WHATSAPP,
                NotificationCategory.MARKETING)).expectNext(true).verifyComplete();
        verify(sql).bind("category", "MARKETING");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void missingConsentDefaultsToDenied() {
        when(sql.map(any(BiFunction.class))).thenReturn((RowsFetchSpec) booleanRows);
        when(booleanRows.one()).thenReturn(Mono.empty());
        StepVerifier.create(service.isAllowed(PATIENT_ID.toString(), NotificationChannel.WHATSAPP,
                NotificationCategory.UTILITY)).expectNext(false).verifyComplete();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void findsActivePatientByNormalizedWhatsappReplica() {
        ReferenceSyncService.PatientTarget target =
                new ReferenceSyncService.PatientTarget(PATIENT_ID.toString(), ORGANIZATION_ID.toString());
        when(sql.map(any(BiFunction.class))).thenReturn((RowsFetchSpec) patientRows);
        when(patientRows.one()).thenReturn(Mono.just(target));
        StepVerifier.create(service.findPatientByWhatsapp("+59170000000"))
                .expectNext(target).verifyComplete();
        verify(sql).bind("phone", "+59170000000");
    }
}
