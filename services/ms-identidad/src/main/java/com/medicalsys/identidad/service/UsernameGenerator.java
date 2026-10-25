package com.medicalsys.identidad.service;

import com.medicalsys.identidad.repository.UserAccountRepository;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Nombre de usuario estándar (IAM-07): {@code nombre.apellido.i}, donde {@code i} es la inicial del
 * segundo apellido si lo hay. Primer nombre y primer apellido completos, en minúsculas, sin tildes ni
 * partículas («de», «del», «la»...). Si ya existe, se agrega un número: {@code nombre.apellido.i2}.
 *
 * <p>Ejemplos: Ricardo Salazar Peredo → {@code ricardo.salazar.p}; Lucía Quispe de Rojas →
 * {@code lucia.quispe.r}; Juan Pérez → {@code juan.perez}; María José Núñez del Castillo →
 * {@code maria.nunez.c}. Reglas y fundamentos: docs/seguridad/IAM-07-usuario-y-segundo-factor.md.
 */
@Component
public class UsernameGenerator {

    static final int MAX_LENGTH = 40;
    private static final int MAX_GIVEN = 15;
    // 15 + "." + 19 + ".i" + hasta 3 cifras de desempate = 40, el largo de la columna.
    private static final int MAX_SURNAME = 19;

    /** Se omiten en el usuario; en el nombre completo se conservan. */
    private static final Set<String> PARTICLES = Set.of(
            "de", "del", "la", "las", "los", "y", "e", "da", "das", "do", "dos", "di", "van", "von", "der");

    /** Nombres y apellidos tal como los escribió quien da de alta la cuenta. */
    public record NameParts(String givenNames, String firstSurname, String secondSurname) {

        public String fullName() {
            return String.join(" ", Arrays.stream(new String[] {givenNames, firstSurname, secondSurname})
                    .filter(StringUtils::hasText).map(String::trim).toList());
        }
    }

    private final UserAccountRepository users;

    public UsernameGenerator(UserAccountRepository users) {
        this.users = users;
    }

    /** El primer usuario libre para estos nombres. */
    public String next(NameParts parts) {
        String base = base(parts);
        // Siempre lleva nombre y apellido, así que nunca es «admin», «root» ni otro nombre genérico.
        if (!users.existsByUsername(base)) {
            return base;
        }
        for (int suffix = 2; suffix < 1000; suffix++) {
            String candidate = base + suffix;
            if (!users.existsByUsername(candidate)) {
                return candidate;
            }
        }
        throw ApiException.conflict("No hay un nombre de usuario libre para " + parts.fullName());
    }

    /** El usuario sin número de desempate. */
    static String base(NameParts parts) {
        String given = firstWord(parts.givenNames());
        String surname = join(parts.firstSurname());
        if (given.isEmpty() || surname.isEmpty()) {
            throw ApiException.badRequest("Indica al menos un nombre y un apellido para generar el usuario");
        }
        StringBuilder username = new StringBuilder(truncate(given, MAX_GIVEN))
                .append('.').append(truncate(surname, MAX_SURNAME));
        String second = join(parts.secondSurname());
        if (!second.isEmpty()) {
            username.append('.').append(second.charAt(0));
        }
        return username.toString();
    }

    /**
     * Divide un nombre completo escrito en un solo campo, para las cuentas anteriores a IAM-07: los dos
     * últimos bloques son los apellidos y lo anterior, los nombres. Una partícula va con la palabra que
     * la sigue («Quispe de Rojas» son dos apellidos). Con dos nombres y un apellido se equivoca, por
     * eso el alta pide los campos por separado.
     */
    public static NameParts parse(String fullName) {
        List<String> units = new ArrayList<>();
        StringBuilder pending = new StringBuilder();
        for (String token : (fullName == null ? "" : fullName.trim()).split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            pending.append(pending.isEmpty() ? "" : " ").append(token);
            if (!PARTICLES.contains(normalize(token))) {
                units.add(pending.toString());
                pending.setLength(0);
            }
        }
        if (!pending.isEmpty()) {
            units.add(pending.toString());
        }
        return switch (units.size()) {
            case 0 -> new NameParts("", "", "");
            case 1 -> new NameParts(units.get(0), "", "");
            case 2 -> new NameParts(units.get(0), units.get(1), "");
            default -> new NameParts(String.join(" ", units.subList(0, units.size() - 2)),
                    units.get(units.size() - 2), units.get(units.size() - 1));
        };
    }

    /** Primera palabra que no sea partícula, normalizada. */
    private static String firstWord(String value) {
        for (String token : words(value)) {
            if (!PARTICLES.contains(token)) {
                return token;
            }
        }
        return "";
    }

    /** Todas las palabras sin partículas, pegadas: «de la Cruz» → cruz, «San Martín» → sanmartin. */
    private static String join(String value) {
        StringBuilder joined = new StringBuilder();
        for (String token : words(value)) {
            if (!PARTICLES.contains(token)) {
                joined.append(token);
            }
        }
        return joined.toString();
    }

    private static List<String> words(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.trim().split("[\\s-]+")).map(UsernameGenerator::normalize)
                .filter(word -> !word.isEmpty()).toList();
    }

    /** Minúsculas ASCII: sin tildes, ñ → n, sin apóstrofos ni otros signos. */
    static String normalize(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
