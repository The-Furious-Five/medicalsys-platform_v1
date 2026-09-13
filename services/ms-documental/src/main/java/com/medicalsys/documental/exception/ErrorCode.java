package com.medicalsys.documental.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    DOCUMENT_LINK_ALREADY_EXISTS(HttpStatus.CONFLICT),
    DATA_INTEGRITY_CONFLICT(HttpStatus.CONFLICT),
    EMPTY_FILE(HttpStatus.UNPROCESSABLE_ENTITY),
    FILE_TOO_LARGE(HttpStatus.UNPROCESSABLE_ENTITY),
    FILE_TYPE_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_RETENTION_DATE(HttpStatus.UNPROCESSABLE_ENTITY),
    DOCUMENT_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY),
    DOCUMENT_INTEGRITY_FAILURE(HttpStatus.INTERNAL_SERVER_ERROR),
    STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
