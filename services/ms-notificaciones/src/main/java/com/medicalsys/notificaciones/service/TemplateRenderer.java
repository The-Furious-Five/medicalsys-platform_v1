package com.medicalsys.notificaciones.service;

import com.medicalsys.notificaciones.domain.MessageTemplate;
import com.medicalsys.notificaciones.error.ApiException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class TemplateRenderer {

    private final ObjectMapper objectMapper;

    public TemplateRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String render(MessageTemplate template, Map<String, Object> values) {
        String rendered = template.body();
        Map<String, Object> safeValues = values == null ? Map.of() : values;
        List<String> declared = variables(template.variables());
        for (int index = 0; index < declared.size(); index++) {
            String name = declared.get(index);
            Object value = safeValues.get(name);
            if (value == null) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MISSING_TEMPLATE_VARIABLE",
                        "Falta la variable requerida por la plantilla: " + name);
            }
            String replacement = String.valueOf(value);
            rendered = rendered.replace("{{" + name + "}}", replacement)
                    .replace("{{" + (index + 1) + "}}", replacement);
        }
        return rendered;
    }

    private List<String> variables(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return Arrays.asList(objectMapper.readValue(json, String[].class));
        } catch (JacksonException exception) {
            throw new IllegalStateException("La plantilla contiene variables JSON invalidas", exception);
        }
    }
}
