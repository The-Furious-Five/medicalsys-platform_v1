package com.medicalsys.identidad;

import static org.assertj.core.api.Assertions.assertThat;

import com.medicalsys.identidad.domain.PermissionModule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El catalogo granular (RBAC-01) vive en una migracion SQL que Hibernate solo valida al arrancar
 * contra MySQL. Estas pruebas leen el script y atrapan antes los errores que romperian ese
 * arranque o la autorizacion: un modulo que el enum no conoce, un codigo mal formado o un permiso
 * que SecurityConfig exige y que el catalogo no define.
 */
class PermissionCatalogMigrationTests {

    private static final String MIGRATION = "/db/migration/V3__granular_permission_catalog.sql";
    private static final Pattern ROW = Pattern.compile(
            "\\('([a-z0-9_.]+)', '([A-Z_]+)', '[^']*', (?:TRUE|FALSE)\\)");
    private static final Pattern MODULE_CHECK = Pattern.compile(
            "ADD CONSTRAINT ck_permission_module CHECK \\(module IN \\(([^;]*)\\)\\);");

    private static final Map<String, PermissionModule> MODULE_BY_PREFIX = Map.ofEntries(
            Map.entry("identity", PermissionModule.IDENTITY),
            Map.entry("tenancy", PermissionModule.TENANCY),
            Map.entry("catalogs", PermissionModule.CATALOGS),
            Map.entry("patients", PermissionModule.PATIENTS),
            Map.entry("professionals", PermissionModule.PROFESSIONALS),
            Map.entry("scheduling", PermissionModule.SCHEDULING),
            Map.entry("clinical", PermissionModule.CLINICAL),
            Map.entry("documents", PermissionModule.DOCUMENT_MANAGEMENT),
            Map.entry("billing", PermissionModule.BILLING),
            Map.entry("notifications", PermissionModule.NOTIFICATIONS),
            Map.entry("audit", PermissionModule.AUDIT),
            Map.entry("marketing", PermissionModule.MARKETING));

    private record Row(String code, String module) { }

    @Test
    @DisplayName("cada permiso tiene formato modulo.recurso.accion, es unico y cabe en la columna")
    void codesAreWellFormedAndUnique() throws IOException {
        List<Row> rows = rows();

        assertThat(rows).hasSizeGreaterThan(100);
        assertThat(rows).extracting(Row::code).doesNotHaveDuplicates();
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.code()).matches("[a-z]+\\.[a-z0-9_]+\\.[a-z_]+");
            assertThat(row.code().length()).isLessThanOrEqualTo(60);
        });
    }

    @Test
    @DisplayName("el modulo de cada permiso coincide con su prefijo y existe en el enum")
    void modulesMatchPrefixAndEnum() throws IOException {
        assertThat(rows()).allSatisfy(row -> {
            PermissionModule expected = MODULE_BY_PREFIX.get(row.code().substring(0, row.code().indexOf('.')));
            assertThat(expected).as("prefijo de %s", row.code()).isNotNull();
            assertThat(row.module()).as("modulo de %s", row.code()).isEqualTo(expected.name());
        });
    }

    @Test
    @DisplayName("el CHECK de la columna module admite exactamente los valores del enum")
    void moduleCheckMatchesEnum() throws IOException {
        Matcher check = MODULE_CHECK.matcher(resource("/db/migration/V11__approved_role_matrix.sql"));
        assertThat(check.find()).isTrue();

        List<String> allowed = Arrays.stream(check.group(1).split(","))
                .map(value -> value.trim().replace("'", ""))
                .toList();
        assertThat(allowed).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(PermissionModule.values()).map(Enum::name).toList());
    }

    @Test
    @DisplayName("los permisos que exige SecurityConfig estan en el catalogo")
    void securityConfigAuthoritiesExist() throws IOException {
        assertThat(rows()).extracting(Row::code).contains(
                "identity.user.read",
                "identity.user.create",
                "identity.user.update",
                "identity.user.change_status",
                "identity.role.read",
                "identity.role.assign");
    }

    private static List<Row> rows() throws IOException {
        Matcher matcher = ROW.matcher(migration());
        List<Row> rows = new ArrayList<>();
        while (matcher.find()) {
            rows.add(new Row(matcher.group(1), matcher.group(2)));
        }
        return rows;
    }

    private static String migration() throws IOException {
        return resource(MIGRATION);
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = PermissionCatalogMigrationTests.class.getResourceAsStream(path)) {
            assertThat(in).as(MIGRATION).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
