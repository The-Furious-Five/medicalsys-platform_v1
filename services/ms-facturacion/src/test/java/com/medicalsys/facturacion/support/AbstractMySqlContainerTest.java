package com.medicalsys.facturacion.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Base de las pruebas que necesitan el motor real.
 *
 * <p>Se ejecutan contra MySQL 8 en un contenedor, con las mismas migraciones Flyway que usa el
 * servicio: comprobar ENUM, CHECK, restricciones UNIQUE y el bloqueo del correlativo exige el motor
 * de verdad.
 *
 * <p>El contenedor sigue el patron singleton: arranca una vez por JVM y no se detiene entre clases.
 * Con {@code @Container} de JUnit se detendria al terminar cada clase y los contextos de Spring
 * cacheados quedarian apuntando a un puerto muerto, produciendo timeouts de conexion.
 *
 * <p>Si el equipo trabaja sin Docker activo, las pruebas se marcan como omitidas en vez de fallar,
 * para que la suite siga siendo util en cualquier maquina. Antes de la Review deben ejecutarse con
 * Docker encendido: son la evidencia de que la migracion funciona desde una base vacia, de que el
 * total coincide con la suma del detalle y de que el correlativo no se repite.
 *
 * <p>Cada subclase declara {@code @EnabledIf(DOCKER_CONDITION)}: la anotacion de JUnit no es
 * heredable, de modo que anotar solo esta clase base no tendria efecto.
 */
public abstract class AbstractMySqlContainerTest {

    /** Referencia a la condicion, para que las subclases no repitan la cadena. */
    public static final String DOCKER_CONDITION =
            "com.medicalsys.facturacion.support.AbstractMySqlContainerTest#dockerAvailable";

    private static final boolean DOCKER_AVAILABLE = probeDocker();

    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("billing_db")
            .withUsername("billing_user")
            .withPassword("change-me")
            .withCommand("--default-time-zone=+00:00");

    static {
        if (DOCKER_AVAILABLE) {
            MYSQL.start();
        }
    }

    /** Apunta la datasource al contenedor; el metodo se hereda por toda la jerarquia. */
    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    /**
     * Condicion de ejecucion: sin demonio Docker la prueba se omite.
     *
     * <p>Es publica y estatica para que JUnit la resuelva desde las subclases, que viven en otros
     * paquetes. El resultado se memoriza porque sondear Docker una vez por clase es lento.
     */
    public static boolean dockerAvailable() {
        return DOCKER_AVAILABLE;
    }

    private static boolean probeDocker() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }
}
