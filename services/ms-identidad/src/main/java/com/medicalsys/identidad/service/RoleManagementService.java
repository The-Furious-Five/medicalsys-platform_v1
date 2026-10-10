package com.medicalsys.identidad.service;

import com.medicalsys.identidad.domain.Permission;
import com.medicalsys.identidad.domain.Role;
import com.medicalsys.identidad.domain.RoleIncompatibility;
import com.medicalsys.identidad.domain.RoleScope;
import com.medicalsys.identidad.repository.PermissionRepository;
import com.medicalsys.identidad.repository.RoleIncompatibilityRepository;
import com.medicalsys.identidad.repository.RoleRepository;
import com.medicalsys.identidad.repository.UserRoleRepository;
import com.medicalsys.identidad.security.SessionStore;
import com.medicalsys.identidad.service.RoleGovernance.Actor;
import com.medicalsys.identidad.web.dto.CreateRoleRequest;
import com.medicalsys.identidad.web.dto.RoleAssignableRolesRequest;
import com.medicalsys.identidad.web.dto.RoleIncompatibilityRequest;
import com.medicalsys.identidad.web.dto.RoleIncompatibilityResponse;
import com.medicalsys.identidad.web.dto.RolePermissionsRequest;
import com.medicalsys.identidad.web.dto.RoleResponse;
import com.medicalsys.identidad.web.dto.UpdateRoleRequest;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Alta, edicion, baja logica, permisos y delegacion de los roles, y pares de roles incompatibles
 * (RBAC-02). Toda operacion que quita acceso cierra las sesiones de las cuentas afectadas, igual
 * que revocar un rol: el cambio no espera a que el token expire. Y ninguna puede dejar a la
 * plataforma sin una cuenta activa que administre roles.
 */
@Service
public class RoleManagementService {

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final UserRoleRepository userRoles;
    private final RoleIncompatibilityRepository incompatibilities;
    private final RoleGovernance governance;
    private final SessionStore sessionStore;
    private final IdentityMapper mapper;

    public RoleManagementService(RoleRepository roles, PermissionRepository permissions, UserRoleRepository userRoles,
                                 RoleIncompatibilityRepository incompatibilities, RoleGovernance governance,
                                 SessionStore sessionStore, IdentityMapper mapper) {
        this.roles = roles;
        this.permissions = permissions;
        this.userRoles = userRoles;
        this.incompatibilities = incompatibilities;
        this.governance = governance;
        this.sessionStore = sessionStore;
        this.mapper = mapper;
    }

    @Transactional
    public RoleResponse create(CreateRoleRequest request, UUID actorPublicId) {
        governance.resolve(actorPublicId);
        if (roles.existsByCode(request.code())) {
            throw ApiException.conflict("Ya existe un rol con el codigo " + request.code());
        }
        Role role = Role.custom(request.code(), request.name().trim(), blankToNull(request.description()),
                request.scope() == null ? RoleScope.ORGANIZATION : request.scope());
        return mapper.toRoleResponse(roles.save(role));
    }

    @Transactional
    public RoleResponse update(String code, UpdateRoleRequest request, UUID actorPublicId) {
        Actor actor = governance.resolve(actorPublicId);
        Role role = require(code);
        governance.checkCanEdit(actor, role);
        if (Boolean.FALSE.equals(request.active())) {
            deactivate(role);
        }
        if (request.name() != null) {
            role.setName(request.name().trim());
        }
        if (request.description() != null) {
            role.setDescription(blankToNull(request.description()));
        }
        if (Boolean.TRUE.equals(request.active())) {
            role.setActive(true);
        }
        return mapper.toRoleResponse(role);
    }

    /** Baja logica: el rol deja de asignarse y de dar acceso, pero su historia se conserva. */
    @Transactional
    public RoleResponse deactivate(String code, UUID actorPublicId) {
        Actor actor = governance.resolve(actorPublicId);
        Role role = require(code);
        governance.checkCanEdit(actor, role);
        deactivate(role);
        return mapper.toRoleResponse(role);
    }

    @Transactional
    public RoleResponse replacePermissions(String code, RolePermissionsRequest request, UUID actorPublicId) {
        Actor actor = governance.resolve(actorPublicId);
        Role role = require(code);
        governance.checkCanEdit(actor, role);

        Set<String> requested = request.permissions().stream()
                .map(String::trim)
                .collect(Collectors.toCollection(TreeSet::new));
        List<Permission> found = permissions.findAllByCodeIn(requested);
        Set<String> foundCodes = found.stream().map(Permission::getCode).collect(Collectors.toSet());
        List<String> unknown = requested.stream().filter(requestedCode -> !foundCodes.contains(requestedCode)).toList();
        if (!unknown.isEmpty()) {
            throw ApiException.badRequest("Permisos inexistentes en el catalogo: " + String.join(", ", unknown));
        }

        Set<String> current = role.getPermissions().stream().map(Permission::getCode).collect(Collectors.toSet());
        List<Permission> added = found.stream().filter(permission -> !current.contains(permission.getCode())).toList();
        boolean removesAccess = current.stream().anyMatch(currentCode -> !foundCodes.contains(currentCode));
        governance.checkCanGrant(actor, added);

        long administrators = removesAccess ? governance.roleAdministrators() : 0;
        role.getPermissions().clear();
        found.stream().sorted(Comparator.comparing(Permission::getCode)).forEach(role.getPermissions()::add);
        if (removesAccess) {
            governance.checkRoleAdministrationSurvives(administrators);
            closeSessionsOfHolders(role);
        }
        return mapper.toRoleResponse(role);
    }

