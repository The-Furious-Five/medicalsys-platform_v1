package com.medicalsys.identidad.service;

import com.medicalsys.identidad.domain.Permission;
import com.medicalsys.identidad.domain.PermissionModule;
import com.medicalsys.identidad.domain.Role;
import com.medicalsys.identidad.domain.RoleIncompatibility;
import com.medicalsys.identidad.domain.RoleScope;
import com.medicalsys.identidad.domain.UserAccount;
import com.medicalsys.identidad.domain.UserRole;
import com.medicalsys.identidad.domain.UserStatus;
import com.medicalsys.identidad.repository.RoleIncompatibilityRepository;
import com.medicalsys.identidad.repository.UserAccountRepository;
import com.medicalsys.identidad.repository.UserRoleRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Reglas de control de acceso sobre la propia administracion de accesos (RBAC-02): segregacion de
 * funciones, delegacion explicita, no escalada y aislamiento entre organizaciones. Estan juntas
 * para que la politica de control de acceso (PO-02) tenga un unico lugar en el codigo que la
 * respalde.
 *
 * <p>Un actor {@code null} es un proceso interno (los seeders): no hay persona a la que aplicarle
 * segregacion, pero las reglas que protegen el estado (rol activo, pares incompatibles) siguen
 * valiendo.
 */
@Component
public class RoleGovernance {

    /**
     * Modulos cuyos permisos deciden el acceso de otras personas o de la organizacion entera. Nadie
     * otorga, edita ni gestiona por encima de lo que el mismo tiene en ellos: es lo que impide que un
     * administrador de centro fabrique un administrador de plataforma o desactive al que ya existe.
     */
    static final Set<PermissionModule> GOVERNANCE_MODULES = EnumSet.of(PermissionModule.IDENTITY,
            PermissionModule.TENANCY);

    /** Sin al menos una cuenta activa con este permiso, nadie podria volver a corregir los roles. */
    static final String ROLE_ADMINISTRATION = "identity.role.manage";

    private final UserAccountRepository users;
    private final UserRoleRepository userRoles;
    private final RoleIncompatibilityRepository incompatibilities;

    public RoleGovernance(UserAccountRepository users, UserRoleRepository userRoles,
                          RoleIncompatibilityRepository incompatibilities) {
        this.users = users;
        this.userRoles = userRoles;
        this.incompatibilities = incompatibilities;
    }

    /**
     * Quien ejecuta la operacion, con los roles que tiene vigentes hoy y no los que traia su token.
     * {@code platform} indica que tiene un rol de alcance plataforma: no lo limita ninguna
     * organizacion ni la lista de roles delegados.
     */
    public record Actor(UserAccount account, Set<String> permissions, Set<String> assignableRoles,
                        boolean platform) {

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
        Set<String> assignable = new HashSet<>();
        boolean platform = false;
        for (Role role : effectiveRoles(account, today)) {
            role.getPermissions().forEach(permission -> permissions.add(permission.getCode()));
            role.getAssignableRoles().forEach(delegated -> assignable.add(delegated.getCode()));
            platform |= role.getScope() == RoleScope.PLATFORM;
        }
        return new Actor(account, Set.copyOf(permissions), Set.copyOf(assignable), platform);
    }

    // ------------------------------------------------------------------ cuentas

    /**
     * Organizacion a la que se limita una busqueda de cuentas. Un actor de organizacion solo ve la
     * suya, aunque no la pida; uno de plataforma ve la que pida, o todas.
     */
    public UUID searchScope(Actor actor, UUID requested) {
        if (actor == null || actor.platform()) {
            return requested;
        }
        UUID own = actor.account().getOrganizationPublicId();
        if (own == null || (requested != null && !requested.equals(own))) {
            throw ApiException.forbidden("Solo puede consultar cuentas de su propia organizacion");
        }
        return own;
    }

    /** Una cuenta de otra organizacion no existe para un actor de organizacion: 404, no 403. */
    public void checkCanSee(Actor actor, UserAccount target) {
        if (actor == null || actor.platform()) {
            return;
        }
        UUID own = actor.account().getOrganizationPublicId();
        if (own == null || !own.equals(target.getOrganizationPublicId())) {
            throw ApiException.notFound("No existe la cuenta " + target.getPublicId());
        }
    }

