package com.medicalsys.pacientes.domain.model;

import com.medicalsys.pacientes.domain.enums.BloodType;
import com.medicalsys.pacientes.domain.enums.Department;
import com.medicalsys.pacientes.domain.enums.DocumentType;
import com.medicalsys.pacientes.domain.enums.MaritalStatus;
import com.medicalsys.pacientes.domain.enums.PatientStatus;
import com.medicalsys.pacientes.domain.enums.Sex;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/** Identidad administrativa completa del paciente almacenada en {@code patients_db.patient}. */
@Entity
@Table(name = "patient", uniqueConstraints = {
        @UniqueConstraint(name = "uq_patient_public_id", columnNames = "public_id"),
        @UniqueConstraint(name = "uq_patient_document_type_document_number_document_complement",
                columnNames = {"document_type", "document_number", "document_complement"}),
        @UniqueConstraint(name = "uq_patient_user_account_public_id", columnNames = "user_account_public_id")
})
public class Patient {

    public static final LocalDate MINIMUM_BIRTH_DATE = LocalDate.of(1900, 1, 1);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID publicId;

    /** Referencia externa a identity_db.user_account.public_id; deliberadamente sin FK. */
    @Column(name = "user_account_public_id", columnDefinition = "BINARY(16)")
    private UUID userAccountPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, updatable = false, length = 30)
    private DocumentType documentType;

    @Column(name = "document_number", nullable = false, updatable = false, length = 20)
    private String documentNumber;

    @Column(name = "document_complement", nullable = false, updatable = false, length = 5)
    private String documentComplement;

    @Enumerated(EnumType.STRING)
    @Column(name = "issued_department", length = 20)
    private Department issuedDepartment;

    @Column(name = "given_names", nullable = false, length = 120)
    private String givenNames;

    @Column(name = "paternal_surname", nullable = false, length = 80)
    private String paternalSurname;

    @Column(name = "maternal_surname", length = 80)
    private String maternalSurname;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "full_name", insertable = false, updatable = false, length = 290)
    private String fullName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "sex", nullable = false, length = 20)
    private Sex sex;

    @Enumerated(EnumType.STRING)
    @Column(name = "marital_status", nullable = false, length = 30)
    private MaritalStatus maritalStatus;

    @Convert(converter = BloodTypeConverter.class)
    @Column(name = "blood_type", nullable = false, length = 10)
    private BloodType bloodType;

    @Column(name = "occupation", length = 120)
    private String occupation;

    @Column(name = "phone_e164", length = 20)
    private String phoneE164;

    @Column(name = "alternate_phone", length = 20)
    private String alternatePhone;

    @Column(name = "email", length = 160)
    private String email;

    @Column(name = "encrypted_address", length = 1024, columnDefinition = "VARBINARY(1024)")
    private byte[] encryptedAddress;

    @Column(name = "city", length = 80)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(name = "department", length = 20)
    private Department department;

    @Column(name = "emergency_contact_name", length = 160)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 20)
    private String emergencyContactPhone;

    @Column(name = "emergency_contact_relationship", length = 60)
    private String emergencyContactRelationship;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guardian_patient_id")
    private Patient guardianPatient;

    /** Referencia externa a crypto_db.encryption_key.public_id; deliberadamente sin FK. */
    @Column(name = "encryption_key_public_id", columnDefinition = "BINARY(16)")
    private UUID encryptionKeyPublicId;

    @Column(name = "data_consent_at")
    private Instant dataConsentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PatientStatus status;

    @Column(name = "death_date")
    private LocalDate deathDate;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Patient() {
        // Requerido por JPA.
    }

    /** Crea la identidad inmutable del documento; el resto del perfil se completa por separado. */
    public static Patient register(DocumentType documentType, String documentNumber, String documentComplement) {
        Patient patient = new Patient();
        patient.publicId = UUID.randomUUID();
        patient.documentType = Objects.requireNonNull(documentType, "documentType");
        patient.documentNumber = normalizeDocument(documentNumber);
        patient.documentComplement = normalizeComplement(documentComplement);
        patient.sex = Sex.UNDECLARED;
        patient.maritalStatus = MaritalStatus.UNDECLARED;
        patient.bloodType = BloodType.UNKNOWN;
        patient.status = PatientStatus.ACTIVE;
        return patient;
    }

    public void updatePersonalData(Department issuedDepartment, String givenNames, String paternalSurname,
            String maternalSurname, LocalDate birthDate, Sex sex, MaritalStatus maritalStatus,
            BloodType bloodType, String occupation) {
        validateBirthDate(birthDate);
        this.issuedDepartment = issuedDepartment;
        this.givenNames = requiredText(givenNames, "givenNames");
        this.paternalSurname = requiredText(paternalSurname, "paternalSurname");
        this.maternalSurname = optionalText(maternalSurname);
        this.birthDate = birthDate;
        this.sex = sex == null ? Sex.UNDECLARED : sex;
        this.maritalStatus = maritalStatus == null ? MaritalStatus.UNDECLARED : maritalStatus;
        this.bloodType = bloodType == null ? BloodType.UNKNOWN : bloodType;
        this.occupation = optionalText(occupation);
    }

    public void updateContactData(String phoneE164, String alternatePhone, String email, byte[] encryptedAddress,
            String city, Department department, String emergencyContactName, String emergencyContactPhone,
            String emergencyContactRelationship) {
        this.phoneE164 = optionalText(phoneE164);
        this.alternatePhone = optionalText(alternatePhone);
        this.email = normalizeEmail(email);
        this.encryptedAddress = encryptedAddress == null ? null : Arrays.copyOf(encryptedAddress,
                encryptedAddress.length);
        this.city = optionalText(city);
        this.department = department;
        this.emergencyContactName = optionalText(emergencyContactName);
        this.emergencyContactPhone = optionalText(emergencyContactPhone);
        this.emergencyContactRelationship = optionalText(emergencyContactRelationship);
    }

    public void linkUserAccount(UUID userAccountPublicId) {
        this.userAccountPublicId = userAccountPublicId;
    }

    public void assignGuardian(Patient guardianPatient) {
        if (guardianPatient != null && Objects.equals(publicId, guardianPatient.publicId)) {
            throw new IllegalArgumentException("Un paciente no puede ser su propio tutor");
        }
        this.guardianPatient = guardianPatient;
    }

    public void configureEncryptionKey(UUID encryptionKeyPublicId) {
        this.encryptionKeyPublicId = encryptionKeyPublicId;
    }

    public void recordDataConsent(Instant dataConsentAt) {
        this.dataConsentAt = dataConsentAt;
    }

    /** Mantiene la regla de la base: solo DECEASED lleva fecha de fallecimiento. */
    public void changeStatus(PatientStatus status, LocalDate deathDate) {
        PatientStatus target = Objects.requireNonNull(status, "status");
        if (target == PatientStatus.DECEASED && deathDate == null) {
            throw new IllegalArgumentException("deathDate es obligatorio cuando el estado es DECEASED");
        }
        if (deathDate != null && (deathDate.isBefore(birthDate) || deathDate.isAfter(LocalDate.now()))) {
            throw new IllegalArgumentException("deathDate debe estar entre la fecha de nacimiento y la fecha actual");
        }
        if (target != PatientStatus.DECEASED && deathDate != null) {
            throw new IllegalArgumentException("deathDate solo puede informarse cuando el estado es DECEASED");
        }
        this.status = target;
        this.deathDate = deathDate;
    }

    public void markInactiveAfterMerge() {
        changeStatus(PatientStatus.INACTIVE, null);
    }

    public static String normalizeDocument(String documentNumber) {
        return requiredText(documentNumber, "documentNumber").toUpperCase(Locale.ROOT);
    }

    public static String normalizeComplement(String complement) {
        return complement == null ? "" : complement.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeEmail(String email) {
        String normalized = optionalText(email);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String requiredText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " no puede estar vacio");
        }
        return normalized;
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void validateBirthDate(LocalDate birthDate) {
        Objects.requireNonNull(birthDate, "birthDate");
        if (birthDate.isBefore(MINIMUM_BIRTH_DATE) || birthDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("birthDate debe estar entre 1900-01-01 y la fecha actual");
        }
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public UUID getUserAccountPublicId() { return userAccountPublicId; }
    public DocumentType getDocumentType() { return documentType; }
    public String getDocumentNumber() { return documentNumber; }
    public String getDocumentComplement() { return documentComplement; }
    public Department getIssuedDepartment() { return issuedDepartment; }
    public String getGivenNames() { return givenNames; }
    public String getPaternalSurname() { return paternalSurname; }
    public String getMaternalSurname() { return maternalSurname; }
    public String getFullName() { return fullName; }
    public LocalDate getBirthDate() { return birthDate; }
    public Sex getSex() { return sex; }
    public MaritalStatus getMaritalStatus() { return maritalStatus; }
    public BloodType getBloodType() { return bloodType; }
    public String getOccupation() { return occupation; }
    public String getPhoneE164() { return phoneE164; }
    public String getAlternatePhone() { return alternatePhone; }
    public String getEmail() { return email; }
    public byte[] getEncryptedAddress() {
        return encryptedAddress == null ? null : Arrays.copyOf(encryptedAddress, encryptedAddress.length);
    }
    public String getCity() { return city; }
    public Department getDepartment() { return department; }
    public String getEmergencyContactName() { return emergencyContactName; }
    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public String getEmergencyContactRelationship() { return emergencyContactRelationship; }
    public Patient getGuardianPatient() { return guardianPatient; }
    public UUID getEncryptionKeyPublicId() { return encryptionKeyPublicId; }
    public Instant getDataConsentAt() { return dataConsentAt; }
    public PatientStatus getStatus() { return status; }
    public LocalDate getDeathDate() { return deathDate; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
