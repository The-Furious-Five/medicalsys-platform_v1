package com.medicalsys.pacientes.exception;

import com.medicalsys.pacientes.domain.DocumentType;

public class UnsupportedDocumentTypeException extends ApiException {

    public UnsupportedDocumentTypeException(DocumentType documentType) {
        super(ErrorCode.DOCUMENT_TYPE_NOT_SUPPORTED_IN_MVP,
                "El tipo de documento '" + documentType + "' no esta habilitado en este MVP");
    }
}