    /**
     * Para modificar una cuenta (datos, estado o roles) hay que verla y tener todos los permisos de
     * gestion que ella tiene: un administrador de centro no desactiva al de plataforma.
     */
    public void checkCanManageAccount(Actor actor, UserAccount target) {
        checkCanSee(actor, target);
        if (actor == null) {
            return;
        }
        List<String> missing = missingGovernancePermissions(actor, effectivePermissions(target, LocalDate.now()));
        if (!missing.isEmpty()) {
            throw ApiException.forbidden("La cuenta tiene permisos de gestion de accesos que usted no tiene: "
                    + String.join(", ", missing));
        }
    }

    /** Organizacion de una cuenta nueva: la del actor si es de organizacion, aunque no la indique. */
    public UUID organizationForNewAccount(Actor actor, UUID requested) {
        if (actor == null || actor.platform()) {
            return requested;
        }
        UUID own = actor.account().getOrganizationPublicId();
        if (own == null || (requested != null && !requested.equals(own))) {
            throw ApiException.forbidden("Solo puede crear cuentas en su propia organizacion");
        }
        return own;
    }

    // ------------------------------------------------------------------ asignaciones

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
        if (!actor.platform() && !actor.assignableRoles().contains(role.getCode())) {
            throw ApiException.forbidden("Ninguno de sus roles tiene delegada la asignacion del rol " + role.getCode()
                    + "; se configura con PUT /roles/{rol}/assignable-roles");
        }
        List<String> missing = missingGovernancePermissions(actor, role.getPermissions());
        if (!missing.isEmpty()) {
            throw ApiException.forbidden("El rol " + role.getCode()
                    + " otorga permisos de gestion de accesos que usted no tiene: " + String.join(", ", missing));
        }
        UUID own = actor.account().getOrganizationPublicId();
        if (!actor.platform() && (own == null || !own.equals(assignmentOrganization))) {
            throw ApiException.forbidden("Solo puede asignar roles dentro de su propia organizacion");
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

    // ------------------------------------------------------------------ definicion de roles

    /**
     * Nadie edita un rol que tiene asignado, ni uno con permisos de gestion que no tiene: los roles
     * por encima de uno solo los toca quien esta a su altura y no los ejerce.
     */
    public void checkCanEdit(Actor actor, Role role) {
        if (actor == null) {
            return;
        }
        if (holdsRole(actor.account(), role, LocalDate.now())) {
            throw ApiException.forbidden("Segregacion de funciones: no puede modificar el rol " + role.getCode()
                    + " porque lo tiene asignado");
        }
        List<String> missing = missingGovernancePermissions(actor, role.getPermissions());
        if (!missing.isEmpty()) {
            throw ApiException.forbidden("No puede modificar el rol " + role.getCode()
                    + ": tiene permisos de gestion de accesos que usted no tiene: " + String.join(", ", missing));
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

    // ------------------------------------------------------------------ continuidad

    /** Cuentas activas que hoy pueden administrar roles; se toma antes de una operacion que quita acceso. */
    public long roleAdministrators() {
        return userRoles.countActiveHolders(ROLE_ADMINISTRATION, UserStatus.ACTIVE, LocalDate.now());
    }

    /**
     * Se llama despues de aplicar el cambio y antes de confirmarlo: si habia quien administrara roles
     * y ya no queda nadie, se rechaza y la transaccion se revierte. No exige que exista uno si de
     * antes no lo habia, para no bloquear un entorno sin PLATFORM_ADMIN sembrado.
     */
    public void checkRoleAdministrationSurvives(long before) {
        if (before == 0) {
            return;
        }
        userRoles.flush();
        if (roleAdministrators() == 0) {
            throw ApiException.conflict("La operacion dejaria a la plataforma sin ninguna cuenta activa que pueda "
                    + "administrar roles (" + ROLE_ADMINISTRATION + ")");
        }
    }

    // ------------------------------------------------------------------ auxiliares

    static List<String> missingGovernancePermissions(Actor actor, Collection<Permission> granted) {
        return granted.stream()
                .filter(permission -> GOVERNANCE_MODULES.contains(permission.getModule()))
                .map(Permission::getCode)
                .filter(code -> !actor.holds(code))
                .distinct()
                .sorted()
                .toList();
    }

    static List<Role> effectiveRoles(UserAccount account, LocalDate today) {
        return account.getRoleAssignments().stream()
                .filter(assignment -> IdentityMapper.isEffective(assignment, today))
                .map(UserRole::getRole)
                .toList();
    }

    static Collection<Permission> effectivePermissions(UserAccount account, LocalDate today) {
        Map<String, Permission> byCode = new LinkedHashMap<>();
        effectiveRoles(account, today).forEach(role -> role.getPermissions()
                .forEach(permission -> byCode.putIfAbsent(permission.getCode(), permission)));
        return byCode.values();
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
