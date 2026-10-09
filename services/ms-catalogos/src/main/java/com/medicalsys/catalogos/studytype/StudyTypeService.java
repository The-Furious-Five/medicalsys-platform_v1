package com.medicalsys.catalogos.studytype;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class StudyTypeService {

    private final StudyTypeRepository repository;

    public StudyTypeService(StudyTypeRepository repository) {
        this.repository = repository;
    }

    public List<StudyTypeDto> findAllActive() {
        return repository.findByActiveTrue()
                .stream()
                .map(StudyTypeDto::from)
                .toList();
    }

    public StudyTypeDto findByCode(String code) {
        return repository.findByCode(code)
                .filter(StudyType::isActive)
                .map(StudyTypeDto::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "STUDY_TYPE_NOT_FOUND"));
    }
}
