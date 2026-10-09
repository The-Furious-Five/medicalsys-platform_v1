package com.medicalsys.facturacion.service;

import com.medicalsys.facturacion.common.error.ApiException;
import com.medicalsys.facturacion.common.security.Role;
import com.medicalsys.facturacion.common.tenant.RequestContext;
import com.medicalsys.facturacion.repository.InvoiceRepository;
import com.medicalsys.facturacion.web.dto.CreateInvoiceRequest;
import com.medicalsys.facturacion.web.dto.InvoiceLineRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Emite unas cuantas facturas de demostracion al arrancar con la base vacia.
 *
 * <p>No inserta filas: llama a {@link InvoiceService#issue} como lo haria la interfaz. Es la
 * diferencia que importa, porque una factura no es una fila sino una cadena -correlativo, CUFD,
 * CUF, cliente resuelto en ms-pacientes y precio resuelto en ms-tenencia-, y un INSERT directo
 * produciria comprobantes que ningun codigo del servicio ha validado nunca.
 *
 * <p>Sin esto la pantalla de facturacion abre vacia en una instalacion nueva, que era el caso: el
 * modulo funcionaba pero no habia forma de verlo sin emitir a mano.
 *
 * <p>Se desactiva con {@code BILLING_DEMO_INVOICES=false}, igual que el resto de la siembra.
 */
@Component
@ConditionalOnProperty(prefix = "medicalsys.billing.demo-setup", name = "seed-invoices",
        havingValue = "true", matchIfMissing = true)
public class DemoInvoiceSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoInvoiceSeeder.class);

    private static final UUID ORGANIZATION = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final UUID BRANCH_SOPOCACHI = UUID.fromString("00000000-0000-4000-8000-0000000000a2");
    private static final UUID BRANCH_MIRAFLORES = UUID.fromString("00000000-0000-4000-8000-0000000000a3");
    private static final UUID CASHIER = UUID.fromString("00000000-0000-4000-8000-000000000904");

    /** Prestaciones sembradas por ms-tenencia. */
    private static final UUID CONSULTA_GENERAL = UUID.fromString("00000000-0000-4000-8000-000000000501");
    private static final UUID RADIOGRAFIA = UUID.fromString("00000000-0000-4000-8000-000000000502");
    private static final UUID CONSULTA_CARDIO = UUID.fromString("00000000-0000-4000-8000-000000000506");
    private static final UUID CONSULTA_PEDIATRIA = UUID.fromString("00000000-0000-4000-8000-000000000504");
    private static final UUID CONTROL = UUID.fromString("00000000-0000-4000-8000-000000000508");
    private static final UUID LAB_BASICO = UUID.fromString("00000000-0000-4000-8000-00000000050a");
    private static final UUID ELECTROCARDIO = UUID.fromString("00000000-0000-4000-8000-00000000050b");

    /** Pacientes sembrados por ms-pacientes. */
    private static final UUID P_MARIA = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID P_JUAN = UUID.fromString("10000000-0000-4000-8000-000000000004");
    private static final UUID P_GREGORIO = UUID.fromString("10000000-0000-4000-8000-000000000005");
    private static final UUID P_MATEO = UUID.fromString("10000000-0000-4000-8000-000000000003");
    private static final UUID P_FREDDY = UUID.fromString("10000000-0000-4000-8000-000000000009");

    /** Metodos de pago del catalogo del SIN: 1 efectivo, 2 tarjeta, 7 transferencia. */
    private static final int EFECTIVO = 1;
    private static final int TARJETA = 2;
    private static final int TRANSFERENCIA = 7;

    private final InvoiceService invoices;
    private final InvoiceRepository repository;

    public DemoInvoiceSeeder(InvoiceService invoices, InvoiceRepository repository) {
        this.invoices = invoices;
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        RequestContext context = new RequestContext(ORGANIZATION, CASHIER,
                Set.of(Role.BILLING_CLERK, Role.ADMINISTRATOR));

        int issued = 0;
        issued += emit(context, P_MARIA, BRANCH_SOPOCACHI, EFECTIVO,
                line(CONSULTA_GENERAL, "1", "0"), line(LAB_BASICO, "1", "0"));
        issued += emit(context, P_JUAN, BRANCH_MIRAFLORES, TARJETA,
                line(CONSULTA_CARDIO, "1", "0"), line(ELECTROCARDIO, "1", "15.00"));
        issued += emit(context, P_GREGORIO, BRANCH_SOPOCACHI, EFECTIVO,
                line(CONSULTA_GENERAL, "1", "0"), line(RADIOGRAFIA, "1", "0"));
        issued += emit(context, P_MATEO, BRANCH_SOPOCACHI, TRANSFERENCIA,
                line(CONSULTA_PEDIATRIA, "1", "20.00"));
        issued += emit(context, P_FREDDY, BRANCH_SOPOCACHI, EFECTIVO,
                line(CONTROL, "1", "0"));

        if (issued > 0) {
            log.info("{} factura(s) de demostracion emitidas por el propio servicio", issued);
        }
    }

    private InvoiceLineRequest line(UUID serviceOfferingId, String quantity, String discount) {
        return new InvoiceLineRequest(serviceOfferingId, new BigDecimal(quantity),
                new BigDecimal(discount), null, null);
    }

    /**
     * Emite una factura y sigue adelante si falla.
     *
     * <p>La emision depende de ms-pacientes y ms-tenencia por HTTP. Que uno de ellos no responda
     * durante el arranque no puede dejar el servicio de facturacion caido: se registra y la
     * siembra se completa en el siguiente arranque, porque solo corre con la tabla vacia.
     */
    private int emit(RequestContext context, UUID patientId, UUID branchId, int paymentMethodCode,
            InvoiceLineRequest... items) {
        try {
            invoices.issue(context, new CreateInvoiceRequest(patientId, branchId, null,
                    paymentMethodCode, List.of(items)));
            return 1;
        } catch (ApiException e) {
            log.warn("No se pudo emitir la factura de demostracion del paciente {}: {}",
                    patientId, e.getMessage());
            return 0;
        } catch (RuntimeException e) {
            log.warn("La siembra de facturas quedo incompleta en el paciente {}: {}",
                    patientId, e.toString());
            return 0;
        }
    }
}
