package com.medicalsys.profesionales.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "professional_specialty")
@IdClass(ProfessionalSpecialtyId.class)
public class ProfessionalSpecialty {
    @Id @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "health_professional_id") private HealthProfessional professional;
    @Id @Column(name = "specialty_code", length = 20) private String specialtyCode;
    @Column(name = "is_primary", nullable = false) private boolean primary;
    @Column(name = "graduation_year") private Short graduationYear;
    public HealthProfessional getProfessional() { return professional; }
    public String getSpecialtyCode() { return specialtyCode; }
    public boolean isPrimary() { return primary; }
    public Short getGraduationYear() { return graduationYear; }
    public void setProfessional(HealthProfessional value) { professional = value; }
    public void setSpecialtyCode(String value) { specialtyCode = value; }
    public void setPrimary(boolean value) { primary = value; }
    public void setGraduationYear(Short value) { graduationYear = value; }
}
