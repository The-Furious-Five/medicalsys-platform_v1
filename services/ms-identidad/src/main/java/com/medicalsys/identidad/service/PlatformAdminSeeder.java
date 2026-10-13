package com.medicalsys.identidad.service;

import com.medicalsys.identidad.config.IdentityProperties;
import com.medicalsys.identidad.domain.Role;
import com.medicalsys.identidad.domain.RoleCode;
import com.medicalsys.identidad.domain.UserAccount;
import com.medicalsys.identidad.domain.UserRole;
import com.medicalsys.identidad.repository.RoleRepository;
import com.medicalsys.identidad.repository.UserAccountRepository;
import com.medicalsys.identidad.web.dto.CreateUserRequest;
import com.medicalsys.identidad.web.dto.RoleAssignmentRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Crea una única cuenta bootstrap con alcance de plataforma.
 *
 * <p>Está deshabilitada por defecto. La contraseña llega exclusivamente por variable de entorno y
 * solo se utiliza al crear la cuenta por primera vez. Reiniciar el servicio es seguro: el seeder es
 * idempotente y no duplica ni la cuenta ni su rol.
 */
@Component
@Order(20) // antes de KeycloakAccountMigration (100), que enlaza lo que este seeder crea
public class PlatformAdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminSeeder.class);

    private final IdentityProperties properties;
    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final UserService userService;
    private final KeycloakAccountLinker linker;

    public PlatformAdminSeeder(IdentityProperties properties, UserAccountRepository users,
            RoleRepository roles, UserService userService, KeycloakAccountLinker linker) {
        this.properties = properties;
        this.users = users;
        this.roles = roles;
        this.userService = userService;
        this.linker = linker;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        IdentityProperties.PlatformAdmin config = properties.getPlatformAdmin();
        if (!config.isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(config.getPassword()) || config.getPassword().length() < 8) {
            throw new IllegalStateException(
                    "PLATFORM_ADMIN_PASSWORD es obligatorio y debe tener al menos 8 caracteres");
        }
        if (config.getOrganizationPublicId() == null) {
            throw new IllegalStateException(
                    "PLATFORM_ADMIN_ORGANIZATION debe indicar un tenant de contexto para el gateway");
        }

        if (!users.existsByEmailIgnoreCase(config.getEmail())) {
            userService.createSeedAccount(new CreateUserRequest(
                    config.getEmail(), config.getFullName(), config.getPassword(), null,
                    config.getOrganizationPublicId(), "es-BO", true,
                    List.of(new RoleAssignmentRequest(RoleCode.PLATFORM_ADMIN.name(), null, null, null, null))));
            log.info("Cuenta PLATFORM_ADMIN creada: {}", config.getEmail());
        }

        UserAccount user = users.findByEmailIgnoreCase(config.getEmail()).orElseThrow();
        // IAM-03: la credencial vive en Keycloak; si falta allí, se repone con PLATFORM_ADMIN_PASSWORD.
        linker.restoreConfiguredCredential(user, config.getPassword());
        boolean changed = false;
        if (!config.getPublicId().equals(user.getPublicId())) {
            user.setPublicId(config.getPublicId());
            changed = true;
        }
        if (!config.getOrganizationPublicId().equals(user.getOrganizationPublicId())) {
            user.setOrganizationPublicId(config.getOrganizationPublicId());
            changed = true;
        }
        boolean hasPlatformRole = false;
        for (UserRole assignment : user.getRoleAssignments()) {
            if (assignment.getRole().is(RoleCode.PLATFORM_ADMIN)) {
                hasPlatformRole = true;
                if (assignment.getOrganizationPublicId() != null) {
                    assignment.setOrganizationPublicId(null);
                    changed = true;
                }
            }
        }
        if (!hasPlatformRole) {
            Role role = roles.findByCode(RoleCode.PLATFORM_ADMIN.name())
                    .orElseThrow(() -> new IllegalStateException("El rol PLATFORM_ADMIN no fue sembrado"));
            user.getRoleAssignments().add(new UserRole(user, role, null, null, null, null, null));
            changed = true;
        }
        if (changed) {
            users.save(user);
            log.info("Cuenta PLATFORM_ADMIN reconciliada: {}", config.getEmail());
        }
    }
}
