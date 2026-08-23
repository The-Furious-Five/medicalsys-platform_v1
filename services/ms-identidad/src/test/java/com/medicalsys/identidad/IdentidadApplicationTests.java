package com.medicalsys.identidad;

import static org.assertj.core.api.Assertions.assertThat;

import com.medicalsys.identidad.domain.UserAccount;
import com.medicalsys.identidad.domain.UserStatus;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de la politica de bloqueo de cuenta. No requieren contexto de Spring ni base de datos:
 * la verificacion de arranque completo se hace contra el contenedor real (ver README del servicio).
 */
class IdentidadApplicationTests {

    @Test
    @DisplayName("la cuenta se bloquea al alcanzar el maximo de intentos fallidos")
    void locksAfterMaxFailedAttempts() {
        UserAccount user = new UserAccount();
        user.setStatus(UserStatus.ACTIVE);
        Instant lockUntil = Instant.now().plusSeconds(900);

        for (int attempt = 0; attempt < 4; attempt++) {
            user.registerFailedAttempt(5, lockUntil);
        }
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getLockedUntil()).isNull();

        user.registerFailedAttempt(5, lockUntil);

        assertThat(user.getFailedAttempts()).isEqualTo((byte) 5);
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(user.isCurrentlyLocked(Instant.now())).isTrue();
    }

    @Test
    @DisplayName("un acceso correcto limpia el contador y reactiva una cuenta bloqueada")
    void successfulAccessClearsLock() {
        UserAccount user = new UserAccount();
        user.setStatus(UserStatus.ACTIVE);
        Instant lockUntil = Instant.now().plusSeconds(900);
        for (int attempt = 0; attempt < 5; attempt++) {
            user.registerFailedAttempt(5, lockUntil);
        }

        Instant now = Instant.now();
        user.registerSuccessfulAccess(now);

        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getLastAccessAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("un bloqueo vencido ya no impide el acceso")
    void expiredLockIsNotBlocking() {
        UserAccount user = new UserAccount();
        user.setLockedUntil(Instant.now().minusSeconds(60));

        assertThat(user.isCurrentlyLocked(Instant.now())).isFalse();
    }
}
