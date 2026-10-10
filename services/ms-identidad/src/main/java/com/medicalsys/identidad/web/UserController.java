package com.medicalsys.identidad.web;

import com.medicalsys.identidad.domain.UserStatus;
import com.medicalsys.identidad.service.UserService;
import com.medicalsys.identidad.web.dto.ChangeStatusRequest;
import com.medicalsys.identidad.web.dto.CreateUserRequest;
import com.medicalsys.identidad.web.dto.PageResponse;
import com.medicalsys.identidad.web.dto.RoleAssignmentRequest;
import com.medicalsys.identidad.web.dto.UpdateUserRequest;
import com.medicalsys.identidad.web.dto.UserResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/identity/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(Authentication authentication,
                                               @Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.create(request, Actors.publicId(authentication));
        return ResponseEntity.created(URI.create("/api/v1/identity/users/" + created.publicId())).body(created);
    }

    @GetMapping
    public PageResponse<UserResponse> search(
            @RequestParam(required = false) UUID organizationPublicId,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return userService.search(organizationPublicId, status, query,
                PageRequest.of(page, Math.min(size, 100), Sort.by("email").ascending()));
    }

    @GetMapping("/{publicId}")
    public UserResponse get(@PathVariable UUID publicId) {
        return userService.get(publicId);
    }

    @PatchMapping("/{publicId}")
    public UserResponse update(@PathVariable UUID publicId, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(publicId, request);
    }

    @PatchMapping("/{publicId}/status")
    public UserResponse changeStatus(@PathVariable UUID publicId, @Valid @RequestBody ChangeStatusRequest request) {
        return userService.changeStatus(publicId, request);
    }

    @PostMapping("/{publicId}/roles")
    public UserResponse assignRole(Authentication authentication, @PathVariable UUID publicId,
                                   @Valid @RequestBody RoleAssignmentRequest request) {
        return userService.assignRole(publicId, request, Actors.publicId(authentication));
    }

    @DeleteMapping("/{publicId}/roles/{role}")
    public UserResponse revokeRole(Authentication authentication, @PathVariable UUID publicId,
                                   @PathVariable String role) {
        return userService.revokeRole(publicId, role, Actors.publicId(authentication));
    }
}
