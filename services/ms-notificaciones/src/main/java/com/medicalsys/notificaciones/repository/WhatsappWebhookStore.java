package com.medicalsys.notificaciones.repository;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/** Persistencia de bajo nivel usada por el webhook de WhatsApp. */
@Repository
public class WhatsappWebhookStore {

    private final DatabaseClient database;

    public WhatsappWebhookStore(DatabaseClient database) {
        this.database = database;
    }

    public Mono<Void> insertStatusEvent(Long notificationId, String status, LocalDateTime occurredAt,
            String errorCode, String payload) {
        DatabaseClient.GenericExecuteSpec sql = database.sql("""
                INSERT IGNORE INTO notification_event
                    (notification_id, status, occurred_at, error_code, webhook_payload)
                VALUES (:notificationId, :status, :occurredAt, :errorCode, CAST(:payload AS JSON))
                """)
                .bind("notificationId", notificationId)
                .bind("status", status)
                .bind("occurredAt", occurredAt)
                .bind("payload", payload);
        sql = errorCode == null ? sql.bindNull("errorCode", String.class) : sql.bind("errorCode", errorCode);
        return sql.fetch().rowsUpdated().then();
    }

    public Mono<Void> insertIncoming(String providerId, String source, String body, String intent,
            LocalDateTime receivedAt, String organizationId, String patientId) {
        DatabaseClient.GenericExecuteSpec sql = database.sql("""
                INSERT IGNORE INTO incoming_message
                    (public_id, organization_id, channel, source_phone, patient_id,
                     provider_message_id, message_text, intent, received_at)
                VALUES (:publicId, :organizationId, 'WHATSAPP', :source, :patientId,
                        :providerId, :body, :intent, :receivedAt)
                """)
                .bind("publicId", UUID.randomUUID().toString())
                .bind("source", source)
                .bind("providerId", providerId)
                .bind("intent", intent)
                .bind("receivedAt", receivedAt);
        sql = bindNullable(sql, "organizationId", organizationId);
        sql = bindNullable(sql, "patientId", patientId);
        sql = bindNullable(sql, "body", body);
        return sql.fetch().rowsUpdated().then();
    }

    public Mono<Void> openConversation(String source, LocalDateTime receivedAt,
            String organizationId, String patientId) {
        return database.sql("""
                INSERT INTO conversation_window
                    (organization_id, patient_id, channel, destination, opened_at, last_patient_message_at)
                VALUES (:organizationId, :patientId, 'WHATSAPP', :destination, :openedAt, :lastMessageAt)
                ON DUPLICATE KEY UPDATE
                    destination = VALUES(destination),
                    last_patient_message_at = VALUES(last_patient_message_at)
                """)
                .bind("organizationId", organizationId)
                .bind("patientId", patientId)
                .bind("destination", source)
                .bind("openedAt", receivedAt)
                .bind("lastMessageAt", receivedAt)
                .fetch().rowsUpdated().then();
    }

    private DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec sql,
            String name, String value) {
        return value == null ? sql.bindNull(name, String.class) : sql.bind(name, value);
    }
}
