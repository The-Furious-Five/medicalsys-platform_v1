package com.medicalsys.auditoria.domain.model;

import com.medicalsys.auditoria.domain.enums.AuditCategory;
import com.medicalsys.auditoria.domain.enums.AuditOutcome;
import com.medicalsys.auditoria.domain.enums.AuditSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Entrada inmutable de la bitacora.
 *
 * <p>La clase no expone ningun mutador y ninguna ruta del servicio ofrece UPDATE ni DELETE: la
 * unica forma de cambiar la lectura de un hecho es asentar un hecho nuevo. Esa restriccion es la
 * que da valor probatorio a la bitacora de un sistema clinico.
 *
 * <p>Aqui no se guarda contenido clinico. Se guarda que ocurrio, sobre que recurso y quien lo hizo;
 * el dato sensible sigue viviendo cifrado en su propio microservicio. Una bitacora con el texto del
 * diagnostico duplicaria el riesgo que el resto de la plataforma se esfuerza en contener.
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "sequence_number", nullable = false, updatable = false,
            columnDefinition = "BIGINT UNSIGNED")
    private Long sequenceNumber;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "service_name", nullable = false, updatable = false, length = 40)
    private String serviceName;

    @Column(name = "action", nullable = false, updatable = false, length = 60)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, updatable = false, length = 30)
    private AuditCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, updatable = false, length = 20)
    private AuditSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, updatable = false, length = 20)
    private AuditOutcome outcome;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "actor_display_name", updatable = false, length = 160)
    private String actorDisplayName;

    @Column(name = "actor_roles", updatable = false, length = 200)
    private String actorRoles;

    @Column(name = "actor_ip", updatable = false, length = 45)
    private String actorIp;

    @Column(name = "resource_type", nullable = false, updatable = false, length = 60)
    private String resourceType;

    @Column(name = "resource_public_id", updatable = false)
    private UUID resourcePublicId;

    @Column(name = "resource_label", updatable = false, length = 160)
    private String resourceLabel;

    @Column(name = "patient_public_id", updatable = false)
    private UUID patientPublicId;

    @Column(name = "correlation_id", nullable = false, updatable = false, length = 64)
    private String correlationId;

    @Column(name = "request_method", updatable = false, length = 10)
    private String requestMethod;

    @Column(name = "request_path", updatable = false, length = 255)
    private String requestPath;

    @Column(name = "response_status", updatable = false)
    private Integer responseStatus;

    @Column(name = "reason", updatable = false, length = 255)
    private String reason;

    @Column(name = "metadata_json", updatable = false, length = 2000)
    private String metadataJson;

    @Column(name = "previous_hash", updatable = false, length = 64)
    private String previousHash;

    @Column(name = "record_hash", nullable = false, updatable = false, length = 64)
    private String recordHash;

    protected AuditEvent() {
        // Requerido por JPA.
    }

    /**
     * Constructor unico de la entidad.
     *
     * <p>Es deliberadamente ancho: un evento se arma de una sola vez o no se arma. La normalizacion
     * de cadenas y el calculo del hash ocurren en el servicio antes de llegar aqui, porque el hash
     * tiene que cubrir exactamente el contenido que se va a persistir.
     */
    public AuditEvent(UUID publicId, UUID organizationId, long sequenceNumber, Instant occurredAt,
            Instant recordedAt, String serviceName, String action, AuditCategory category,
            AuditSeverity severity, AuditOutcome outcome, UUID actorUserId, String actorDisplayName,
            String actorRoles, String actorIp, String resourceType, UUID resourcePublicId,
            String resourceLabel, UUID patientPublicId, String correlationId, String requestMethod,
            String requestPath, Integer responseStatus, String reason, String metadataJson,
            String previousHash, String recordHash) {
        this.publicId = publicId;
        this.organizationId = organizationId;
        this.sequenceNumber = sequenceNumber;
        this.occurredAt = occurredAt;
        this.recordedAt = recordedAt;
        this.serviceName = serviceName;
        this.action = action;
        this.category = category;
        this.severity = severity;
        this.outcome = outcome;
        this.actorUserId = actorUserId;
        this.actorDisplayName = actorDisplayName;
        this.actorRoles = actorRoles;
        this.actorIp = actorIp;
        this.resourceType = resourceType;
        this.resourcePublicId = resourcePublicId;
        this.resourceLabel = resourceLabel;
        this.patientPublicId = patientPublicId;
        this.correlationId = correlationId;
        this.requestMethod = requestMethod;
        this.requestPath = requestPath;
        this.responseStatus = responseStatus;
        this.reason = reason;
        this.metadataJson = metadataJson;
        this.previousHash = previousHash;
        this.recordHash = recordHash;
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public UUID getOrganizationId() { return organizationId; }
    public Long getSequenceNumber() { return sequenceNumber; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getRecordedAt() { return recordedAt; }
    public String getServiceName() { return serviceName; }
    public String getAction() { return action; }
    public AuditCategory getCategory() { return category; }
    public AuditSeverity getSeverity() { return severity; }
    public AuditOutcome getOutcome() { return outcome; }
    public UUID getActorUserId() { return actorUserId; }
    public String getActorDisplayName() { return actorDisplayName; }
    public String getActorRoles() { return actorRoles; }
    public String getActorIp() { return actorIp; }
    public String getResourceType() { return resourceType; }
    public UUID getResourcePublicId() { return resourcePublicId; }
    public String getResourceLabel() { return resourceLabel; }
    public UUID getPatientPublicId() { return patientPublicId; }
    public String getCorrelationId() { return correlationId; }
    public String getRequestMethod() { return requestMethod; }
    public String getRequestPath() { return requestPath; }
    public Integer getResponseStatus() { return responseStatus; }
    public String getReason() { return reason; }
    public String getMetadataJson() { return metadataJson; }
    public String getPreviousHash() { return previousHash; }
    public String getRecordHash() { return recordHash; }
}
