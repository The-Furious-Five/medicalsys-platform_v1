package com.medicalsys.identidad.service;

import com.medicalsys.identidad.config.IdentityProperties;
import com.medicalsys.identidad.domain.RoleCode;
import com.medicalsys.identidad.domain.RoleScope;
import com.medicalsys.identidad.domain.SecurityLevel;
import com.medicalsys.identidad.repository.UserAccountRepository;
import com.medicalsys.identidad.web.dto.CreateUserRequest;
import com.medicalsys.identidad.web.dto.RoleAssignmentRequest;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
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
@Order(10) // antes de KeycloakAccountMigration (100), que enlaza lo que este seeder crea
public class DemoUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    /**
     * Una cuenta del centro. El identificador publico se fija aqui, no se sortea. El nivel MAC
     * (ISO-01) es el que el OSI le daria al rol: sin el, la cuenta entraria con INTERNAL y su rol no
     * le aportaria los permisos confidenciales.
     */
    private record DemoAccount(String publicId, String email, String fullName, String phone, RoleCode role) {

        SecurityLevel clearance() {
            return switch (role) {
                case PHYSICIAN, OSI -> SecurityLevel.RESTRICTED;
                case PATIENT -> SecurityLevel.INTERNAL;
                default -> SecurityLevel.CONFIDENTIAL;
            };
        }
    }

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
                    "Martin Ibanez Sosa", "+59179516024", RoleCode.PHYSICIAN),
            // ISO-01: una cuenta por cada rol nuevo, para recorrer la interfaz con cada uno.
            new DemoAccount("00000000-0000-4000-8000-00000000090b", "osi@medicalsys.demo",
                    "Lucia Quiroga Vargas", "+59171839204", RoleCode.OSI),
            new DemoAccount("00000000-0000-4000-8000-00000000090c", "auditor@medicalsys.demo",
                    "Hugo Paredes Lima", "+59176203915", RoleCode.AUDITOR),
            new DemoAccount("00000000-0000-4000-8000-00000000090d", "enfermeria@medicalsys.demo",
                    "Carla Rojas Mamani", "+59172648130", RoleCode.NURSE),
            new DemoAccount("00000000-0000-4000-8000-00000000090e", "laboratorio@medicalsys.demo",
                    "Diego Choque Flores", "+59175092471", RoleCode.LAB_TECHNICIAN),
            new DemoAccount("00000000-0000-4000-8000-00000000090f", "marketing@medicalsys.demo",
                    "Valeria Gutierrez Soria", "+59178350619", RoleCode.MARKETING_MANAGER));

    private final IdentityProperties properties;
    private final UserAccountRepository users;
    private final UserService userService;
    private final KeycloakAccountLinker linker;

    public DemoUserSeeder(IdentityProperties properties, UserAccountRepository users, UserService userService,
                          KeycloakAccountLinker linker) {
        this.properties = properties;
        this.users = users;
        this.userService = userService;
        this.linker = linker;
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
        boolean created = !users.existsByEmailIgnoreCase(account.email());
        if (created) {
            userService.createSeedAccount(new CreateUserRequest(
                    account.email(), account.fullName(), password, account.phone(), organization, "es-BO", true,
                    List.of(new RoleAssignmentRequest(account.role().name(), organization, null, null, null))));
            log.info("Cuenta de demostracion creada: {} con rol {}", account.email(), account.role());
        }
        // Identificador, organización y alcance de roles se reconcilian también en cuentas ya
        // existentes. Esto corrige bases creadas por versiones anteriores que usaban un UUID de
        // organización distinto al public_id sembrado por ms-tenencia.
        users.findByEmailIgnoreCase(account.email())
                .ifPresent(user -> {
                    // IAM-03: la credencial vive en Keycloak; si falta allí, se repone con la de la demo.
                    linker.restoreConfiguredCredential(user, password);
                    // Solo al crearla: despues el nivel es decision del OSI y un reinicio no lo pisa.
                    boolean changed = created;
                    if (created) {
                        user.setClearance(account.clearance());
                    }
                    if (!publicId.equals(user.getPublicId())) {
                        user.setPublicId(publicId);
                        changed = true;
                    }
                    if (!organization.equals(user.getOrganizationPublicId())) {
                        user.setOrganizationPublicId(organization);
                        changed = true;
                    }
                    for (var assignment : user.getRoleAssignments()) {
                        // Un rol de plataforma (el OSI) no pertenece a ninguna organizacion.
                        if (assignment.getRole().getScope() != RoleScope.PLATFORM
                                && !organization.equals(assignment.getOrganizationPublicId())) {
                            assignment.setOrganizationPublicId(organization);
                            changed = true;
                        }
                    }
                    if (changed) {
                        users.save(user);
                        log.info("Cuenta demo {} reconciliada con organizationId={}", account.email(),
                                organization);
                    }
                });
    }
}
