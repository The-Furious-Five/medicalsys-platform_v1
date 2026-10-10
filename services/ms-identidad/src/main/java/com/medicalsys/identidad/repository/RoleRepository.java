package com.medicalsys.identidad.repository;

import com.medicalsys.identidad.domain.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface RoleRepository extends JpaRepository<Role, Short> {

    @EntityGraph(attributePaths = "permissions")
    Optional<Role> findByCode(String code);

    boolean existsByCode(String code);
}
