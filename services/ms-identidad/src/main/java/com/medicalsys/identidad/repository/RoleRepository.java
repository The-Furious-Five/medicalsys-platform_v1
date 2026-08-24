package com.medicalsys.identidad.repository;

import com.medicalsys.identidad.domain.Role;
import com.medicalsys.identidad.domain.RoleCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface RoleRepository extends JpaRepository<Role, Short> {

    @EntityGraph(attributePaths = "permissions")
    Optional<Role> findByCode(RoleCode code);
}
