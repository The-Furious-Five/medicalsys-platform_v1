package com.medicalsys.agenda.repository;

import com.medicalsys.agenda.domain.ScheduleBlock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleBlockRepository
        extends JpaRepository<ScheduleBlock, Long>, JpaSpecificationExecutor<ScheduleBlock> {

    /** Bloqueos que afectan a un profesional: propios, de su sucursal o del recurso preferente. */
    @Query("""
            select b from ScheduleBlock b
            where b.organizationPublicId = :organizationPublicId
              and b.startsAt < :endsAt
              and b.endsAt > :startsAt
              and (b.healthProfessionalPublicId = :healthProfessionalPublicId
                   or (b.healthProfessionalPublicId is null and b.branchPublicId is null)
                   or b.branchPublicId = :branchPublicId)
            """)
    List<ScheduleBlock> findAffecting(
            @Param("organizationPublicId") UUID organizationPublicId,
            @Param("healthProfessionalPublicId") UUID healthProfessionalPublicId,
            @Param("branchPublicId") UUID branchPublicId,
            @Param("startsAt") OffsetDateTime startsAt,
            @Param("endsAt") OffsetDateTime endsAt);

    @Query("""
            select b from ScheduleBlock b
            where b.physicalResource.id = :physicalResourceId
              and b.startsAt < :endsAt
              and b.endsAt > :startsAt
            """)
    List<ScheduleBlock> findAffectingResource(
            @Param("physicalResourceId") Long physicalResourceId,
            @Param("startsAt") OffsetDateTime startsAt,
            @Param("endsAt") OffsetDateTime endsAt);
}
