package com.medicalsys.profesionales.domain;

import java.io.Serializable;
import java.util.Objects;

public class ProfessionalSpecialtyId implements Serializable {
    private Long professional;
    private String specialtyCode;
    public ProfessionalSpecialtyId() { }
    public ProfessionalSpecialtyId(Long professional, String specialtyCode) { this.professional = professional; this.specialtyCode = specialtyCode; }
    @Override public boolean equals(Object other) { return other instanceof ProfessionalSpecialtyId id && Objects.equals(professional, id.professional) && Objects.equals(specialtyCode, id.specialtyCode); }
    @Override public int hashCode() { return Objects.hash(professional, specialtyCode); }
}
