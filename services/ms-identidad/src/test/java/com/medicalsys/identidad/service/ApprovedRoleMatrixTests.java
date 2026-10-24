package com.medicalsys.identidad.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.medicalsys.identidad.domain.AccessModel;
import com.medicalsys.identidad.domain.Permission;
import com.medicalsys.identidad.domain.PermissionModule;
import com.medicalsys.identidad.domain.Role;
import com.medicalsys.identidad.domain.RoleScope;
import com.medicalsys.identidad.domain.SecurityLevel;
import com.medicalsys.identidad.domain.UserAccount;
import com.medicalsys.identidad.domain.UserRole;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ApprovedRoleMatrixTests {
    private final IdentityMapper mapper = new IdentityMapper();

    @Test
    void allFifteenRolesHaveTheExactApprovedNameAndModel() throws Exception {
        String sql = migration();
        for (String row : List.of(
                "'PLATFORM_ADMIN', 'Admin. de plataforma'", "'OSI', 'Oficial Seguridad (OSI)'",
                "'AUDITOR', 'Auditor interno'", "'EXTERNAL_AUDITOR', 'Auditor externo'",
                "'ADMINISTRATOR', 'Admin. de organización'", "'PHYSICIAN', 'Médico'",
                "'MEDICAL_INTERN', 'Pasante de medicina'", "'NURSE', 'Enfermería'",
                "'LAB_TECHNICIAN', 'Laboratorio'", "'RECEPTIONIST', 'Recepción'",
                "'BILLING_CLERK', 'Cajero 1'", "'BILLING_SUPERVISOR', 'Cajero 2'",
                "'MARKETING_MANAGER', 'Marketing'", "'PATIENT', 'Paciente'",
                "'DEVOPS', 'Tecnología (DevOps)'")) {
            assertThat(sql).contains("(" + row + ",");
        }
        assertThat(grants("BILLING_CLERK")).contains("billing.invoice.issue")
                .doesNotContain("billing.invoice.void");
        assertThat(grants("BILLING_SUPERVISOR")).containsExactlyInAnyOrder("billing.invoice.read", "billing.invoice.void");
        assertThat(grants("EXTERNAL_AUDITOR")).containsExactly("audit.log.read");
        assertThat(grants("AUDITOR")).contains("audit.log.export");
        assertThat(grants("OSI")).doesNotContain("audit.log.export", "patients.patient.read");
        assertThat(grants("PLATFORM_ADMIN")).allMatch(p -> p.startsWith("tenancy."));
        assertThat(grants("DEVOPS")).containsExactlyInAnyOrder("infrastructure.server.manage", "infrastructure.database.manage");
        assertThat(grants("MEDICAL_INTERN")).doesNotContain("clinical.narrative.read", "clinical.encounter.sign", "clinical.encounter.open");
        assertThat(grants("ADMINISTRATOR")).contains("identity.role.assign_initial")
                .doesNotContain("identity.role.assign", "identity.user.change_status", "clinical.record.read", "billing.invoice.read");
        assertThat(grants("PATIENT")).allMatch(p -> p.startsWith("patients.self_access.") || p.startsWith("marketing.advertisement."));
    }

    @Test
    void clearanceOnlyFiltersTheMacRoleThatGrantsEachPermission() {
        UserAccount user = new UserAccount();
        user.setClearance(SecurityLevel.INTERNAL);
        Role cashier = role("BILLING_CLERK", AccessModel.RBAC, "billing.invoice.issue");
        Role external = role("EXTERNAL_AUDITOR", AccessModel.DAC, "audit.log.read");
        Role physician = role("PHYSICIAN", AccessModel.MAC, "clinical.narrative.read");
        assign(user, cashier, null);
        assign(user, external, null);
        assign(user, physician, null);
        assertThat(mapper.activePermissionCodes(user)).containsExactly("audit.log.read", "billing.invoice.issue");
        assertThat(RoleGovernance.actorOf(user, LocalDate.now()).permissions())
                .containsExactlyInAnyOrder("audit.log.read", "billing.invoice.issue");
        user.setClearance(SecurityLevel.RESTRICTED);
        assertThat(mapper.activePermissionCodes(user)).contains("clinical.narrative.read");
    }

    @Test
    void expiredDiscretionaryGrantsAndInactiveRolesDoNotIssuePermissions() {
        UserAccount user = new UserAccount();
        assign(user, role("EXTERNAL_AUDITOR", AccessModel.DAC, "audit.log.read"), LocalDate.now().minusDays(1));
        Role intern = role("MEDICAL_INTERN", AccessModel.DAC, "clinical.record.read");
        intern.setActive(false);
        assign(user, intern, null);
        assertThat(mapper.activePermissionCodes(user)).isEmpty();
    }

    private static Role role(String code, AccessModel model, String permissionCode) {
        Role role = Role.custom(code, code, null, RoleScope.ORGANIZATION);
        ReflectionTestUtils.setField(role, "accessModel", model);
        Permission permission = new Permission(permissionCode, PermissionModule.CLINICAL, permissionCode, true);
        permission.setClassification(SecurityLevel.RESTRICTED);
        role.getPermissions().add(permission);
        return role;
    }

    private static void assign(UserAccount user, Role role, LocalDate until) {
        user.getRoleAssignments().add(new UserRole(user, role, null, null, LocalDate.now().minusDays(2), until, null));
    }

    private static Set<String> grants(String role) throws Exception {
        var block = Pattern.compile("WHERE r.code = '" + role + "' AND p.code IN \\(([^;]+)\\);").matcher(migration());
        assertThat(block.find()).isTrue();
        return Pattern.compile("'([^']+)'").matcher(block.group(1)).results().map(m -> m.group(1)).collect(Collectors.toSet());
    }

    private static String migration() throws Exception {
        try (var input = ApprovedRoleMatrixTests.class.getResourceAsStream("/db/migration/V11__approved_role_matrix.sql")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
