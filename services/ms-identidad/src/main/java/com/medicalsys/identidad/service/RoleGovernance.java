package com.medicalsys.identidad.service;

import com.medicalsys.identidad.domain.Permission;
import com.medicalsys.identidad.domain.PermissionModule;
import com.medicalsys.identidad.domain.Role;
import com.medicalsys.identidad.domain.RoleIncompatibility;
import com.medicalsys.identidad.domain.RoleScope;
import com.medicalsys.identidad.domain.UserAccount;
import com.medicalsys.identidad.domain.UserRole;
import com.medicalsys.identidad.repository.RoleIncompatibilityRepository;
import com.medicalsys.identidad.repository.UserAccountRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Reglas de segregacion de funciones y de no escalada que se aplican al asignar roles y al editar
 * sus permisos (RBAC-02). Estan juntas para que la politica de control de acceso (PO-02) tenga un
 * unico lugar en el codigo que la respalde.
 *
 * <p>Un actor {@code null} es un proceso interno (los seeders): no hay persona a la que aplicarle
 * segregacion, pero las reglas que protegen el estado (rol activo, pares incompatibles) siguen
 * valiendo.
 */
@Component
public class RoleGovernance {

    /**
     * Modulos cuyos permisos deciden el acceso de otras personas o de la organizacion entera. Quien
     * asigna un rol no puede otorgar con el ninguno de estos que no tenga: si no, un administrador
     * de centro podria fabricar un administrador de plataforma. Los permisos operativos (clinicos,
     * agenda, caja) si se delegan, porque dar de alta a un medico es parte de administrar un centro.
     */
    static final Set<PermissionModule> GOVERNANCE_MODULES = EnumSet.of(PermissionModule.IDENTITY,
            PermissionModule.TENANCY);

    private final UserAccountRepository users;
    private final RoleIncompatibilityRepository incompatibilities;

    public RoleGovernance(UserAccountRepository users, RoleIncompatibilityRepository incompatibilities) {
        this.users = users;
        this.incompatibilities = incompatibilities;
    }

    /** Quien ejecuta la operacion, con los roles que tiene vigentes hoy y no los que traia su token. */
    public record Actor(UserAccount account, Set<String> permissions, boolean platform) {

        public boolean holds(String permission) {
            return permissions.contains(permission);
        }
    }

    /** {@code null} solo para procesos internos; una sesion que no corresponde a una cuenta es un 403. */
    public Actor resolve(UUID actorPublicId) {
        if (actorPublicId == null) {
            return null;
        }
        UserAccount account = users.findByPublicId(actorPublicId)
                .orElseThrow(() -> ApiException.forbidden("La sesion no corresponde a una cuenta existente"));
        return actorOf(account, LocalDate.now());
    }

    static Actor actorOf(UserAccount account, LocalDate today) {
        Set<String> permissions = new HashSet<>();
        boolean platform = false;
        for (UserRole assignment : account.getRoleAssignments()) {
            if (!IdentityMapper.isEffective(assignment, today)) {
                continue;
            }
            Role role = assignment.getRole();
            role.getPermissions().forEach(permission -> permissions.add(permission.getCode()));
            platform |= role.getScope() == RoleScope.PLATFORM;
        }
        return new Actor(account, Set.copyOf(permissions), platform);
    }

    /** Reglas de quien asigna o revoca {@code role} a {@code target} en la organizacion indicada. */
    public void checkCanManageAssignment(Actor actor, UserAccount target, Role role, UUID assignmentOrganization) {
        if (actor == null) {
            return;
        }
        if (Objects.equals(actor.account().getPublicId(), target.getPublicId())) {
            throw ApiException.forbidden(
                    "Segregacion de funciones: nadie puede asignarse ni revocarse roles a si mismo");
        }
        if (role.getScope() == RoleScope.PLATFORM && !actor.platform()) {
            throw ApiException.forbidden(
                    "El rol " + role.getCode() + " es de plataforma: solo lo gestiona un rol de plataforma");
        }
        List<String> missing = missingGovernancePermissions(actor, role.getPermissions());
        if (!missing.isEmpty()) {
            throw ApiException.forbidden("El rol " + role.getCode()
                    + " otorga permisos de gestion de accesos que usted no tiene: " + String.join(", ", missing));
        }
        if (!actor.platform()) {
            UUID own = actor.account().getOrganizationPublicId();
            if (own == null || !own.equals(target.getOrganizationPublicId()) || !own.equals(assignmentOrganization)) {
                throw ApiException.forbidden("Solo puede gestionar roles de cuentas de su propia organizacion");
            }
        }
    }

    /** Nadie edita, desactiva ni cambia los permisos de un rol que tiene asignado. */
    public void checkDoesNotHold(Actor actor, Role role) {
        if (actor != null && holdsRole(actor.account(), role, LocalDate.now())) {
            throw ApiException.forbidden("Segregacion de funciones: no puede modificar el rol " + role.getCode()
                    + " porque lo tiene asignado");
        }
    }

    /** Al editar un rol solo se agregan permisos que quien edita ya tiene. Quitar nunca escala. */
    public void checkCanGrant(Actor actor, Collection<Permission> added) {
        if (actor == null) {
            return;
        }
        List<String> missing = added.stream()
                .map(Permission::getCode)
                .filter(code -> !actor.holds(code))
                .sorted()
                .toList();
        if (!missing.isEmpty()) {
            throw ApiException.forbidden("No puede otorgar permisos que usted no tiene: " + String.join(", ", missing));
        }
    }

    /** Rechaza la asignacion si la cuenta ya tiene un rol declarado incompatible con {@code role}. */
    public void checkCompatible(UserAccount target, Role role) {
        List<RoleIncompatibility> pairs = incompatibilities.findAllInvolving(role);
        firstConflict(target, role, pairs, LocalDate.now()).ifPresent(pair -> {
            throw ApiException.conflict("Segregacion de funciones: el rol " + role.getCode()
                    + " es incompatible con " + pair.other(role).getCode() + ", que la cuenta ya tiene ("
                    + pair.getReason() + ")");
        });
    }

    static List<String> missingGovernancePermissions(Actor actor, Collection<Permission> granted) {
        return granted.stream()
                .filter(permission -> GOVERNANCE_MODULES.contains(permission.getModule()))
                .map(Permission::getCode)
                .filter(code -> !actor.holds(code))
                .sorted()
                .toList();
    }

    /**
     * Cuenta un rol aunque este desactivado o su asignacion todavia no haya empezado: si se
     * reactiva, o llega la fecha, el conflicto apareceria sin que nadie lo haya aprobado.
     */
    static Optional<RoleIncompatibility> firstConflict(UserAccount target, Role role,
                                                       List<RoleIncompatibility> pairs, LocalDate today) {
        return pairs.stream()
                .filter(pair -> holdsRole(target, pair.other(role), today))
                .findFirst();
    }

    /** Asignacion no vencida: vigente hoy o con inicio futuro. */
    static boolean holdsRole(UserAccount account, Role role, LocalDate today) {
        return account.getRoleAssignments().stream()
                .anyMatch(assignment -> assignment.getRole().getCode().equals(role.getCode())
                        && (assignment.getValidUntil() == null || !assignment.getValidUntil().isBefore(today)));
    }
}
