package com.medicalsys.documental.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "consent_template",
        uniqueConstraints = {
            @UniqueConstraint(name = "uq_consent_template_public_id", columnNames = "public_id"),
            @UniqueConstraint(name = "uq_consent_template_organization_public_id_code_version",
                    columnNames = {"organization_public_id", "code", "version"})
        })
public class ConsentTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false,
            columnDefinition = "BINARY(16)")
    private UUID publicId;

    @Column(name = "organization_public_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID organizationPublicId;

    @Column(name = "code", nullable = false, updatable = false, length = 40)
    private String code;

    @Column(name = "version", nullable = false, updatable = false)
    private short version;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "specialty_code", length = 20)
    private String specialtyCode;

    @Column(name = "markdown_body", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String markdownBody;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variables", columnDefinition = "json")
    private List<String> variables;

    @Column(name = "requires_witness", nullable = false)
    private boolean requiresWitness;

    @Column(name = "requires_guardian_for_minor", nullable = false)
    private boolean requiresGuardianForMinor;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ConsentTemplateStatus status;

    @Column(name = "body_hash", nullable = false, length = 64)
    private String bodyHash;

    @Column(name = "created_by_user_public_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID createdByUserPublicId;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected ConsentTemplate() {
        // Requerido por JPA.
    }

    public static ConsentTemplate draft(UUID publicId, UUID organizationPublicId, String code, int version,
            String title, String specialtyCode, String markdownBody, List<String> variables,
            boolean requiresWitness, boolean requiresGuardianForMinor, LocalDate validFrom, LocalDate validUntil,
            String bodyHash, UUID createdByUserPublicId) {
        ConsentTemplate template = new ConsentTemplate();
        template.publicId = Objects.requireNonNull(publicId, "publicId");
        template.organizationPublicId = organizationPublicId;
        template.code = Objects.requireNonNull(code, "code");
        template.version = (short) version;
        template.title = Objects.requireNonNull(title, "title");
        template.specialtyCode = specialtyCode;
        template.markdownBody = Objects.requireNonNull(markdownBody, "markdownBody");
        template.variables = variables == null || variables.isEmpty() ? null : List.copyOf(variables);
        template.requiresWitness = requiresWitness;
        template.requiresGuardianForMinor = requiresGuardianForMinor;
        template.validFrom = Objects.requireNonNull(validFrom, "validFrom");
        template.validUntil = validUntil;
        template.status = ConsentTemplateStatus.DRAFT;
        template.bodyHash = Objects.requireNonNull(bodyHash, "bodyHash");
        template.createdByUserPublicId = createdByUserPublicId;
        return template;
    }

    public void publish() {
        if (status != ConsentTemplateStatus.DRAFT) {
            throw new IllegalStateException("Solo una plantilla en borrador puede publicarse");
        }
        status = ConsentTemplateStatus.PUBLISHED;
    }

    public void deprecate() {
        status = ConsentTemplateStatus.DEPRECATED;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public UUID getOrganizationPublicId() {
        return organizationPublicId;
    }

    public String getCode() {
        return code;
    }

    public int getVersion() {
        return version; // widen: el resto del servicio trabaja con int para no arrastrar short en cascada
    }

    public String getTitle() {
        return title;
    }

    public String getSpecialtyCode() {
        return specialtyCode;
    }

    public String getMarkdownBody() {
        return markdownBody;
    }

    public List<String> getVariables() {
        return variables;
    }

    public boolean isRequiresWitness() {
        return requiresWitness;
    }

    public boolean isRequiresGuardianForMinor() {
        return requiresGuardianForMinor;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public ConsentTemplateStatus getStatus() {
        return status;
    }

    public String getBodyHash() {
        return bodyHash;
    }

    public UUID getCreatedByUserPublicId() {
        return createdByUserPublicId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
