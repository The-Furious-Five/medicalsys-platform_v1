package com.medicalsys.identidad.repository;

import com.medicalsys.identidad.domain.UserAccount;
import com.medicalsys.identidad.domain.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    Optional<UserAccount> findByPublicId(UUID publicId);

    boolean existsByEmailIgnoreCase(String email);

    Optional<UserAccount> findByUsername(String username);

    boolean existsByUsername(String username);

    /** Cuentas creadas antes de IAM-07, que todavía no tienen nombre de usuario. */
    List<UserAccount> findByUsernameIsNull();

    /**
     * Único camino para fijar el usuario de una cuenta ya creada: la columna es updatable = false. Solo
     * escribe si sigue vacío, así que un usuario asignado no se puede pisar.
     */
    @Modifying
    @Query("update UserAccount u set u.username = :username where u.id = :id and u.username is null")
    int assignUsername(@Param("id") Long id, @Param("username") String username);

    /** Cuentas aun no enlazadas a Keycloak (IAM-03). */
    @Query("select u.id from UserAccount u where u.keycloakUserId is null")
    List<Long> findUnlinkedIds();

    @Query("select u.id from UserAccount u where u.keycloakUserId is not null")
    List<Long> findLinkedIds();

    @Query("""
            select u from UserAccount u
            where (:organizationPublicId is null or u.organizationPublicId = :organizationPublicId)
              and (:status is null or u.status = :status)
              and (:search is null or lower(u.email) like :search or lower(u.fullName) like :search
                   or u.username like :search
                   or lower(u.userCode) like :search)
            """)
    Page<UserAccount> search(@Param("organizationPublicId") UUID organizationPublicId,
                             @Param("status") UserStatus status,
                             @Param("search") String search,
                             Pageable pageable);
}
