package com.medicalsys.marketing.web.controller;

import com.medicalsys.marketing.privacy.SegmentCriteriaField;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ruta publica de comprobacion del servicio y de su enrutamiento desde el gateway.
 *
 * <p>Devuelve ademas la lista de ejes de segmentacion permitidos. No es adorno: publica la frontera
 * de privacidad en el mismo contrato, de modo que quien integre pueda comprobar sin leer el codigo
 * que marketing no admite un criterio clinico.
 */
@RestController
@RequestMapping("/api/v1/marketing")
public class MarketingPingController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "service", "ms-marketing",
                "status", "ok",
                "publishes", "marketing.campana",
                "clinicalDataAccess", "none");
    }

    /** Ejes por los que se puede segmentar. Cualquier otro se rechaza con 422. */
    @GetMapping("/criterios-permitidos")
    public Map<String, Object> allowedCriteria() {
        List<Map<String, String>> fields = java.util.Arrays.stream(SegmentCriteriaField.values())
                .map(field -> Map.of("field", field.jsonName(), "description", field.description()))
                .map(map -> (Map<String, String>) map)
                .toList();
        return Map.of(
                "fields", fields,
                "note", "La segmentacion solo usa datos administrativos y demograficos. Ningun diagnostico "
                        + "CIE-10 ni campo del expediente participa jamas.");
    }
}
