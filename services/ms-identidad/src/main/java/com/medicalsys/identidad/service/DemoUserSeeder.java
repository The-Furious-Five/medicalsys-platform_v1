package com.medicalsys.identidad.service;

import com.medicalsys.identidad.config.IdentityProperties;
import com.medicalsys.identidad.domain.RoleCode;
import com.medicalsys.identidad.repository.UserAccountRepository;
import com.medicalsys.identidad.web.dto.CreateUserRequest;
import com.medicalsys.identidad.web.dto.RoleAssignmentRequest;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Crea las cuentas de demostracion del Sprint 2 con contrasena hasheada.
 * Se puede desactivar con IDENTITY_DEMO_USERS_ENABLED=false.
 */
@Component
public class DemoUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    private final IdentityProperties properties;
    private final UserAccountRepository users;
    private final UserService userService;

    public DemoUserSeeder(IdentityProperties properties, UserAccountRepository users, UserService userService) {
        this.properties = properties;
        this.users = users;
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.getDemoUsers().isEnabled()) {
            return;
        }
        UUID organization = properties.getDemoUsers().getOrganizationPublicId();
        String password = properties.getDemoUsers().getPassword();

        seed("admin@medicalsys.demo", "Administrador Demo", RoleCode.ADMINISTRATOR, organization, password);
        seed("recepcion@medicalsys.demo", "Recepcion Demo", RoleCode.RECEPTIONIST, organization, password);
        seed("doctor@medicalsys.demo", "Profesional Demo", RoleCode.PHYSICIAN, organization, password);
    }

    private void seed(String email, String fullName, RoleCode role, UUID organization, String password) {
        if (users.existsByEmailIgnoreCase(email)) {
            return;
        }
        userService.create(new CreateUserRequest(
                email, fullName, password, null, organization, "es-BO", true,
                List.of(new RoleAssignmentRequest(role, organization, null, null, null))), null);
        log.info("Cuenta de demostracion creada: {} con rol {}", email, role);
    }
}
