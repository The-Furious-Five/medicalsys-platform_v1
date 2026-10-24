package com.medicalsys.identidad.repository;

import com.medicalsys.identidad.domain.Role;
import com.medicalsys.identidad.domain.UserRole;
import com.medicalsys.identidad.domain.UserStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    /** Cuentas con alguna asignacion del rol, vigente o no: a todas hay que cerrarles la sesion. */
    @Query("select distinct ur.userAccount.publicId from UserRole ur where ur.role = :role")
    List<UUID> findHolderPublicIds(@Param("role") Role role);

    /** Cuentas que tienen hoy, o tendran, los dos roles: violarian un par incompatible nuevo. */
    @Query("""
            select count(distinct ur.userAccount.id) from UserRole ur
            where ur.role = :first
              and (ur.validUntil is null or ur.validUntil >= :today)
              and exists (
                  select 1 from UserRole other
                  where other.userAccount = ur.userAccount
                    and other.role = :second
                    and (other.validUntil is null or other.validUntil >= :today))
            """)
    long countHoldersOfBoth(@Param("first") Role first, @Param("second") Role second,
                            @Param("today") LocalDate today);

    /**
     * Cuentas activas que hoy ejercen el permiso por algun rol activo con asignacion vigente y con un
     * nivel MAC que lo cubre. ponytail: compara contra RESTRICTED porque solo se usa con
     * identity.role.manage, que es RESTRICTED; si se usa con otro permiso, comparar por nivel.
     */
    @Query("""
            select count(distinct ur.userAccount.id) from UserRole ur join ur.role r join r.permissions p
            where p.code = :permission
              and r.active = true
              and ur.userAccount.clearance = com.medicalsys.identidad.domain.SecurityLevel.RESTRICTED
              and ur.userAccount.status = :status
              and ur.validFrom <= :today
              and (ur.validUntil is null or ur.validUntil >= :today)
            """)
    long countActiveHolders(@Param("permission") String permission, @Param("status") UserStatus status,
                            @Param("today") LocalDate today);
}
