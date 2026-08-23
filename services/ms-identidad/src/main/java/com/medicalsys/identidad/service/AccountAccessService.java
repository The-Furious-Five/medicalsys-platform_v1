package com.medicalsys.identidad.service;

import com.medicalsys.identidad.config.IdentityProperties;
import com.medicalsys.identidad.domain.UserAccount;
import com.medicalsys.identidad.repository.UserAccountRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra los intentos fallidos de acceso en una transaccion propia.
 *
 * <p>El login responde 401 lanzando una excepcion, lo que revierte su transaccion. Si el contador
 * se incrementara ahi, el bloqueo por intentos fallidos nunca se guardaria. Por eso este servicio
 * usa REQUIRES_NEW: el contador se confirma aunque el login termine en error.
 */
@Service
public class AccountAccessService {

    private final UserAccountRepository users;
    private final IdentityProperties properties;

    public AccountAccessService(UserAccountRepository users, IdentityProperties properties) {
        this.users = users;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int registerFailedAttempt(Long userAccountId) {
        UserAccount user = users.findById(userAccountId).orElse(null);
        if (user == null) {
            return 0;
        }
        Instant lockUntil = Instant.now().plus(properties.getLockout().getLockDuration());
        user.registerFailedAttempt(properties.getLockout().getMaxFailedAttempts(), lockUntil);
        return user.getFailedAttempts() == null ? 0 : user.getFailedAttempts();
    }
}
