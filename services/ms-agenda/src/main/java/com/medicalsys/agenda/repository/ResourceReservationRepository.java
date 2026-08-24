package com.medicalsys.agenda.repository;

import com.medicalsys.agenda.domain.ResourceReservation;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceReservationRepository
        extends JpaRepository<ResourceReservation, Long>, JpaSpecificationExecutor<ResourceReservation> {

    @Query("""
            select r from ResourceReservation r
            where r.physicalResource.id = :physicalResourceId
              and r.status <> com.medicalsys.agenda.domain.ReservationStatus.CANCELLED
              and r.startsAt < :endsAt
              and r.endsAt > :startsAt
            """)
    List<ResourceReservation> findOccupying(
            @Param("physicalResourceId") Long physicalResourceId,
            @Param("startsAt") OffsetDateTime startsAt,
            @Param("endsAt") OffsetDateTime endsAt);
}
