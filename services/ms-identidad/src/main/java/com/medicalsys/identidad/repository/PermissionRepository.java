package com.medicalsys.identidad.repository;

import com.medicalsys.identidad.domain.Permission;
import com.medicalsys.identidad.domain.PermissionModule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Short> {

    List<Permission> findAllByModuleOrderByCodeAsc(PermissionModule module);

    List<Permission> findAllByOrderByCodeAsc();
}
