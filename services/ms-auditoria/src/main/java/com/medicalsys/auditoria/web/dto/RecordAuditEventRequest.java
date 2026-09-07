package com.medicalsys.auditoria.web.dto;

import com.medicalsys.auditoria.domain.enums.AuditCategory;
import com.medicalsys.auditoria.domain.enums.AuditOutcome;
import com.medicalsys.auditoria.domain.enums.AuditSeverity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Evento que un microservicio pide asentar en la bitacora.
 *
 * <p>Solo circula por {@code /internal/v1/**}: el navegador nunca escribe auditoria, porque una
 * bitacora que el cliente puede redactar no prueba nada.
 *
 * @param eventId        identificador que elige el productor; permite reintentar sin duplicar
 * @param organizationId tenant del hecho; se usa solo si la llamada no trae X-Organization-Id
 * @param occurredAt     instante real del hecho segun el productor; por defecto, el de recepcion
 * @param serviceName    microservicio que origina el evento, en el formato {@code ms-*}
 * @param action         codigo estable del hecho, p. ej. {@code PATIENT_REGISTERED}
 * @param category       area funcional para filtrar sin conocer el catalogo de acciones
 * @param severity       relevancia para un auditor; por defecto {@code INFO}
 * @param outcome        desenlace; por defecto {@code SUCCESS}
 * @param actorUserId    usuario que ejecuto la accion; nulo si la origino la plataforma
 * @param actorRoles     roles vigentes del actor al momento del hecho
 * @param resourceType   tipo de recurso afectado, p. ej. {@code PATIENT}
 * @param patientPublicId paciente afectado, cuando aplica; habilita la consulta por paciente
 * @param reason         motivo declarado de un rechazo o de un acceso excepcional
 * @param metadata       contexto adicional en pares clave/valor; jamas contenido clinico
 */
public record RecordAuditEventRequest(
        UUID eventId,

        UUID organizationId,

        Instant occurredAt,

        @NotBlank
        @Size(max = 40)
        @Pattern(regexp = "^ms-[a-z][a-z0-9-]{1,36}$",
                message = "debe ser el nombre de un microservicio, p. ej. ms-pacientes")
        String serviceName,

        @NotBlank
        @Size(max = 60)
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,59}$",
                message = "debe ser un codigo estable en mayusculas, p. ej. PATIENT_REGISTERED")
        String action,

        @NotNull
        AuditCategory category,

        AuditSeverity severity,

        AuditOutcome outcome,

        UUID actorUserId,

        @Size(max = 160)
        String actorDisplayName,

        @Size(max = 8)
        List<@NotBlank @Size(max = 40) String> actorRoles,

        @Size(max = 45)
        String actorIp,

        @NotBlank
        @Size(max = 60)
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,59}$",
                message = "debe ser un tipo de recurso en mayusculas, p. ej. PATIENT")
        String resourceType,

        UUID resourcePublicId,

        @Size(max = 160)
        String resourceLabel,

        UUID patientPublicId,

        @Size(max = 10)
        String requestMethod,

        @Size(max = 255)
        String requestPath,

        @Min(100)
        @Max(599)
        Integer responseStatus,

        @Size(max = 255)
        String reason,

        @Size(max = 20, message = "no puede llevar mas de 20 claves de contexto")
        Map<@NotBlank @Size(max = 40) String, @Size(max = 200) String> metadata) {

    /** Severidad efectiva; el productor puede omitirla y quedarse con la del contrato. */
    public AuditSeverity severityOrDefault() {
        return severity == null ? AuditSeverity.INFO : severity;
    }

    /** Desenlace efectivo; omitirlo significa que la operacion se completo. */
    public AuditOutcome outcomeOrDefault() {
        return outcome == null ? AuditOutcome.SUCCESS : outcome;
    }
}
