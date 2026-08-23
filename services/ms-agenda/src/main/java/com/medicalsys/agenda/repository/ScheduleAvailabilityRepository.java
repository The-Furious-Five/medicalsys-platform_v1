package com.medicalsys.agenda.repository;

import com.medicalsys.agenda.domain.ScheduleAvailability;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleAvailabilityRepository extends JpaRepository<ScheduleAvailability, Long> {

    List<ScheduleAvailability> findByProfessionalOrganizationPublicIdOrderByDayOfWeekAscStartTimeAsc(
            UUID professionalOrganizationPublicId);

    @Query("""
            select a from ScheduleAvailability a
            where a.professionalOrganizationPublicId = :professionalOrganizationPublicId
              and a.active = true
              and a.dayOfWeek = :dayOfWeek
              and a.validFrom <= :date
              and (a.validUntil is null or a.validUntil >= :date)
            order by a.startTime asc
            """)
    List<ScheduleAvailability> findEffectiveFor(
            @Param("professionalOrganizationPublicId") UUID professionalOrganizationPublicId,
            @Param("dayOfWeek") short dayOfWeek,
            @Param("date") LocalDate date);
}
