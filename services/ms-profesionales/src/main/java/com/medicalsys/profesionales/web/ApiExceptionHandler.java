package com.medicalsys.profesionales.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> duplicate(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("DUPLICATE_PROFESSIONAL", "La matrícula, usuario o documento ya está registrado"));
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> status(ResponseStatusException ex) {
        String code = ex.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)
                ? "PROFESSIONAL_NOT_FOUND" : "INVALID_PROFESSIONAL_REQUEST";
        return ResponseEntity.status(ex.getStatusCode()).body(new ApiError(code, ex.getReason()));
    }
}
