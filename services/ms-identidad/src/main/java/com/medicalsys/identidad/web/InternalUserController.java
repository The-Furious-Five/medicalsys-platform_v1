package com.medicalsys.identidad.web;

import com.medicalsys.identidad.service.IdentityMapper;
import com.medicalsys.identidad.service.UserService;
import com.medicalsys.identidad.web.dto.InternalUserResponse;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrato interno para otros microservicios de la red Compose.
 * Devuelve el minimo necesario para validar un created_by_user_public_id.
 */
@RestController
@RequestMapping("/internal/v1/users")
public class InternalUserController {

    private final UserService userService;
    private final IdentityMapper mapper;

    public InternalUserController(UserService userService, IdentityMapper mapper) {
        this.userService = userService;
        this.mapper = mapper;
    }

    @GetMapping("/{publicId}")
    @Transactional(readOnly = true)
    public InternalUserResponse get(@PathVariable UUID publicId) {
        return mapper.toInternalResponse(userService.require(publicId));
    }
}
