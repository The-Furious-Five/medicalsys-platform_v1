package com.medicalsys.identidad.service;

import com.medicalsys.identidad.config.IdentityProperties;
import com.medicalsys.identidad.domain.Role;
import com.medicalsys.identidad.domain.RoleCode;
import com.medicalsys.identidad.domain.SecurityLevel;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * Crea las cuentas bootstrap con alcance de plataforma: PLATFORM_ADMIN y, desde ISO-01, el OSI. Son
 * dos personas distintas (pares incompatibles) y el OSI es la única que puede otorgar permisos, así
 * que sin él nadie podría corregir los roles después de V9.
 *
 * <p>Está deshabilitada por defecto. La contraseña llega exclusivamente por variable de entorno y
 * solo se utiliza al crear la cuenta por primera vez (o si falta en Keycloak). Se crea directo en
 * Keycloak, así que debe cumplir la política de IAM-02: si no la cumple, la cuenta no se crea y el
 * motivo queda en el log, pero el servicio arranca. Reiniciar el servicio es seguro: el seeder es
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
    private final TransactionTemplate transactions;

    public PlatformAdminSeeder(IdentityProperties properties, UserAccountRepository users,
            RoleRepository roles, UserService userService, TransactionTemplate transactions) {
        this.properties = properties;
        this.users = users;
        this.roles = roles;
        this.userService = userService;
        this.transactions = transactions;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedInOwnTransaction(properties.getPlatformAdmin(), RoleCode.PLATFORM_ADMIN, SecurityLevel.CONFIDENTIAL,
                "PLATFORM_ADMIN");
        seedInOwnTransaction(properties.getSecurityOfficer(), RoleCode.OSI, SecurityLevel.RESTRICTED, "OSI");
    }

    private void seedInOwnTransaction(IdentityProperties.PlatformAdmin config, RoleCode roleCode,
                                      SecurityLevel clearance, String envPrefix) {
        try {
            transactions.executeWithoutResult(status -> seed(config, roleCode, clearance, envPrefix));
        } catch (ApiException ex) {
            // Política de contraseñas o Keycloak caído: la cuenta no se crea, el resto del servicio sí.
            log.error("No se pudo sembrar la cuenta {} ({}): {}", roleCode, config.getEmail(), ex.getMessage());
        }
    }

    private void seed(IdentityProperties.PlatformAdmin config, RoleCode roleCode, SecurityLevel clearance,
                      String envPrefix) {
        if (!config.isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(config.getPassword()) || config.getPassword().length() < 8) {
            throw new IllegalStateException(
                    envPrefix + "_PASSWORD es obligatorio y debe tener al menos 8 caracteres");
        }
        if (config.getOrganizationPublicId() == null) {
            throw new IllegalStateException(
                    envPrefix + "_ORGANIZATION debe indicar un tenant de contexto para el gateway");
        }

        if (!users.existsByEmailIgnoreCase(config.getEmail())) {
            userService.createSeedAccount(new CreateUserRequest(
                    config.getEmail(), config.getFullName(), null, null, null, config.getPassword(), null,
                    config.getOrganizationPublicId(), "es-BO", true,
                    List.of(new RoleAssignmentRequest(roleCode.name(), null, null, null, null))),
                    config.getPublicId());
            log.info("Cuenta {} creada: {}", roleCode, config.getEmail());
        }

        UserAccount user = users.findByEmailIgnoreCase(config.getEmail()).orElseThrow();
        // IAM-07: el usuario no es secreto y quien administra el servidor lo necesita para el primer ingreso.
        log.info("Cuenta {} ({}): inicia sesion con el usuario {}", roleCode, config.getEmail(), user.getUsername());
        boolean changed = false;
        // MAC: el bootstrap nunca queda por debajo del nivel que su rol necesita.
        if (!user.getClearance().covers(clearance)) {
            user.setClearance(clearance);
            changed = true;
        }
        if (!config.getPublicId().equals(user.getPublicId())) {
            user.setPublicId(config.getPublicId());
            changed = true;
        }
        if (!config.getOrganizationPublicId().equals(user.getOrganizationPublicId())) {
            user.setOrganizationPublicId(config.getOrganizationPublicId());
            changed = true;
        }
        // IAM-03: la credencial vive en Keycloak; si falta allí, se repone con la contraseña configurada.
        if (userService.ensureSeedCredential(user, config.getPassword())) {
            log.warn("La cuenta {} no tenía credencial en Keycloak; se repuso desde la configuración", roleCode);
            changed = true;
        }
        boolean hasPlatformRole = false;
        for (UserRole assignment : user.getRoleAssignments()) {
            if (assignment.getRole().is(roleCode)) {
                hasPlatformRole = true;
                if (assignment.getOrganizationPublicId() != null) {
                    assignment.setOrganizationPublicId(null);
                    changed = true;
                }
            }
        }
        if (!hasPlatformRole) {
            Role role = roles.findByCode(roleCode.name())
                    .orElseThrow(() -> new IllegalStateException("El rol " + roleCode + " no fue sembrado"));
            user.getRoleAssignments().add(new UserRole(user, role, null, null, null, null, null));
            changed = true;
        }
        if (changed) {
            users.save(user);
            log.info("Cuenta {} reconciliada: {}", roleCode, config.getEmail());
        }
    }
}
