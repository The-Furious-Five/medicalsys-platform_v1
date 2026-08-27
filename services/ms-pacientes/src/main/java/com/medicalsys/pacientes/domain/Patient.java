package com.medicalsys.pacientes.domain;

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
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(
    name = "patient",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_patient_document_type_document_number_document_complement",
            columnNames = {
                "document_type",
                "document_number",
                "document_complement"
            }
        )
    }
)
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false,
            columnDefinition = "BINARY(16)")
    private UUID publicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType documentType;

    @Column(name = "document_number", nullable = false, length = 20)
    private String documentNumber;

    @Column(name = "document_complement", nullable = false, length = 5)
    private String documentComplement = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "issued_department", length = 20)
    private Department issuedDepartment;

    @Column(name = "given_names", nullable = false, length = 90)
    private String givenNames;

    @Column(name = "paternal_surname", nullable = false, length = 60)
    private String paternalSurname;

    @Column(name = "maternal_surname", length = 60)
    private String maternalSurname;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "full_name", length = 290, insertable = false, updatable = false)
    private String fullName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "sex", nullable = false, length = 20)
    private PatientSex sex;

    @Column(name = "phone_e164", length = 20)
    private String phoneE164;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "city", length = 80)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(name = "department", length = 20)
    private Department department;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PatientStatus status;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Patient() {
        // Requerido por JPA.
    }

    public static Patient create(DocumentType documentType, String documentNumber, String documentComplement,
            Department issuedDepartment, String givenNames, String paternalSurname, String maternalSurname,
            LocalDate birthDate, PatientSex sex, String phoneE164, String email, String city,
            Department department) {
        Patient patient = new Patient();
        patient.publicId = UUID.randomUUID();
        patient.documentType = Objects.requireNonNull(documentType, "documentType");
        patient.documentNumber = Objects.requireNonNull(documentNumber, "documentNumber");
        patient.documentComplement = Objects.requireNonNull(documentComplement, "documentComplement");
        patient.issuedDepartment = issuedDepartment;
        patient.givenNames = Objects.requireNonNull(givenNames, "givenNames");
        patient.paternalSurname = Objects.requireNonNull(paternalSurname, "paternalSurname");
        patient.maternalSurname = Objects.requireNonNull(maternalSurname, "maternalSurname");
        patient.birthDate = Objects.requireNonNull(birthDate, "birthDate");
        patient.sex = sex == null ? PatientSex.UNDECLARED : sex;
        patient.phoneE164 = Objects.requireNonNull(phoneE164, "phoneE164");
        patient.email = email;
        patient.city = city;
        patient.department = department;
        patient.status = PatientStatus.ACTIVE;
        return patient;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public String getDocumentComplement() {
        return documentComplement;
    }

    public Department getIssuedDepartment() {
        return issuedDepartment;
    }

    public String getGivenNames() {
        return givenNames;
    }

    public String getPaternalSurname() {
        return paternalSurname;
    }

    public String getMaternalSurname() {
        return maternalSurname;
    }

    public String getFullName() {
        return fullName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public PatientSex getSex() {
        return sex;
    }

    public String getPhoneE164() {
        return phoneE164;
    }

    public String getEmail() {
        return email;
    }

    public String getCity() {
        return city;
    }

    public Department getDepartment() {
        return department;
    }

    public PatientStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
