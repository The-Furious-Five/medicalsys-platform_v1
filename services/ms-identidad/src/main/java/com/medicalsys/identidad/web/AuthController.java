package com.medicalsys.identidad.web;

import com.medicalsys.identidad.service.ApiException;
import com.medicalsys.identidad.service.AuthService;
import com.medicalsys.identidad.service.UserService;
import com.medicalsys.identidad.web.dto.AuthenticatedUser;
import com.medicalsys.identidad.web.dto.ChangePasswordRequest;
import com.medicalsys.identidad.web.dto.LoginRequest;
import com.medicalsys.identidad.web.dto.LoginResponse;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/identity/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public AuthenticatedUser me(Authentication authentication) {
        return authService.currentUser(subject(authentication));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(Authentication authentication) {
        Jwt jwt = jwt(authentication);
        authService.logout(jwt.getId(), subject(authentication));
        return ResponseEntity.ok(Map.of("status", "session-closed"));
    }

    /**
     * Con sesión cambia la contraseña del titular del JWT. Sin sesión resuelve el primer ingreso con
     * contraseña temporal y la contraseña vencida (IAM-05): la cuenta sale de {@code email}.
     */
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(Authentication authentication,
                                                              @Valid @RequestBody ChangePasswordRequest request) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
            userService.changePassword(subject(authentication), request);
        } else if (request.email() != null) {
            userService.changePasswordWithoutSession(request);
        } else {
            throw ApiException.unauthorized("Inicia sesion o indica el correo de la cuenta");
        }
        return ResponseEntity.ok(Map.of("status", "password-updated"));
    }

    private Jwt jwt(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw ApiException.unauthorized("Se requiere un token de acceso valido");
        }
        return jwt;
    }

    private UUID subject(Authentication authentication) {
        return UUID.fromString(jwt(authentication).getSubject());
    }
}
