package com.medicalsys.notificaciones.repository;

import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/** Consulta si Meta permite enviar texto libre dentro de la ventana de 24 horas. */
@Repository
public class ConversationWindowStore {

    private final DatabaseClient database;
    private final Clock clock;

    @Autowired
    public ConversationWindowStore(DatabaseClient database) {
        this(database, Clock.systemUTC());
    }

    ConversationWindowStore(DatabaseClient database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    public Mono<Boolean> isOpen(String organizationId, String patientId) {
        if (patientId == null) {
            return Mono.just(false);
        }
        return database.sql("""
                SELECT COUNT(*) AS amount
                FROM conversation_window
                WHERE organization_id = :organizationId
                  AND patient_id = :patientId
                  AND channel = 'WHATSAPP'
                  AND expires_at > :now
                """)
                .bind("organizationId", organizationId)
                .bind("patientId", patientId)
                .bind("now", LocalDateTime.now(clock))
                .map((row, metadata) -> {
                    Number amount = row.get("amount", Number.class);
                    return amount != null && amount.longValue() > 0;
                })
                .one().defaultIfEmpty(false);
    }
}
