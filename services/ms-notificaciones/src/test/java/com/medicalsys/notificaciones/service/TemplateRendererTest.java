package com.medicalsys.notificaciones.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.medicalsys.notificaciones.domain.MessageTemplate;
import com.medicalsys.notificaciones.domain.NotificationCategory;
import com.medicalsys.notificaciones.domain.NotificationChannel;
import com.medicalsys.notificaciones.domain.TemplateApprovalStatus;
import com.medicalsys.notificaciones.error.ApiException;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer(JsonMapper.builder().build());

    @Test
    void rendersNamedAndPositionalPlaceholders() {
        MessageTemplate template = template("Hola {{patientName}}, su cita es el {{2}}.",
                "[\"patientName\",\"appointmentDate\"]");

        String result = renderer.render(template,
                Map.of("patientName", "Ana", "appointmentDate", "10/09/2026"));

        assertEquals("Hola Ana, su cita es el 10/09/2026.", result);
    }

    @Test
    void rejectsMissingRequiredVariable() {
        MessageTemplate template = template("Hola {{patientName}}", "[\"patientName\"]");
        ApiException error = assertThrows(ApiException.class, () -> renderer.render(template, Map.of()));
        assertEquals("MISSING_TEMPLATE_VARIABLE", error.code());
    }

    @Test
    void acceptsNullValuesMapWhenTemplateDeclaresNoVariables() {
        MessageTemplate template = template("Mensaje fijo", null);
        assertEquals("Mensaje fijo", renderer.render(template, null));
    }

    @Test
    void replacesEveryOccurrenceOfTheSameVariable() {
        MessageTemplate template = template("{{patientName}} / {{patientName}}", "[\"patientName\"]");
        assertEquals("Ana / Ana", renderer.render(template, Map.of("patientName", "Ana")));
    }

    @Test
    void ignoresVariablesNotDeclaredByTemplate() {
        MessageTemplate template = template("Hola", "[]");
        assertEquals("Hola", renderer.render(template, Map.of("unused", "value")));
    }

    @Test
    void rejectsCorruptedTemplateVariableJson() {
        MessageTemplate template = template("Hola", "not-json");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> renderer.render(template, Map.of()));
        assertEquals("La plantilla contiene variables JSON invalidas", error.getMessage());
    }

    private MessageTemplate template(String body, String variables) {
        LocalDateTime now = LocalDateTime.now();
        return new MessageTemplate(1L, "template-id", "organization-id", "APPOINTMENT_CREATED",
                NotificationChannel.WHATSAPP, NotificationCategory.UTILITY, "appointment_created", "es",
                null, body, variables, TemplateApprovalStatus.APPROVED, true, now, now);
    }
}
