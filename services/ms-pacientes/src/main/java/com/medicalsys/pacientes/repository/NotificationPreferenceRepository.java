package com.medicalsys.pacientes.repository;

import com.medicalsys.pacientes.domain.enums.NotificationCategory;
import com.medicalsys.pacientes.domain.enums.NotificationChannel;
import com.medicalsys.pacientes.domain.model.NotificationPreference;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    List<NotificationPreference> findAllByPatient_PublicIdOrderByChannelAscCategoryAsc(UUID patientId);

    Optional<NotificationPreference> findByPatient_PublicIdAndChannelAndCategory(UUID patientId,
            NotificationChannel channel, NotificationCategory category);
}
