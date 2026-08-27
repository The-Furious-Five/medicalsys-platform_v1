package com.medicalsys.pacientes.domain.model;

import com.medicalsys.pacientes.domain.enums.Department;
import com.medicalsys.pacientes.domain.enums.DocumentType;
import com.medicalsys.pacientes.domain.enums.PatientStatus;
import com.medicalsys.pacientes.domain.enums.Sex;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Identidad administrativa y contacto del paciente ({@code patient}).
 *
 * <p>Su {@code public_id} viaja a scheduling_db, medical_records_db, documents_db y billing_db como
 * referencia externa sin FK: identifica a quien se atiende, sin que esas bases guarden sus datos.
 *
 * <p>No guarda contrasena (pertenece a identidad) ni diagnostico, alergia o narrativa (pertenecen a
 * historial). La direccion viaja cifrada y este servicio no la interpreta.
 */
@Entity
@Table(name = "patient")
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID publicId;

    /** Referencia externa a {@code user_account.public_id} de identity_db; sin FK. */
    @Column(name = "user_account_public_id", columnDefinition = "BINARY(16)")
    private UUID userAccountPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, updatable = false, length = 30)
    private DocumentType documentType;

    @Column(name = "document_number", nullable = false, updatable = false, length = 20)
    private String documentNumber;

    /**
     * Complemento del CI boliviano.
     *
     * <p>Nunca es nulo: la unicidad es sobre la terna (tipo, numero, complemento) y en MySQL un NULL
     * no colisiona con otro NULL, asi que un complemento nulo permitiria duplicar el documento.
     * La cadena vacia es el valor por defecto del diccionario y si participa en la restriccion.
     */
    @Column(name = "document_complement", nullable = false, updatable = false, length = 5)
    private String documentComplement;

    @Column(name = "given_names", nullable = false, length = 120)
    private String givenNames;

    @Column(name = "paternal_surname", nullable = false, length = 80)
    private String paternalSurname;

    @Column(name = "maternal_surname", length = 80)
    private String maternalSurname;

    /** Columna generada por el motor: {@code CONCAT_WS(' ', paterno, materno, nombres)}. */
    @Generated
    @Column(name = "full_name", insertable = false, updatable = false, length = 290)
    private String fullName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "sex", nullable = false, length = 20)
    private Sex sex;

    @Column(name = "phone_e164", length = 20)
    private String phoneE164;

    @Column(name = "alternate_phone", length = 20)
    private String alternatePhone;

    @Column(name = "email", length = 160)
    private String email;

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

    /**
     * Registra un paciente activo.
     *
     * <p>El {@code publicId} lo genera el servicio: es la identidad con la que agenda, historial,
     * documental y facturacion referenciaran a esta persona para siempre.
     */
    public static Patient register(DocumentType documentType, String documentNumber, String documentComplement,
            String givenNames, String paternalSurname, String maternalSurname, LocalDate birthDate, Sex sex) {
        Patient patient = new Patient();
        patient.publicId = UUID.randomUUID();
        patient.documentType = Objects.requireNonNull(documentType, "documentType");
        patient.documentNumber = normalizeDocument(documentNumber);
        patient.documentComplement = normalizeComplement(documentComplement);
        patient.givenNames = Objects.requireNonNull(givenNames, "givenNames").trim();
        patient.paternalSurname = Objects.requireNonNull(paternalSurname, "paternalSurname").trim();
        patient.maternalSurname = maternalSurname == null || maternalSurname.isBlank()
                ? null : maternalSurname.trim();
        patient.birthDate = Objects.requireNonNull(birthDate, "birthDate");
        patient.sex = sex == null ? Sex.UNDECLARED : sex;
        patient.status = PatientStatus.ACTIVE;
        return patient;
    }

    /** Normaliza el numero de documento: se compara y se guarda siempre en mayusculas y sin espacios. */
    public static String normalizeDocument(String documentNumber) {
        return Objects.requireNonNull(documentNumber, "documentNumber").trim().toUpperCase(Locale.ROOT);
    }

    /** Un complemento ausente se guarda como cadena vacia, nunca como NULL. */
    public static String normalizeComplement(String complement) {
        return complement == null ? "" : complement.trim().toUpperCase(Locale.ROOT);
    }

    /** Datos de contacto; todos opcionales. */
    public void contactAt(String phoneE164, String alternatePhone, String email, String city, Department department) {
        this.phoneE164 = phoneE164;
        this.alternatePhone = alternatePhone;
        this.email = email;
        this.city = city;
        this.department = department;
    }

    /** Contacto de emergencia; opcional. */
    public void emergencyContact(String name, String phone, String relationship) {
        this.emergencyContactName = name;
        this.emergencyContactPhone = phone;
        this.emergencyContactRelationship = relationship;
    }

    /** Vincula al paciente con su cuenta de usuario en identity_db. */
    public void linkUserAccount(UUID userAccountPublicId) {
        this.userAccountPublicId = userAccountPublicId;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public UUID getUserAccountPublicId() {
        return userAccountPublicId;
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

    public Sex getSex() {
        return sex;
    }

    public String getPhoneE164() {
        return phoneE164;
    }

    public String getAlternatePhone() {
        return alternatePhone;
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

    public String getEmergencyContactName() {
        return emergencyContactName;
    }

    public String getEmergencyContactPhone() {
        return emergencyContactPhone;
    }

    public String getEmergencyContactRelationship() {
        return emergencyContactRelationship;
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
