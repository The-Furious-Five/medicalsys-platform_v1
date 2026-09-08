package com.medicalsys.identidad.service;

import com.medicalsys.identidad.config.IdentityProperties;
import com.medicalsys.identidad.domain.RoleCode;
import com.medicalsys.identidad.repository.UserAccountRepository;
import com.medicalsys.identidad.web.dto.CreateUserRequest;
import com.medicalsys.identidad.web.dto.RoleAssignmentRequest;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cuentas del centro de demostracion, con contrasena hasheada por {@link UserService}.
 * Se puede desactivar con IDENTITY_DEMO_USERS_ENABLED=false.
 *
 * <p>El padron no son tres cuentas genericas sino el personal de un policlinico: la
 * administradora, recepcion, caja y cuatro medicos con nombre propio. Sin varios medicos no
 * hay nada que elegir en el desplegable de la agenda ni en el alta de profesionales, y el
 * filtro por especialidad no se puede probar.
 *
 * <p>Los identificadores publicos son fijos y no aleatorios. ms-profesionales siembra a cada
 * medico contra el {@code user_account_public_id} de su cuenta, y son bases de datos
 * distintas: sin un identificador acordado de antemano, la ficha del profesional apuntaria a
 * una cuenta que no existe y el alta desde la interfaz no encontraria con quien enlazarlo.
 */
@Component
public class DemoUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    /** Una cuenta del centro. El identificador publico se fija aqui, no se sortea. */
    private record DemoAccount(String publicId, String email, String fullName, String phone, RoleCode role) { }

    private static final List<DemoAccount> ACCOUNTS = List.of(
            new DemoAccount("00000000-0000-4000-8000-000000000901", "admin@medicalsys.demo",
                    "Patricia Loayza Bernal", "+59171204583", RoleCode.ADMINISTRATOR),
            new DemoAccount("00000000-0000-4000-8000-000000000902", "recepcion@medicalsys.demo",
                    "Elena Cruz Apaza", "+59176418209", RoleCode.RECEPTIONIST),
            new DemoAccount("00000000-0000-4000-8000-000000000903", "doctor@medicalsys.demo",
                    "Ricardo Salazar Peredo", "+59170385142", RoleCode.PHYSICIAN),
            new DemoAccount("00000000-0000-4000-8000-000000000904", "caja@medicalsys.demo",
                    "Marcelo Villegas Ortuno", "+59173509164", RoleCode.BILLING_CLERK),
            new DemoAccount("00000000-0000-4000-8000-000000000905", "pediatria@medicalsys.demo",
                    "Gabriela Nunez Arce", "+59177260913", RoleCode.PHYSICIAN),
            new DemoAccount("00000000-0000-4000-8000-000000000906", "ginecologia@medicalsys.demo",
                    "Silvia Mendoza Rocha", "+59178143026", RoleCode.PHYSICIAN),
            new DemoAccount("00000000-0000-4000-8000-000000000907", "cardiologia@medicalsys.demo",
                    "Fernando Arispe Calderon", "+59172907485", RoleCode.PHYSICIAN),
            new DemoAccount("00000000-0000-4000-8000-000000000908", "traumatologia@medicalsys.demo",
                    "Oscar Ledezma Fuentes", "+59175831470", RoleCode.PHYSICIAN),
            // Nutricion y odontologia tambien abren encuentros y firman, asi que llevan
            // PHYSICIAN: RoleCode no distingue por profesion, distingue por lo que la
            // persona puede hacer sobre el expediente.
            new DemoAccount("00000000-0000-4000-8000-000000000909", "nutricion@medicalsys.demo",
                    "Andrea Terceros Blanco", "+59174620358", RoleCode.PHYSICIAN),
            new DemoAccount("00000000-0000-4000-8000-00000000090a", "odontologia@medicalsys.demo",
                    "Martin Ibanez Sosa", "+59179516024", RoleCode.PHYSICIAN));

    private final IdentityProperties properties;
    private final UserAccountRepository users;
    private final UserService userService;

    public DemoUserSeeder(IdentityProperties properties, UserAccountRepository users, UserService userService) {
        this.properties = properties;
        this.users = users;
        this.userService = userService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.getDemoUsers().isEnabled()) {
            return;
        }
        UUID organization = properties.getDemoUsers().getOrganizationPublicId();
        String password = properties.getDemoUsers().getPassword();
        for (DemoAccount account : ACCOUNTS) {
            seed(account, organization, password);
        }
    }

    private void seed(DemoAccount account, UUID organization, String password) {
        UUID publicId = UUID.fromString(account.publicId());
        if (!users.existsByEmailIgnoreCase(account.email())) {
            userService.create(new CreateUserRequest(
                    account.email(), account.fullName(), password, account.phone(), organization, "es-BO", true,
                    List.of(new RoleAssignmentRequest(account.role(), organization, null, null, null))), null);
            log.info("Cuenta de demostracion creada: {} con rol {}", account.email(), account.role());
        }
        // El identificador se fija tambien sobre las cuentas ya existentes: una base sembrada
        // por una version anterior las tiene con UUID aleatorio, y ms-profesionales quedaria
        // enlazado a cuentas inexistentes. Es un UPDATE por arranque y solo en local.
        users.findByEmailIgnoreCase(account.email())
                .filter(user -> !publicId.equals(user.getPublicId()))
                .ifPresent(user -> {
                    user.setPublicId(publicId);
                    users.save(user);
                    log.info("Identificador publico de {} fijado en {}", account.email(), publicId);
                });
    }
}
