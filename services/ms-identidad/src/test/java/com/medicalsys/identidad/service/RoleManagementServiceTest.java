package com.medicalsys.identidad.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalsys.identidad.audit.AuditEventDraft;
import com.medicalsys.identidad.audit.AuditTrail;
import com.medicalsys.identidad.config.IdentityProperties;
import com.medicalsys.identidad.domain.Permission;
import com.medicalsys.identidad.domain.PermissionModule;
import com.medicalsys.identidad.domain.Role;
import com.medicalsys.identidad.domain.RoleScope;
import com.medicalsys.identidad.domain.UserAccount;
import com.medicalsys.identidad.repository.PermissionRepository;
import com.medicalsys.identidad.repository.RoleIncompatibilityRepository;
import com.medicalsys.identidad.repository.RoleRepository;
import com.medicalsys.identidad.repository.UserRoleRepository;
import com.medicalsys.identidad.security.SessionStore;
import com.medicalsys.identidad.service.RoleGovernance.Actor;
import com.medicalsys.identidad.web.dto.CreateRoleRequest;
import com.medicalsys.identidad.web.dto.RolePermissionsRequest;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * RBAC-02: alta, permisos y baja de roles desde la API, sin tocar código ni la base. LOG-02: cada
 * cambio queda en la bitácora con quién lo hizo.
 */
class RoleManagementServiceTest {

    private static final UUID PLATFORM_ADMIN = UUID.fromString("00000000-0000-4000-8000-000000000900");
    private static final UUID PLATFORM_ORG = UUID.fromString("00000000-0000-4000-8000-0000000000a1");

    private final RoleRepository roles = mock(RoleRepository.class);
    private final PermissionRepository permissions = mock(PermissionRepository.class);
    private final UserRoleRepository userRoles = mock(UserRoleRepository.class);
    private final RoleGovernance governance = mock(RoleGovernance.class);
    private final SessionStore sessionStore = mock(SessionStore.class);
    private final AuditTrail auditTrail = mock(AuditTrail.class);

    private final RoleManagementService service = new RoleManagementService(roles, permissions, userRoles,
            mock(RoleIncompatibilityRepository.class), governance, sessionStore, new IdentityMapper(), auditTrail,
            new IdentityProperties());

    private final Permission auditRead = new Permission("audit.log.read", PermissionModule.AUDIT, "Leer la bitácora", false);
    private final Permission auditVerify = new Permission("audit.log.verify", PermissionModule.AUDIT, "Verificar la cadena", false);

