package com.medicalsys.marketing.event;

import com.medicalsys.marketing.common.error.BusinessRuleException;
import com.medicalsys.marketing.common.error.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lectura tipada y tolerante del cuerpo de un evento entrante.
 *
 * <p>Un productor puede enviar campos de mas: se ignoran. Lo que no se tolera es un campo obligatorio
 * ausente o con formato invalido, porque proyectar a medias deja la replica peor que no proyectar --
 * un gasto sumado sin su fecha, por ejemplo, mueve la audiencia de un segmento sin dejar rastro de
 * por que.
 *
 * <p>Los errores salen como {@link ErrorCode#EVENT_NOT_SUPPORTED}, que es un 422: la peticion estaba
 * bien formada y el problema es que su contenido no se puede aplicar.
 */
final class EventPayload {

    private final Map<String, Object> values;
    private final String eventType;

    EventPayload(Map<String, Object> values, String eventType) {
        this.values = values == null ? Map.of() : values;
        this.eventType = eventType;
    }

    /** UUID obligatorio. */
    UUID requireUuid(String field) {
        return uuid(field).orElseThrow(() -> missing(field));
    }

    /** UUID opcional. */
    Optional<UUID> uuid(String field) {
        return text(field).map(raw -> {
            try {
                return UUID.fromString(raw);
            } catch (IllegalArgumentException e) {
                throw invalid(field, "no es un UUID valido");
            }
        });
    }

    /** Cadena opcional; una cadena en blanco se trata como ausente. */
    Optional<String> text(String field) {
        Object value = values.get(field);
        if (value == null) {
            return Optional.empty();
        }
        String raw = String.valueOf(value).trim();
        return raw.isEmpty() ? Optional.empty() : Optional.of(raw);
    }

    /** Fecha ISO-8601 opcional. Acepta tambien un instante con hora y se queda con la fecha. */
    Optional<LocalDate> date(String field) {
        return text(field).map(raw -> {
            try {
                return raw.length() > 10 ? LocalDate.parse(raw.substring(0, 10)) : LocalDate.parse(raw);
            } catch (DateTimeParseException e) {
                throw invalid(field, "no es una fecha ISO-8601");
            }
        });
    }

    /** Importe decimal opcional. */
    Optional<BigDecimal> decimal(String field) {
        return text(field).map(raw -> {
            try {
                return new BigDecimal(raw);
            } catch (NumberFormatException e) {
                throw invalid(field, "no es un numero decimal");
            }
        });
    }

    /** Entero largo opcional; se usa para la version de la replica. */
    long version() {
        return text("version").map(raw -> {
            try {
                return Long.parseLong(raw);
            } catch (NumberFormatException e) {
                throw invalid("version", "no es un entero");
            }
        }).orElse(0L);
    }

    /** Booleano opcional. */
    Optional<Boolean> flag(String field) {
        return text(field).map(raw -> Boolean.parseBoolean(raw.toLowerCase(Locale.ROOT)));
    }

    /** Valor de enumeracion opcional; un valor desconocido es un error, no un silencio. */
    <E extends Enum<E>> Optional<E> enumeration(String field, Class<E> type) {
        return text(field).map(raw -> {
            try {
                return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw invalid(field, "no es un valor reconocido de " + type.getSimpleName());
            }
        });
    }

    private BusinessRuleException missing(String field) {
        return new BusinessRuleException(ErrorCode.EVENT_NOT_SUPPORTED,
                "El evento '" + eventType + "' no trae el campo obligatorio '" + field + "'");
    }

    private BusinessRuleException invalid(String field, String reason) {
        return new BusinessRuleException(ErrorCode.EVENT_NOT_SUPPORTED,
                "El campo '" + field + "' del evento '" + eventType + "' " + reason);
    }
}
