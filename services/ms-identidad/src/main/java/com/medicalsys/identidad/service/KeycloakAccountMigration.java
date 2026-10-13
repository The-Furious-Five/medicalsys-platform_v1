package com.medicalsys.identidad.service;

import com.medicalsys.identidad.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * IAM-03: al arrancar, migra a Keycloak las cuentas que todavía no están enlazadas (incluidas las de
 * demostración que acaban de sembrar DemoUserSeeder y PlatformAdminSeeder, por eso corre después).
 *
 * <p>Si Keycloak no responde, el servicio arranca igual: cada cuenta se enlaza en su primer login.
 */
@Component
@Order(100)
public class KeycloakAccountMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAccountMigration.class);

    private final UserAccountRepository users;
    private final KeycloakAccountLinker linker;

    public KeycloakAccountMigration(UserAccountRepository users, KeycloakAccountLinker linker) {
        this.users = users;
        this.linker = linker;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            long unlinked = users.findLinkedIds().stream().filter(linker::unlinkIfMissing).count();
            int linked = 0;
            int pending = 0;
            for (Long id : users.findUnlinkedIds()) {
                if (linker.link(id).isPresent()) {
                    linked++;
                } else {
                    pending++;
                }
            }
            log.info("Migración a Keycloak: {} cuentas enlazadas, {} desenlazadas por faltar en Keycloak, "
                    + "{} sin credencial", linked, unlinked, pending);
        } catch (ApiException ex) {
            log.warn("Keycloak no está disponible al arrancar; las cuentas se enlazarán en su primer login");
        }
    }
}