    @BeforeEach
    void setUp() {
        UserAccount account = new UserAccount();
        account.setPublicId(PLATFORM_ADMIN);
        account.setOrganizationPublicId(PLATFORM_ORG);
        when(governance.resolve(PLATFORM_ADMIN)).thenReturn(new Actor(account, Set.of("identity.role.manage"), Set.of(), true));
        when(roles.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("alta: el rol nace activo y sin permisos, y queda ROLE_CREATED con su autor")
    void createsARoleAndAuditsIt() {
        var response = service.create(new CreateRoleRequest("AUDITOR", "Auditor", "Revisa la bitácora", RoleScope.ORGANIZATION),
                PLATFORM_ADMIN);

        assertThat(response.code()).isEqualTo("AUDITOR");
        assertThat(response.active()).isTrue();
        assertThat(response.permissions()).isEmpty();
        AuditEventDraft draft = captureDraft();
        assertThat(draft.action()).isEqualTo("ROLE_CREATED");
        assertThat(draft.resourceType()).isEqualTo("ROLE");
        assertThat(draft.resourceLabel()).isEqualTo("AUDITOR");
        assertThat(draft.actorUserId()).isEqualTo(PLATFORM_ADMIN);
        assertThat(draft.organizationId()).isEqualTo(PLATFORM_ORG);
    }

    @Test
    @DisplayName("alta con un código que ya existe: 409 y nada en la bitácora")
    void rejectsADuplicatedCode() {
        when(roles.existsByCode("AUDITOR")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateRoleRequest("AUDITOR", "Auditor", null, null), PLATFORM_ADMIN))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(auditTrail, never()).record(any());
    }

    @Test
    @DisplayName("permisos: reemplaza el conjunto, cierra las sesiones de quienes pierden acceso y lo audita")
    void replacesPermissionsAndClosesSessionsWhenAccessIsRemoved() {
        Role auditor = Role.custom("AUDITOR", "Auditor", null, RoleScope.ORGANIZATION);
        auditor.getPermissions().add(auditVerify);
        UUID holder = UUID.randomUUID();
        when(roles.findByCode("AUDITOR")).thenReturn(Optional.of(auditor));
        when(permissions.findAllByCodeIn(anyCollection())).thenReturn(List.of(auditRead));
        when(userRoles.findHolderPublicIds(auditor)).thenReturn(List.of(holder));

        var response = service.replacePermissions("AUDITOR", new RolePermissionsRequest(List.of("audit.log.read")), PLATFORM_ADMIN);

        assertThat(response.permissions()).extracting("code").containsExactly("audit.log.read");
        verify(sessionStore).closeAll(holder);
        AuditEventDraft draft = captureDraft();
        assertThat(draft.action()).isEqualTo("ROLE_PERMISSIONS_CHANGED");
        assertThat(draft.severity()).isEqualTo("WARNING");
        assertThat(draft.metadata()).containsEntry("added", "audit.log.read").containsEntry("removed", "audit.log.verify");
    }

    @Test
    @DisplayName("permisos: un código que no está en el catálogo es 400 y el rol no cambia")
    void rejectsUnknownPermissions() {
        Role auditor = Role.custom("AUDITOR", "Auditor", null, RoleScope.ORGANIZATION);
        when(roles.findByCode("AUDITOR")).thenReturn(Optional.of(auditor));
        when(permissions.findAllByCodeIn(anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> service.replacePermissions("AUDITOR",
                new RolePermissionsRequest(List.of("audit.log.borrar")), PLATFORM_ADMIN))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(auditor.getPermissions()).isEmpty();
        verify(auditTrail, never()).record(any());
    }

    @Test
    @DisplayName("baja: un rol creado desde la API se desactiva (no se borra) y queda ROLE_DISABLED")
    void deactivatesACustomRole() {
        Role auditor = Role.custom("AUDITOR", "Auditor", null, RoleScope.ORGANIZATION);
        when(roles.findByCode("AUDITOR")).thenReturn(Optional.of(auditor));

        var response = service.deactivate("AUDITOR", PLATFORM_ADMIN);

        assertThat(response.active()).isFalse();
        assertThat(captureDraft().action()).isEqualTo("ROLE_DISABLED");
    }

    @Test
    @DisplayName("baja: un rol de sistema no se puede desactivar")
    void refusesToDeactivateASystemRole() {
        Role administrator = Role.custom("ADMINISTRATOR", "Administrador", null, RoleScope.ORGANIZATION);
        ReflectionTestUtils.setField(administrator, "system", true);
        when(roles.findByCode("ADMINISTRATOR")).thenReturn(Optional.of(administrator));

        assertThatThrownBy(() -> service.deactivate("ADMINISTRATOR", PLATFORM_ADMIN))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(administrator.isActive()).isTrue();
        verify(auditTrail, never()).record(any());
    }

    @Test
    @DisplayName("segregación: si la gobernanza rechaza al actor (p. ej. su propio rol), no cambia nada")
    void governanceRejectionChangesNothing() {
        Role auditor = Role.custom("AUDITOR", "Auditor", null, RoleScope.ORGANIZATION);
        when(roles.findByCode("AUDITOR")).thenReturn(Optional.of(auditor));
        doThrow(ApiException.forbidden("No puede modificar un rol que usted mismo tiene"))
                .when(governance).checkCanEdit(any(), any());

        assertThatThrownBy(() -> service.replacePermissions("AUDITOR",
                new RolePermissionsRequest(List.of("audit.log.read")), PLATFORM_ADMIN))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(auditor.getPermissions()).isEmpty();
        verify(auditTrail, never()).record(any());
    }

    private AuditEventDraft captureDraft() {
        ArgumentCaptor<AuditEventDraft> captor = ArgumentCaptor.forClass(AuditEventDraft.class);
        verify(auditTrail).record(captor.capture());
        return captor.getValue();
    }
}
