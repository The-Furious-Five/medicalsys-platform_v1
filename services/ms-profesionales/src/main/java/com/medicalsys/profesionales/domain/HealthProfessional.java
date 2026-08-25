package com.medicalsys.profesionales.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "health_professional")
public class HealthProfessional {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)") private UUID publicId;
    @Column(name = "user_account_public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)") private UUID userAccountPublicId;
    @Enumerated(EnumType.STRING) @Column(name = "document_type", nullable = false) private DocumentType documentType;
    @Column(name = "document_number", nullable = false, length = 20) private String documentNumber;
    @Column(name = "document_complement", nullable = false, length = 5) private String documentComplement;
    @Column(name = "given_names", nullable = false, length = 120) private String givenNames;
    @Column(name = "paternal_surname", nullable = false, length = 80) private String paternalSurname;
    @Column(name = "maternal_surname", length = 80) private String maternalSurname;
    @Enumerated(EnumType.STRING) @Column(name = "professional_type", nullable = false) private ProfessionalType professionalType;
    @Column(name = "professional_license_number", nullable = false, length = 40) private String professionalLicenseNumber;
    @Column(length = 160) private String title;
    @Column(length = 1000) private String biography;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ProfessionalStatus status;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @OneToMany(mappedBy = "professional", cascade = CascadeType.ALL, orphanRemoval = true) private List<ProfessionalSpecialty> specialties = new ArrayList<>();
    @OneToMany(mappedBy = "professional", cascade = CascadeType.ALL, orphanRemoval = true) private List<ProfessionalOrganization> organizations = new ArrayList<>();

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public UUID getUserAccountPublicId() { return userAccountPublicId; }
    public DocumentType getDocumentType() { return documentType; }
    public String getDocumentNumber() { return documentNumber; }
    public String getDocumentComplement() { return documentComplement; }
    public String getGivenNames() { return givenNames; }
    public String getPaternalSurname() { return paternalSurname; }
    public String getMaternalSurname() { return maternalSurname; }
    public ProfessionalType getProfessionalType() { return professionalType; }
    public String getProfessionalLicenseNumber() { return professionalLicenseNumber; }
    public String getTitle() { return title; }
    public String getBiography() { return biography; }
    public ProfessionalStatus getStatus() { return status; }
    public List<ProfessionalSpecialty> getSpecialties() { return specialties; }
    public List<ProfessionalOrganization> getOrganizations() { return organizations; }
    public void setPublicId(UUID value) { publicId = value; }
    public void setUserAccountPublicId(UUID value) { userAccountPublicId = value; }
    public void setDocumentType(DocumentType value) { documentType = value; }
    public void setDocumentNumber(String value) { documentNumber = value; }
    public void setDocumentComplement(String value) { documentComplement = value == null ? "" : value; }
    public void setGivenNames(String value) { givenNames = value; }
    public void setPaternalSurname(String value) { paternalSurname = value; }
    public void setMaternalSurname(String value) { maternalSurname = value; }
    public void setProfessionalType(ProfessionalType value) { professionalType = value; }
    public void setProfessionalLicenseNumber(String value) { professionalLicenseNumber = value; }
    public void setTitle(String value) { title = value; }
    public void setBiography(String value) { biography = value; }
    public void setStatus(ProfessionalStatus value) { status = value; }
    public void addSpecialty(ProfessionalSpecialty value) { value.setProfessional(this); specialties.add(value); }
    public void addOrganization(ProfessionalOrganization value) { value.setProfessional(this); organizations.add(value); }
}
