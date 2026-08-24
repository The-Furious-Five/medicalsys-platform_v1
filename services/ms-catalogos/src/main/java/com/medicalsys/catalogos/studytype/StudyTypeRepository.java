package com.medicalsys.catalogos.studytype;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface StudyTypeRepository extends JpaRepository<StudyType, Integer> {
    List<StudyType>    findByActiveTrue();
    Optional<StudyType> findByCode(String code);
}
