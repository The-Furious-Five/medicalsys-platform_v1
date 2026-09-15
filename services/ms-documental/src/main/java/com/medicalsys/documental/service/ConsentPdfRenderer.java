package com.medicalsys.documental.service;

import com.medicalsys.documental.exception.ApiException;
import com.medicalsys.documental.exception.ErrorCode;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Renderiza el PDF de un consentimiento a partir del texto de una {@link com.medicalsys.documental.domain.ConsentTemplate}.
 *
 * <p>Sustitucion deliberadamente simple: {@code {{variable}}} por su valor, sin validar que todas
 * las variables declaradas por la plantilla esten presentes (a diferencia del renderer de
 * ms-notificaciones). Una variable ausente deja el hueco en blanco en vez de bloquear la generacion
 * del documento; el staff revisa el PDF antes de pedirle al paciente que lo firme.
 *
 * <p>El cuerpo de la plantilla se trata como texto plano (parrafos separados por linea en blanco),
 * no como markdown real: no vale la pena traer un parser de markdown para esto.
 */
@Component
public class ConsentPdfRenderer {

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*\\}\\}");

    public byte[] render(String title, String bodyText, Map<String, Object> variables) {
        String substituted = substitute(bodyText, variables == null ? Map.of() : variables);
        String html = toXhtml(title, substituted);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(output);
            builder.run();
            return output.toByteArray();
        } catch (IOException | RuntimeException exception) {
            throw new ApiException(ErrorCode.PDF_GENERATION_FAILED,
                    "No fue posible generar el PDF del consentimiento", exception);
        }
    }

    private String substitute(String bodyText, Map<String, Object> variables) {
        Matcher matcher = VARIABLE.matcher(bodyText);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            Object value = variables.get(matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String toXhtml(String title, String bodyText) {
        StringBuilder paragraphs = new StringBuilder();
        for (String paragraph : bodyText.split("\\r?\\n\\s*\\r?\\n")) {
            String trimmed = paragraph.trim();
            if (!trimmed.isEmpty()) {
                paragraphs.append("<p>").append(escape(trimmed).replace("\n", "<br/>")).append("</p>\n");
            }
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                <style>
                  body { font-family: sans-serif; font-size: 11pt; line-height: 1.4; }
                  h1 { font-size: 16pt; }
                  .footer { margin-top: 2em; font-size: 9pt; color: #555; }
                </style>
                </head>
                <body>
                <h1>%s</h1>
                %s
                <p class="footer">Documento generado electronicamente el %s.</p>
                </body>
                </html>
                """.formatted(escape(title), paragraphs, LocalDate.now());
    }

    private String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /** Variables minimas que el servicio siempre puede aportar, sin que el staff tenga que tipearlas. */
    public static Map<String, Object> baseVariables(String patientName) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("patientName", patientName);
        variables.put("date", LocalDate.now().toString());
        return variables;
    }
}
