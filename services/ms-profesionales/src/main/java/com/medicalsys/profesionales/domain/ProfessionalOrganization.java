package com.medicalsys.profesionales.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "professional_organization")
public class ProfessionalOrganization {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)") private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "health_professional_id", nullable = false) private HealthProfessional professional;
    @Column(name = "organization_public_id", nullable = false, columnDefinition = "BINARY(16)") private UUID organizationPublicId;
    @Column(name = "branch_public_id", columnDefinition = "BINARY(16)") private UUID branchPublicId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private OrganizationModality modality;
    @Column(name = "commission_percentage", precision = 5, scale = 2) private BigDecimal commissionPercentage;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date") private LocalDate endDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private OrganizationStatus status;
    public UUID getPublicId() { return publicId; }
    public UUID getOrganizationPublicId() { return organizationPublicId; }
    public UUID getBranchPublicId() { return branchPublicId; }
    public OrganizationModality getModality() { return modality; }
    public OrganizationStatus getStatus() { return status; }
    public BigDecimal getCommissionPercentage() { return commissionPercentage; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setProfessional(HealthProfessional value) { professional = value; }
    public void setPublicId(UUID value) { publicId = value; }
    public void setOrganizationPublicId(UUID value) { organizationPublicId = value; }
    public void setBranchPublicId(UUID value) { branchPublicId = value; }
    public void setModality(OrganizationModality value) { modality = value; }
    public void setCommissionPercentage(BigDecimal value) { commissionPercentage = value; }
    public void setStartDate(LocalDate value) { startDate = value; }
    public void setEndDate(LocalDate value) { endDate = value; }
    public void setStatus(OrganizationStatus value) { status = value; }
}
