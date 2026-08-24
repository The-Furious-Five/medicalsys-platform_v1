package com.medicalsys.agenda.repository;

import com.medicalsys.agenda.domain.AppointmentStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentStatusHistoryRepository extends JpaRepository<AppointmentStatusHistory, Long> {

    List<AppointmentStatusHistory> findByAppointmentIdOrderByOccurredAtAsc(Long appointmentId);
}
