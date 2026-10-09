package com.medicalsys.notificaciones.service;

import com.medicalsys.notificaciones.api.dto.OrganizationReferenceRequest;
import com.medicalsys.notificaciones.api.dto.PatientReferenceRequest;
import com.medicalsys.notificaciones.api.dto.PreferenceReferenceRequest;
import com.medicalsys.notificaciones.domain.NotificationCategory;
import com.medicalsys.notificaciones.domain.NotificationChannel;
import java.time.LocalDateTime;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ReferenceSyncService {

    private final DatabaseClient database;

    public ReferenceSyncService(DatabaseClient database) {
        this.database = database;
    }

    public Mono<Void> upsertOrganization(OrganizationReferenceRequest request) {
        return database.sql("""
                INSERT INTO organization_reference (organization_id, legal_name, active)
                VALUES (:id, :name, :active)
                ON DUPLICATE KEY UPDATE legal_name = VALUES(legal_name), active = VALUES(active)
                """)
                .bind("id", request.organizationId().toString())
                .bind("name", request.legalName())
                .bind("active", request.active())
                .fetch().rowsUpdated().then();
    }

    public Mono<Void> upsertPatient(PatientReferenceRequest request) {
        DatabaseClient.GenericExecuteSpec sql = database.sql("""
                INSERT INTO patient_reference
                    (patient_id, organization_id, display_name, whatsapp_number, active)
                VALUES (:patientId, :organizationId, :displayName, :whatsappNumber, :active)
                ON DUPLICATE KEY UPDATE
                    organization_id = VALUES(organization_id),
                    display_name = VALUES(display_name),
                    whatsapp_number = VALUES(whatsapp_number),
                    active = VALUES(active)
                """)
                .bind("patientId", request.patientId().toString())
                .bind("organizationId", request.organizationId().toString())
                .bind("displayName", request.displayName())
                .bind("active", request.active());
        sql = request.whatsappNumber() == null
                ? sql.bindNull("whatsappNumber", String.class)
                : sql.bind("whatsappNumber", request.whatsappNumber());
        return sql.fetch().rowsUpdated().then();
    }

    public Mono<Void> upsertPreference(PreferenceReferenceRequest request) {
        NotificationCategory category = preferenceCategory(request.category());
        DatabaseClient.GenericExecuteSpec sql = database.sql("""
                INSERT INTO notification_preference_reference
                    (patient_id, channel, category, opted_in, opted_in_at, opted_out_at)
                VALUES (:patientId, :channel, :category, :optedIn, :optedInAt, :optedOutAt)
                ON DUPLICATE KEY UPDATE
                    opted_in = VALUES(opted_in),
                    opted_in_at = VALUES(opted_in_at),
                    opted_out_at = VALUES(opted_out_at)
                """)
                .bind("patientId", request.patientId().toString())
                .bind("channel", request.channel().name())
                .bind("category", category.name())
                .bind("optedIn", request.optedIn());
        sql = bindDate(sql, "optedInAt", request.optedInAt());
        sql = bindDate(sql, "optedOutAt", request.optedOutAt());
        return sql.fetch().rowsUpdated().then();
    }

    public Mono<Boolean> isAllowed(String patientId, NotificationChannel channel,
            NotificationCategory category) {
        return database.sql("""
                SELECT opted_in
                FROM notification_preference_reference
                WHERE patient_id = :patientId AND channel = :channel AND category = :category
                """)
                .bind("patientId", patientId)
                .bind("channel", channel.name())
                .bind("category", preferenceCategory(category).name())
                .map((row, metadata) -> Boolean.TRUE.equals(row.get("opted_in", Boolean.class)))
                .one()
                .defaultIfEmpty(false);
    }

    public Mono<PatientTarget> findPatientByWhatsapp(String phone) {
        return database.sql("""
                SELECT patient_id, organization_id
                FROM patient_reference
                WHERE whatsapp_number = :phone AND active = TRUE
                LIMIT 1
                """)
                .bind("phone", phone)
                .map((row, metadata) -> new PatientTarget(
                        row.get("patient_id", String.class), row.get("organization_id", String.class)))
                .one();
    }

    public Mono<String> findWhatsappNumber(String patientId) {
        return database.sql("""
                SELECT whatsapp_number
                FROM patient_reference
                WHERE patient_id = :patientId AND active = TRUE
                """)
                .bind("patientId", patientId)
                .map((row, metadata) -> row.get("whatsapp_number", String.class))
                .first();
    }

    private NotificationCategory preferenceCategory(NotificationCategory category) {
        return category == NotificationCategory.MARKETING
                ? NotificationCategory.MARKETING : NotificationCategory.UTILITY;
    }

    private DatabaseClient.GenericExecuteSpec bindDate(DatabaseClient.GenericExecuteSpec sql,
            String name, LocalDateTime value) {
        return value == null ? sql.bindNull(name, LocalDateTime.class) : sql.bind(name, value);
    }

    public record PatientTarget(String patientId, String organizationId) {
    }
}
