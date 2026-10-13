package com.medicalsys.identidad;

import static org.assertj.core.api.Assertions.assertThat;

import com.medicalsys.identidad.domain.UserAccount;
import com.medicalsys.identidad.domain.UserStatus;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Estado de bloqueo de la cuenta local. Desde IAM-03 los intentos los cuenta Keycloak (bloqueo al
 * 3er intento, ver infra/keycloak); la cuenta local solo refleja el resultado. No requieren contexto
 * de Spring ni base de datos.
 */
class IdentidadApplicationTests {

    @Test
    @DisplayName("el bloqueo de Keycloak se refleja como LOCKED, sin fecha de fin: es permanente")
    void mirrorsIdentityProviderLockout() {
        UserAccount user = new UserAccount();
        user.setStatus(UserStatus.ACTIVE);

        user.markLockedByIdentityProvider();

        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("un acceso correcto reactiva una cuenta bloqueada y registra el acceso")
    void successfulAccessClearsLock() {
        UserAccount user = new UserAccount();
        user.setStatus(UserStatus.ACTIVE);
        user.markLockedByIdentityProvider();

        Instant now = Instant.now();
        user.registerSuccessfulAccess(now);

        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getLastAccessAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("una cuenta sin keycloak_user_id todavía no está enlazada")
    void linkState() {
        UserAccount user = new UserAccount();
        assertThat(user.isLinkedToIdentityProvider()).isFalse();

        user.setKeycloakUserId("3b043518-c564-4fbb-b35a-bbd9c7e9abb1");

        assertThat(user.isLinkedToIdentityProvider()).isTrue();
    }

    @Test
    @DisplayName("un bloqueo vencido ya no impide el acceso")
    void expiredLockIsNotBlocking() {
        UserAccount user = new UserAccount();
        user.setLockedUntil(Instant.now().minusSeconds(60));

        assertThat(user.isCurrentlyLocked(Instant.now())).isFalse();
    }
}
