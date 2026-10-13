package com.medicalsys.identidad.service;

import com.medicalsys.identidad.domain.Permission;
import com.medicalsys.identidad.domain.Role;
import com.medicalsys.identidad.domain.RoleIncompatibility;
import com.medicalsys.identidad.domain.UserAccount;
import com.medicalsys.identidad.domain.UserRole;
import com.medicalsys.identidad.web.dto.InternalUserResponse;
import com.medicalsys.identidad.web.dto.PermissionResponse;
import com.medicalsys.identidad.web.dto.RoleAssignmentResponse;
import com.medicalsys.identidad.web.dto.RoleIncompatibilityResponse;
import com.medicalsys.identidad.web.dto.RoleResponse;
import com.medicalsys.identidad.web.dto.UserResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class IdentityMapper {

    public UserResponse toUserResponse(UserAccount user) {
        return new UserResponse(
                user.getPublicId(),
                user.getOrganizationPublicId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhoneE164(),
                user.getLanguage(),
                user.getStatus(),
                user.isMfaEnabled(),
                user.getFailedAttempts() == null ? 0 : user.getFailedAttempts(),
                user.getLockedUntil(),
                user.getLastAccessAt(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getRoleAssignments().stream().map(this::toRoleAssignment).toList());
    }

    public InternalUserResponse toInternalResponse(UserAccount user) {
        return new InternalUserResponse(
                user.getPublicId(),
                user.getOrganizationPublicId(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus(),
                activeRoleCodes(user));
    }

    public RoleAssignmentResponse toRoleAssignment(UserRole assignment) {
        return new RoleAssignmentResponse(
                assignment.getId(),
                assignment.getRole().getCode(),
                assignment.getOrganizationPublicId(),
                assignment.getBranchPublicId(),
                assignment.getValidFrom(),
                assignment.getValidUntil(),
                isEffective(assignment, LocalDate.now()));
    }

    public RoleResponse toRoleResponse(Role role) {
        return new RoleResponse(
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.getScope(),
                role.isSystem(),
                role.isActive(),
                role.getPermissions().stream().map(this::toPermissionResponse).toList(),
                role.getAssignableRoles().stream().map(Role::getCode).sorted().toList());
    }

    public PermissionResponse toPermissionResponse(Permission permission) {
        return new PermissionResponse(
                permission.getCode(),
                permission.getModule(),
                permission.getDescription(),
                permission.isSensitive());
    }

    public RoleIncompatibilityResponse toIncompatibilityResponse(RoleIncompatibility pair) {
        return new RoleIncompatibilityResponse(
                pair.getRole().getCode(),
                pair.getIncompatibleRole().getCode(),
                pair.getReason(),
                pair.getCreatedBy() == null ? null : pair.getCreatedBy().getPublicId(),
                pair.getCreatedAt());
    }

    /** Un rol desactivado (RBAC-02) no aporta roles ni permisos aunque la asignacion siga vigente. */
    public static boolean isEffective(UserRole assignment, LocalDate day) {
        return assignment.isActiveOn(day) && assignment.getRole().isActive();
    }

    public List<String> activeRoleCodes(UserAccount user) {
        LocalDate today = LocalDate.now();
        return user.getRoleAssignments().stream()
                .filter(assignment -> isEffective(assignment, today))
                .map(assignment -> assignment.getRole().getCode())
                .distinct()
                .sorted()
                .toList();
    }

    public List<String> activePermissionCodes(UserAccount user) {
        LocalDate today = LocalDate.now();
        return user.getRoleAssignments().stream()
                .filter(assignment -> isEffective(assignment, today))
                .flatMap(assignment -> assignment.getRole().getPermissions().stream())
                .map(Permission::getCode)
                .distinct()
                .sorted()
                .toList();
    }
}
