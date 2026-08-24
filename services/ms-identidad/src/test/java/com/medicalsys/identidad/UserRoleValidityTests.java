package com.medicalsys.identidad;

import static org.assertj.core.api.Assertions.assertThat;

import com.medicalsys.identidad.domain.Role;
import com.medicalsys.identidad.domain.UserAccount;
import com.medicalsys.identidad.domain.UserRole;
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
}
