package com.medicalsys.identidad.service;

import com.medicalsys.identidad.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refleja en la cuenta local el bloqueo por fuerza bruta que decide Keycloak (IAM-02/IAM-03).
 *
 * <p>Keycloak cuenta los intentos fallidos y bloquea al 3er intento. ms-identidad solo guarda el
 * estado LOCKED para mostrarlo en la gestión de usuarios. El login responde 423 lanzando una
 * excepción, lo que revierte su transacción; por eso este servicio usa REQUIRES_NEW.
 */
@Service
public class AccountAccessService {

    private final UserAccountRepository users;

    public AccountAccessService(UserAccountRepository users) {
        this.users = users;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerLockout(Long userAccountId) {
        users.findById(userAccountId).ifPresent(user -> user.markLockedByIdentityProvider());
    }
}
