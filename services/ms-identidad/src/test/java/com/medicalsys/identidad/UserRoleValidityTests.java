package com.medicalsys.identidad;

import static org.assertj.core.api.Assertions.assertThat;

import com.medicalsys.identidad.domain.Permission;
import com.medicalsys.identidad.domain.PermissionModule;
import com.medicalsys.identidad.domain.Role;
import com.medicalsys.identidad.domain.RoleScope;
import com.medicalsys.identidad.domain.UserAccount;
import com.medicalsys.identidad.domain.UserRole;
import com.medicalsys.identidad.service.IdentityMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La vigencia de user_role decide que roles entran en el JWT. */
class UserRoleValidityTests {

    private final UserAccount user = new UserAccount();
    private final Role role = new Role();

    @Test
    @DisplayName("una asignacion sin fecha final esta vigente desde su inicio")
    void openEndedAssignmentIsActive() {
        UserRole assignment = new UserRole(user, role, null, null, LocalDate.of(2026, 1, 1), null, null);

        assertThat(assignment.isActiveOn(LocalDate.of(2025, 12, 31))).isFalse();
        assertThat(assignment.isActiveOn(LocalDate.of(2026, 1, 1))).isTrue();
        assertThat(assignment.isActiveOn(LocalDate.of(2030, 6, 15))).isTrue();
    }

    @Test
    @DisplayName("una asignacion con fecha final incluye el ultimo dia y excluye el siguiente")
    void boundedAssignmentIsInclusive() {
        UserRole assignment = new UserRole(user, role, null, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), null);

        assertThat(assignment.isActiveOn(LocalDate.of(2026, 3, 31))).isTrue();
        assertThat(assignment.isActiveOn(LocalDate.of(2026, 4, 1))).isFalse();
    }

    @Test
    @DisplayName("sin fecha de inicio explicita la asignacion vale desde hoy")
    void defaultsToToday() {
        UserRole assignment = new UserRole(user, role, null, null, null, null, null);

        assertThat(assignment.getValidFrom()).isEqualTo(LocalDate.now());
        assertThat(assignment.isActiveOn(LocalDate.now())).isTrue();
    }

    @Test
    @DisplayName("un rol desactivado no aporta roles ni permisos al token aunque la asignacion siga vigente")
    void deactivatedRoleGrantsNothing() {
        Role auditor = Role.custom("AUDITOR", "Auditor", null, RoleScope.ORGANIZATION);
        auditor.getPermissions().add(new Permission("audit.log.read", PermissionModule.AUDIT, "Leer", true));
        user.getRoleAssignments().add(new UserRole(user, auditor, null, null, LocalDate.of(2020, 1, 1), null, null));
        IdentityMapper mapper = new IdentityMapper();

        assertThat(mapper.activeRoleCodes(user)).containsExactly("AUDITOR");
        assertThat(mapper.activePermissionCodes(user)).containsExactly("audit.log.read");

        auditor.setActive(false);

        assertThat(mapper.activeRoleCodes(user)).isEmpty();
        assertThat(mapper.activePermissionCodes(user)).isEmpty();
        assertThat(mapper.toRoleAssignment(user.getRoleAssignments().getFirst()).active()).isFalse();
    }
}
