package com.medicalsys.marketing.domain.model;

import com.medicalsys.marketing.domain.enums.LoyaltyMovementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Asiento del libro mayor de puntos ({@code loyalty_points_movement}).
 *
 * <p>La tabla es append-only y el saldo se deriva sumando los movimientos; no existe una columna
 * "saldo" que alguien pueda corregir a mano. Cuando un paciente reclama que le faltan puntos, la
 * respuesta es la lista de asientos, no un numero sin procedencia.
 *
 * <p>{@code sourceEventPublicId} lleva restriccion UNIQUE y es lo que hace efectivamente-una-vez a
 * un transporte de eventos que solo garantiza al-menos-una-vez: si {@code FacturaEmitida} llega dos
 * veces, el segundo intento choca contra el motor en vez de duplicar los puntos.
 */
@Entity
@Table(name = "loyalty_points_movement")
public class LoyaltyPointsMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loyalty_program_id", nullable = false, updatable = false)
    private LoyaltyProgram program;

    @Column(name = "patient_public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID patientPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false)
    private LoyaltyMovementType type;

    @Column(name = "points", nullable = false, updatable = false)
    private int points;

    @Column(name = "resulting_balance", nullable = false, updatable = false)
    private int resultingBalance;

    @Column(name = "invoice_public_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID invoicePublicId;

    @Column(name = "appointment_public_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID appointmentPublicId;

    @Column(name = "source_event_public_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID sourceEventPublicId;

    @Column(name = "reason", length = 250)
    private String reason;

    @Column(name = "registered_by_user_public_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID registeredByUserPublicId;

    @Column(name = "occurred_at", insertable = false, updatable = false)
    private Instant occurredAt;

    protected LoyaltyPointsMovement() {
        // Requerido por JPA.
    }

    /**
     * Registra un asiento.
     *
     * @param points          positivo acumula, negativo descuenta
     * @param previousBalance saldo antes del asiento; el resultante se calcula aqui para que nunca
     *                        llegue desde fuera
     */
    public static LoyaltyPointsMovement post(LoyaltyProgram program, UUID patientPublicId,
            LoyaltyMovementType type, int points, int previousBalance, UUID invoicePublicId,
            UUID appointmentPublicId, UUID sourceEventPublicId, String reason, UUID registeredByUserPublicId) {
        LoyaltyPointsMovement movement = new LoyaltyPointsMovement();
        movement.publicId = UUID.randomUUID();
        movement.program = Objects.requireNonNull(program, "program");
        movement.patientPublicId = Objects.requireNonNull(patientPublicId, "patientPublicId");
        movement.type = Objects.requireNonNull(type, "type");
        movement.points = points;
        movement.resultingBalance = previousBalance + points;
        movement.invoicePublicId = invoicePublicId;
        movement.appointmentPublicId = appointmentPublicId;
        movement.sourceEventPublicId = sourceEventPublicId;
        movement.reason = reason;
        movement.registeredByUserPublicId = registeredByUserPublicId;
        return movement;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public LoyaltyProgram getProgram() {
        return program;
    }

    public UUID getPatientPublicId() {
        return patientPublicId;
    }

    public LoyaltyMovementType getType() {
        return type;
    }

    public int getPoints() {
        return points;
    }

    public int getResultingBalance() {
        return resultingBalance;
    }

    public UUID getInvoicePublicId() {
        return invoicePublicId;
    }

    public UUID getAppointmentPublicId() {
        return appointmentPublicId;
    }

    public UUID getSourceEventPublicId() {
        return sourceEventPublicId;
    }

    public String getReason() {
        return reason;
    }

    public UUID getRegisteredByUserPublicId() {
        return registeredByUserPublicId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
