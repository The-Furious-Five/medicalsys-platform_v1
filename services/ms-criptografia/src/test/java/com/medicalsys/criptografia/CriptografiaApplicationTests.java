package com.medicalsys.criptografia;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prueba de contexto mínima.
 * Usa perfil "test" con H2 en memoria — no requiere MySQL.
 * La verificación de health y ping se hace con docker compose ps
 * y curl/Postman como indica el checklist del equipo.
 */
@SpringBootTest
@ActiveProfiles("test")
class CriptografiaApplicationTests {

    /** El contexto Spring carga sin errores: Flyway, JPA y web activos. */
    @Test
    void contextLoads() {
    }
}