    /**
     * Roles que quien tiene {@code code} puede asignar o revocar. Solo pesa para roles de
     * organizacion: los de plataforma gestionan cualquier rol. Cambiarla no altera ningun token, asi
     * que no cierra sesiones.
     */
    @Transactional
    public RoleResponse replaceAssignableRoles(String code, RoleAssignableRolesRequest request, UUID actorPublicId) {
        Actor actor = governance.resolve(actorPublicId);
        Role role = require(code);
        governance.checkCanEdit(actor, role);

        Set<String> requested = request.roles().stream()
                .map(RoleManagementService::normalize)
                .collect(Collectors.toCollection(TreeSet::new));
        List<Role> found = roles.findAllByCodeIn(requested);
        Set<String> foundCodes = found.stream().map(Role::getCode).collect(Collectors.toSet());
        List<String> unknown = requested.stream().filter(requestedCode -> !foundCodes.contains(requestedCode)).toList();
        if (!unknown.isEmpty()) {
            throw ApiException.badRequest("Roles inexistentes: " + String.join(", ", unknown));
        }
        if (role.getScope() == RoleScope.ORGANIZATION) {
            List<String> platformRoles = found.stream()
                    .filter(delegated -> delegated.getScope() == RoleScope.PLATFORM)
                    .map(Role::getCode)
                    .sorted()
                    .toList();
            if (!platformRoles.isEmpty()) {
                throw ApiException.badRequest("Un rol de organizacion no puede delegar roles de plataforma: "
                        + String.join(", ", platformRoles));
            }
        }

        role.getAssignableRoles().clear();
        found.stream().sorted(Comparator.comparing(Role::getCode)).forEach(role.getAssignableRoles()::add);
        return mapper.toRoleResponse(role);
    }

    @Transactional(readOnly = true)
    public List<RoleIncompatibilityResponse> listIncompatibilities() {
        return incompatibilities.findAllOrdered().stream().map(mapper::toIncompatibilityResponse).toList();
    }

    @Transactional
    public RoleIncompatibilityResponse declareIncompatible(RoleIncompatibilityRequest request, UUID actorPublicId) {
        Actor actor = governance.resolve(actorPublicId);
        Role[] pair = orderedPair(request.roleA(), request.roleB());
        governance.checkCanEdit(actor, pair[0]);
        governance.checkCanEdit(actor, pair[1]);
        if (incompatibilities.findByRoleAndIncompatibleRole(pair[0], pair[1]).isPresent()) {
            throw ApiException.conflict("Los roles " + pair[0].getCode() + " y " + pair[1].getCode()
                    + " ya estan declarados incompatibles");
        }
        long holders = userRoles.countHoldersOfBoth(pair[0], pair[1], LocalDate.now());
        if (holders > 0) {
            throw ApiException.conflict(holders + " cuenta(s) ya tienen " + pair[0].getCode() + " y "
                    + pair[1].getCode() + " a la vez: revoque uno de los dos antes de declararlos incompatibles");
        }
        RoleIncompatibility created = incompatibilities.save(new RoleIncompatibility(pair[0], pair[1],
                request.reason().trim(), actor == null ? null : actor.account()));
        return mapper.toIncompatibilityResponse(created);
    }

    @Transactional
    public void removeIncompatibility(String roleA, String roleB, UUID actorPublicId) {
        Actor actor = governance.resolve(actorPublicId);
        Role[] pair = orderedPair(roleA, roleB);
        governance.checkCanEdit(actor, pair[0]);
        governance.checkCanEdit(actor, pair[1]);
        RoleIncompatibility existing = incompatibilities.findByRoleAndIncompatibleRole(pair[0], pair[1])
                .orElseThrow(() -> ApiException.notFound("Los roles " + pair[0].getCode() + " y "
                        + pair[1].getCode() + " no estan declarados incompatibles"));
        incompatibilities.delete(existing);
    }

    private void deactivate(Role role) {
        if (role.isSystem()) {
            throw ApiException.conflict("El rol " + role.getCode() + " es de sistema y no se puede desactivar; "
                    + "si sobra algun permiso, quiteselo con PUT /roles/" + role.getCode() + "/permissions");
        }
        if (role.isActive()) {
            long administrators = governance.roleAdministrators();
            role.setActive(false);
            governance.checkRoleAdministrationSurvives(administrators);
            closeSessionsOfHolders(role);
        }
    }

    private void closeSessionsOfHolders(Role role) {
        userRoles.findHolderPublicIds(role).forEach(sessionStore::closeAll);
    }

    private Role[] orderedPair(String roleA, String roleB) {
        Role first = require(roleA);
        Role second = require(roleB);
        if (first.getCode().equals(second.getCode())) {
            throw ApiException.badRequest("Un rol no puede ser incompatible consigo mismo");
        }
        return RoleIncompatibility.ordered(first, second);
    }

    private Role require(String code) {
        String normalized = normalize(code);
        return roles.findByCode(normalized)
                .orElseThrow(() -> ApiException.notFound("No existe el rol " + normalized));
    }

    static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
