package com.medicalsys.identidad.repository;

import com.medicalsys.identidad.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
}
