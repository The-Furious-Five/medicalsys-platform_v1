package com.medicalsys.identidad.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medicalsys.identidad.domain.Permission;
import com.medicalsys.identidad.domain.PermissionModule;
import com.medicalsys.identidad.domain.Role;
import com.medicalsys.identidad.domain.RoleIncompatibility;
import com.medicalsys.identidad.domain.RoleScope;
import com.medicalsys.identidad.domain.UserAccount;
import com.medicalsys.identidad.domain.UserRole;
import com.medicalsys.identidad.service.RoleGovernance.Actor;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Reglas de segregacion de funciones y de no escalada de RBAC-02, sin base de datos. */
class RoleGovernanceTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);
    private static final UUID CLINIC = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final UUID OTHER_CLINIC = UUID.fromString("00000000-0000-4000-8000-0000000000b2");

    private static final Permission USER_READ = permission("identity.user.read", PermissionModule.IDENTITY);
    private static final Permission ROLE_ASSIGN = permission("identity.role.assign", PermissionModule.IDENTITY);
    private static final Permission ROLE_MANAGE = permission("identity.role.manage", PermissionModule.IDENTITY);
    private static final Permission ORG_CREATE = permission("tenancy.organization.create", PermissionModule.TENANCY);
    private static final Permission BRANCH_READ = permission("tenancy.branch.read", PermissionModule.TENANCY);
    private static final Permission ENCOUNTER_SIGN = permission("clinical.encounter.sign", PermissionModule.CLINICAL);

    private final RoleGovernance governance = new RoleGovernance(null, null);

    private final Role administrator = role("ADMINISTRATOR", RoleScope.ORGANIZATION, USER_READ, ROLE_ASSIGN, BRANCH_READ);
    private final Role physician = role("PHYSICIAN", RoleScope.ORGANIZATION, BRANCH_READ, ENCOUNTER_SIGN);
    private final Role platformAdmin = role("PLATFORM_ADMIN", RoleScope.PLATFORM,
            USER_READ, ROLE_ASSIGN, ROLE_MANAGE, ORG_CREATE, BRANCH_READ, ENCOUNTER_SIGN);

    private final UserAccount admin = account(CLINIC, administrator);
    private final UserAccount doctor = account(CLINIC);

    @Test
    @DisplayName("el administrador asigna un rol operativo a una cuenta de su centro aunque no tenga sus permisos clinicos")
    void adminDelegatesOperationalRole() {
        assertThatCode(() -> governance.checkCanManageAssignment(actor(admin), doctor, physician, CLINIC))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("nadie se asigna ni se revoca roles a si mismo")
    void noSelfAssignment() {
        assertForbidden(() -> governance.checkCanManageAssignment(actor(admin), admin, physician, CLINIC),
                "a si mismo");
    }

    @Test
    @DisplayName("un rol de plataforma solo lo gestiona un rol de plataforma")
    void platformRoleNeedsPlatformActor() {
        assertForbidden(() -> governance.checkCanManageAssignment(actor(admin), doctor, platformAdmin, null),
                "de plataforma");
    }

    @Test
    @DisplayName("no se otorga un permiso de gestion de accesos que quien asigna no tiene")
    void noGovernanceEscalation() {
        Role roleManager = role("ROLE_MANAGER", RoleScope.ORGANIZATION, ROLE_MANAGE, ORG_CREATE);

        assertForbidden(() -> governance.checkCanManageAssignment(actor(admin), doctor, roleManager, CLINIC),
                "identity.role.manage, tenancy.organization.create");
    }

    @Test
    @DisplayName("un administrador de centro no gestiona roles de otra organizacion")
    void adminStaysInsideOwnOrganization() {
        UserAccount foreign = account(OTHER_CLINIC);

        assertForbidden(() -> governance.checkCanManageAssignment(actor(admin), foreign, physician, OTHER_CLINIC),
                "su propia organizacion");
        assertForbidden(() -> governance.checkCanManageAssignment(actor(admin), doctor, physician, OTHER_CLINIC),
                "su propia organizacion");
    }

    @Test
    @DisplayName("el administrador de plataforma gestiona cualquier organizacion y los roles de plataforma")
    void platformActorIsNotBoundToOrganization() {
        UserAccount operator = account(CLINIC, platformAdmin);
        UserAccount foreign = account(OTHER_CLINIC);

        assertThatCode(() -> governance.checkCanManageAssignment(actor(operator), foreign, physician, OTHER_CLINIC))
                .doesNotThrowAnyException();
        assertThatCode(() -> governance.checkCanManageAssignment(actor(operator), foreign, platformAdmin, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un proceso interno (actor nulo) no esta sujeto a segregacion")
    void internalProcessSkipsActorRules() {
        assertThatCode(() -> governance.checkCanManageAssignment(null, doctor, platformAdmin, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("nadie modifica un rol que tiene asignado, pero una asignacion vencida no cuenta")
    void cannotEditOwnRole() {
        assertForbidden(() -> governance.checkDoesNotHold(actor(admin), administrator), "lo tiene asignado");

        UserAccount former = account(CLINIC);
        former.getRoleAssignments().add(new UserRole(former, administrator, CLINIC, null,
                LocalDate.now().minusYears(1), LocalDate.now().minusDays(1), null));
        assertThatCode(() -> governance.checkDoesNotHold(actor(former), administrator)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("al editar un rol solo se agregan permisos que quien edita ya tiene")
    void cannotGrantWhatYouLack() {
        assertForbidden(() -> governance.checkCanGrant(actor(admin), List.of(USER_READ, ENCOUNTER_SIGN)),
                "clinical.encounter.sign");
        assertThatCode(() -> governance.checkCanGrant(actor(admin), List.of(USER_READ, BRANCH_READ)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("los permisos del actor salen solo de roles activos con asignacion vigente")
    void actorPermissionsIgnoreInactiveRolesAndExpiredAssignments() {
        Role retired = role("RETIRED", RoleScope.PLATFORM, ROLE_MANAGE);
        retired.setActive(false);
        Role expired = role("EXPIRED", RoleScope.ORGANIZATION, ORG_CREATE);
        UserAccount account = account(CLINIC, administrator, retired);
        account.getRoleAssignments().add(new UserRole(account, expired, CLINIC, null,
                TODAY.minusMonths(2), TODAY.minusDays(1), null));

        Actor actor = RoleGovernance.actorOf(account, TODAY);

        assertThat(actor.permissions()).containsExactlyInAnyOrder("identity.user.read", "identity.role.assign",
                "tenancy.branch.read");
        assertThat(actor.platform()).isFalse();
    }

    @Test
    @DisplayName("un par incompatible bloquea aunque el otro rol este inactivo o empiece en el futuro")
    void incompatibilityCountsInactiveAndFutureAssignments() {
        Role billing = role("BILLING_CLERK", RoleScope.ORGANIZATION);
        RoleIncompatibility pair = new RoleIncompatibility(physician, billing, "quien atiende no cobra", null);

        UserAccount future = account(CLINIC);
        future.getRoleAssignments().add(new UserRole(future, billing, CLINIC, null, TODAY.plusDays(5), null, null));
        assertThat(RoleGovernance.firstConflict(future, physician, List.of(pair), TODAY)).contains(pair);

        billing.setActive(false);
        UserAccount dormant = account(CLINIC, billing);
        assertThat(RoleGovernance.firstConflict(dormant, physician, List.of(pair), TODAY)).contains(pair);

        UserAccount former = account(CLINIC);
        former.getRoleAssignments().add(new UserRole(former, billing, CLINIC, null,
                TODAY.minusYears(1), TODAY.minusDays(1), null));
        assertThat(RoleGovernance.firstConflict(former, physician, List.of(pair), TODAY)).isEmpty();
    }

    private static void assertForbidden(ThrowingCallable call, String detail) {
        assertThatThrownBy(call)
                .isInstanceOf(ApiException.class)
                .hasMessageContaining(detail)
                .extracting(error -> ((ApiException) error).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private static Actor actor(UserAccount account) {
        return RoleGovernance.actorOf(account, LocalDate.now());
    }

    private static Permission permission(String code, PermissionModule module) {
        return new Permission(code, module, code, false);
    }

    private static Role role(String code, RoleScope scope, Permission... permissions) {
        Role role = Role.custom(code, code, null, scope);
        role.getPermissions().addAll(List.of(permissions));
        return role;
    }

    private static UserAccount account(UUID organization, Role... roles) {
        UserAccount account = new UserAccount();
        account.setOrganizationPublicId(organization);
        for (Role role : roles) {
            UUID scope = role.getScope() == RoleScope.PLATFORM ? null : organization;
            account.getRoleAssignments().add(
                    new UserRole(account, role, scope, null, LocalDate.of(2020, 1, 1), null, null));
        }
        return account;
    }
}
