package com.medicalsys.historial.web.dto;

import com.medicalsys.historial.domain.enums.StudyPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Solicitud de estudio complementario, por ejemplo una placa de rayos X.
 *
 * <p>Nace en estado REQUESTED y su publicId es el orderId que consume ms-agenda.
 *
 * @param studyTypeCode codigo del tipo de estudio en ms-catalogos, por ejemplo X_RAY
 * @param priority      prioridad; por defecto ROUTINE
 * @param instructions  indicaciones para la toma
 * @param icd10Code     diagnostico presuntivo que justifica el estudio, opcional
 */
public record CreateStudyOrderRequest(
        @NotBlank(message = "studyTypeCode es obligatorio") @Size(max = 30) String studyTypeCode,
        StudyPriority priority,
        @Size(max = 500) String instructions,
        @Size(max = 10) String icd10Code) {
}
