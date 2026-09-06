package com.medicalsys.marketing.web.dto;

import com.medicalsys.marketing.domain.enums.AdvertisementPlacement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Alta de un anuncio del portal.
 *
 * <p>{@code targetAudience} es un filtro ligero -- edad, sexo, ciudad -- y pasa por el mismo
 * guardian de privacidad que el criterio de un segmento: un anuncio dirigido por patologia seria la
 * misma filtracion, con otra ropa.
 *
 * @param title             titulo visible
 * @param content           cuerpo del anuncio
 * @param imageDocumentId   imagen almacenada en ms-documental; referencia debil
 * @param targetUrl         destino del clic
 * @param serviceOfferingId prestacion que permite agendar desde el anuncio
 * @param placement         zona del producto donde se muestra
 * @param priority          1 es la mayor prioridad; 10 la menor
 * @param targetAudience    filtro ligero de publico objetivo
 * @param validFrom         inicio de la vigencia
 * @param validUntil        fin de la vigencia, exclusivo
 */
public record CreateAdvertisementRequest(
        @NotBlank(message = "no debe estar vacio")
        @Size(max = 180, message = "debe tener como maximo 180 caracteres")
        String title,

        String content,

        UUID imageDocumentId,

        @Size(max = 500, message = "debe tener como maximo 500 caracteres")
        String targetUrl,

        UUID serviceOfferingId,

        AdvertisementPlacement placement,

        @Min(value = 1, message = "debe estar entre 1 y 10")
        @Max(value = 10, message = "debe estar entre 1 y 10")
        Integer priority,

        Map<String, Object> targetAudience,

        @NotNull(message = "es obligatoria")
        OffsetDateTime validFrom,

        @NotNull(message = "es obligatoria")
        OffsetDateTime validUntil) {
}
