package com.medicalsys.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Prueba de contexto del gateway.
 *
 * <p>El secreto se inyecta aqui porque {@code GatewayJwtConfig} exige al menos 32 bytes y falla el
 * arranque si no llega: es deliberado -un gateway que valida firmas no debe arrancar con un
 * secreto vacio- pero significa que el contexto de prueba tiene que aportar uno. Sin esta
 * propiedad la prueba fallaba desde que se anadio la propagacion de JWT.
 *
 * <p>Es un valor de prueba, no un secreto: en ejecucion lo aporta la variable de entorno.
 */
@SpringBootTest(properties = "JWT_SECRET=gateway-test-secret-de-al-menos-32-bytes")
class GatewayApplicationTests {

    @Test
    void contextLoads() {
    }
}
