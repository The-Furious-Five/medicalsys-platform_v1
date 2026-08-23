package com.medicalsys.historial.repository;

import com.medicalsys.historial.domain.model.StudyResult;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a resultados de estudio. */
public interface StudyResultRepository extends JpaRepository<StudyResult, Long> {

    List<StudyResult> findByStudyOrderIdOrderByParameterAsc(Long studyOrderId);

    boolean existsByStudyOrderIdAndParameter(Long studyOrderId, String parameter);
}
