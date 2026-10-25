package com.medicalsys.identidad.service;

import com.medicalsys.identidad.domain.UserAccount;
import com.medicalsys.identidad.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * IAM-07: da nombre de usuario a las cuentas creadas antes de V12, a partir de su nombre completo. Corre
 * antes que los seeders y solo toca cuentas sin usuario, así que reiniciar el servicio no cambia nada.
 */
@Component
@Order(5)
public class UsernameBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UsernameBackfill.class);

    private final UserAccountRepository users;
    private final UsernameGenerator usernames;
    private final TransactionTemplate transactions;

    public UsernameBackfill(UserAccountRepository users, UsernameGenerator usernames, TransactionTemplate transactions) {
        this.users = users;
        this.usernames = usernames;
        this.transactions = transactions;
    }

    @Override
    public void run(ApplicationArguments args) {
        int assigned = 0;
        for (UserAccount user : users.findByUsernameIsNull()) {
            try {
                // Una transacción por cuenta: la siguiente ve el usuario recién asignado y no lo repite.
                String username = usernames.next(UsernameGenerator.parse(user.getFullName()));
                Integer updated = transactions.execute(status -> users.assignUsername(user.getId(), username));
                if (updated != null && updated > 0) {
                    assigned++;
                    // El usuario no es secreto: así quien administra sabe con qué nombre entra cada cuenta.
                    log.info("IAM-07: la cuenta {} ({}) inicia sesion como {}", user.getUserCode(), user.getEmail(), username);
                }
            } catch (RuntimeException ex) {
                log.warn("La cuenta {} no recibio usuario: {}", user.getUserCode(), ex.getMessage());
            }
        }
        if (assigned > 0) {
            log.info("IAM-07: {} cuentas existentes recibieron su nombre de usuario", assigned);
        }
    }
}
