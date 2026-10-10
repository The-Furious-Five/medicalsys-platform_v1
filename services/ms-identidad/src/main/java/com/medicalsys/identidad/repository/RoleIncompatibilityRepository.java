package com.medicalsys.identidad.repository;

import com.medicalsys.identidad.domain.Role;
import com.medicalsys.identidad.domain.RoleIncompatibility;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleIncompatibilityRepository extends JpaRepository<RoleIncompatibility, Long> {

    Optional<RoleIncompatibility> findByRoleAndIncompatibleRole(Role role, Role incompatibleRole);

    @Query("select ri from RoleIncompatibility ri where ri.role = :role or ri.incompatibleRole = :role")
    List<RoleIncompatibility> findAllInvolving(@Param("role") Role role);

    @Query("select ri from RoleIncompatibility ri order by ri.role.code, ri.incompatibleRole.code")
    List<RoleIncompatibility> findAllOrdered();
}
