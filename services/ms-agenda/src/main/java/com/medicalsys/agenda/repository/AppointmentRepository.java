package com.medicalsys.agenda.repository;

import com.medicalsys.agenda.domain.Appointment;
import com.medicalsys.agenda.domain.AppointmentStatus;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository
        extends JpaRepository<Appointment, Long>, JpaSpecificationExecutor<Appointment> {

    Optional<Appointment> findByPublicId(UUID publicId);

    Optional<Appointment> findByOrganizationPublicIdAndPublicId(UUID organizationPublicId, UUID publicId);

    @Query("""
            select a from Appointment a
            where a.healthProfessionalPublicId = :healthProfessionalPublicId
              and a.status in :statuses
              and a.startsAt < :endsAt
              and a.endsAt > :startsAt
            order by a.startsAt asc
            """)
    List<Appointment> findOccupying(
            @Param("healthProfessionalPublicId") UUID healthProfessionalPublicId,
            @Param("startsAt") OffsetDateTime startsAt,
            @Param("endsAt") OffsetDateTime endsAt,
            @Param("statuses") Collection<AppointmentStatus> statuses);
}
