package com.medicalsys.marketing.event;

import com.medicalsys.marketing.common.error.BusinessRuleException;
import com.medicalsys.marketing.common.error.ErrorCode;
import com.medicalsys.marketing.domain.enums.PatientStatus;
import com.medicalsys.marketing.domain.enums.Sex;
import com.medicalsys.marketing.domain.model.Campaign;
import com.medicalsys.marketing.domain.model.CampaignRecipient;
import com.medicalsys.marketing.domain.model.PatientActivity;
import com.medicalsys.marketing.domain.model.ProcessedEvent;
import com.medicalsys.marketing.privacy.ClinicalDataGuard;
import com.medicalsys.marketing.repository.CampaignRecipientRepository;
import com.medicalsys.marketing.repository.CampaignRepository;
import com.medicalsys.marketing.repository.ProcessedEventRepository;
import com.medicalsys.marketing.service.LoyaltyService;
import com.medicalsys.marketing.service.ReferenceDataService;
import com.medicalsys.marketing.web.dto.EventAcceptedResponse;
import com.medicalsys.marketing.web.dto.IncomingEventRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aplica los eventos que ms-marketing consume.
 *
 * <p>Es el unico punto de entrada de hechos ajenos, y por eso concentra tres garantias:
 * <ol>
 *   <li><b>Idempotencia.</b> El {@code eventId} se anota en {@code processed_event}. Reprocesar los
 *       eventos de facturacion de un dia sin esto duplicaria el gasto acumulado de cada paciente y,
 *       con el, la audiencia de todo segmento que filtre por gasto.</li>
 *   <li><b>Frontera de privacidad.</b> Cada cuerpo pasa por {@link ClinicalDataGuard} antes de
 *       proyectarse, y ademas cada manejador lee solo los campos que nombra. La inspeccion vive aqui
 *       y no en el controlador a proposito: el controlador es un transporte entre varios posibles, y
 *       el dia que los eventos lleguen por un broker seguirian teniendo que pasar por esta barrera.
 *       Un productor descuidado que incluyera un diagnostico recibe 422; uno que incluya un campo
 *       irrelevante simplemente no lo ve proyectado.</li>
 *   <li><b>Proyeccion parcial explicita.</b> De cada evento se toma lo minimo. Del encuentro clinico,
 *       por ejemplo, se toma una fecha y nada mas.</li>
 * </ol>
 *
 * <p>El MVP de la plataforma no tiene broker: los eventos llegan por una ruta interna con la misma
 * forma que tendrian en un mensaje. El dia que aparezca el broker cambia el transporte, no esto.
 */
@Service
public class EventIngestionService {

    private static final Logger log = LoggerFactory.getLogger(EventIngestionService.class);

    /** Estados de cita que cuentan como visita atendida. */
    private static final String ATTENDED = "ATTENDED";
    private static final String COMPLETED = "COMPLETED";
    private static final String NO_SHOW = "NO_SHOW";

    private final ProcessedEventRepository processedEvents;
    private final ReferenceDataService referenceData;
    private final LoyaltyService loyalty;
    private final CampaignRepository campaigns;
    private final CampaignRecipientRepository recipients;
    private final ClinicalDataGuard clinicalDataGuard;
    private final Clock clock;

    public EventIngestionService(ProcessedEventRepository processedEvents, ReferenceDataService referenceData,
            LoyaltyService loyalty, CampaignRepository campaigns, CampaignRecipientRepository recipients,
            ClinicalDataGuard clinicalDataGuard, Clock clock) {
        this.processedEvents = processedEvents;
        this.referenceData = referenceData;
        this.loyalty = loyalty;
        this.campaigns = campaigns;
        this.recipients = recipients;
        this.clinicalDataGuard = clinicalDataGuard;
        this.clock = clock;
    }

    /**
     * Aplica un evento entrante.
     *
     * <p>Un evento ya aplicado devuelve {@code duplicate=true} y no vuelve a proyectarse. Se responde
     * 200 y no 409: un reintento tras un timeout es el comportamiento correcto de un productor, y
     * responderle un error lo empujaria a tratar como fallo algo que funciono.
     */
    @Transactional
    public EventAcceptedResponse apply(IncomingEventRequest request) {
        // La inspeccion va antes que el inbox: un evento con dato clinico no debe quedar anotado como
        // aplicado, porque entonces un reenvio corregido se descartaria como duplicado.
        clinicalDataGuard.assertEventPayloadAllowed(request.payload(),
                "evento entrante '" + request.eventType() + "'");

        if (processedEvents.existsById(request.eventId())) {
            return new EventAcceptedResponse(request.eventId(), request.eventType(), true,
                    "El evento ya se habia aplicado; no se repite");
        }

        String detail = dispatch(request);
        processedEvents.save(ProcessedEvent.of(request.eventId(), request.eventType()));
        log.debug("Evento {} de tipo {} aplicado: {}", request.eventId(), request.eventType(), detail);
        return new EventAcceptedResponse(request.eventId(), request.eventType(), false, detail);
    }

    /**
     * Encamina el evento a su manejador.
     *
     * <p>El encaminamiento es por prefijo para que un productor pueda afinar el tipo -- de
     * {@code agenda.cita} a {@code agenda.cita.atendida} -- sin romper a este consumidor.
     */
    private String dispatch(IncomingEventRequest request) {
        String type = request.eventType().trim().toLowerCase(Locale.ROOT);
        EventPayload payload = new EventPayload(request.payload(), request.eventType());
        UUID organizationId = request.organizationId();

        if (type.startsWith("tenencia.organizacion")) {
            return applyOrganization(organizationId, payload);
        }
        if (type.startsWith("tenencia.servicio") || type.startsWith("tenencia.prestacion")) {
            return applyServiceOffering(organizationId, payload);
        }
        if (type.startsWith("pacientes.paciente")) {
            return applyPatient(organizationId, payload);
        }
        if (type.startsWith("agenda.cita")) {
            return applyAppointment(organizationId, payload);
        }
        if (type.startsWith("facturacion.factura")) {
            return applyInvoice(organizationId, payload, request.eventId());
        }
        if (type.startsWith("historial.encuentro")) {
            return applySignedEncounter(organizationId, payload);
        }
        if (type.startsWith("notificaciones.mensaje")) {
            return applyNotification(organizationId, payload);
        }

        throw new BusinessRuleException(ErrorCode.EVENT_NOT_SUPPORTED,
                "ms-marketing no consume el evento '" + request.eventType() + "'. Consume: tenencia.organizacion, "
                        + "tenencia.servicio, pacientes.paciente, agenda.cita, facturacion.factura, "
                        + "historial.encuentro y notificaciones.mensaje");
    }

    // ------------------------------------------------------------------------------- ms-tenencia

    private String applyOrganization(UUID organizationId, EventPayload payload) {
        referenceData.applyOrganization(organizationId,
                payload.text("commercialName").orElse(null),
                payload.flag("includesMarketing").orElse(null),
                payload.version());
        return "Replica de organizacion actualizada";
    }

    private String applyServiceOffering(UUID organizationId, EventPayload payload) {
        UUID serviceOfferingId = payload.requireUuid("serviceOfferingId");
        referenceData.applyServiceOffering(serviceOfferingId, organizationId,
                payload.text("name").orElse(null),
                payload.decimal("basePriceBob").orElse(null),
                payload.flag("active").orElse(null),
                payload.version());
        return "Replica de prestacion actualizada";
    }

    // ------------------------------------------------------------------------------ ms-pacientes

    /**
     * Proyecta el alta o el cambio de un paciente.
     *
     * <p>Si el evento trae la organizacion -- y en esta plataforma siempre la trae, en el sobre -- se
     * abre ademas su fila de actividad. Eso es lo que hace "conocido" al paciente para ese tenant:
     * sin fila de actividad no entra en ninguna audiencia, ni siquiera en una puramente demografica.
     */
    private String applyPatient(UUID organizationId, EventPayload payload) {
        UUID patientId = payload.requireUuid("patientId");
        referenceData.applyPatient(patientId,
                payload.text("fullName").orElse(null),
                payload.date("birthDate").orElse(null),
                payload.enumeration("sex", Sex.class).orElse(null),
                payload.text("city").orElse(null),
                payload.text("phoneE164").orElse(null),
                payload.enumeration("status", PatientStatus.class).orElse(null),
                payload.version());

        referenceData.requireActivity(organizationId, patientId);

        Optional<Boolean> optOut = payload.flag("marketingOptOut");
        if (optOut.isPresent()) {
            referenceData.setMarketingOptOut(organizationId, patientId, optOut.get(), clock.instant());
        }
        return "Replica demografica del paciente actualizada";
    }

    // --------------------------------------------------------------------------------- ms-agenda

    /**
     * Proyecta una cita sobre la actividad comercial y, si venia de una campana, la atribuye.
     *
     * <p>El evento de agenda no trae el motivo de la cita, y este manejador no lo pediria aunque lo
     * trajera: cuenta visitas e inasistencias, que es informacion administrativa.
     */
    private String applyAppointment(UUID organizationId, EventPayload payload) {
        UUID patientId = payload.requireUuid("patientId");
        String status = payload.text("status").orElse(ATTENDED).toUpperCase(Locale.ROOT);
        LocalDate scheduledOn = payload.date("scheduledOn").orElse(LocalDate.now(clock));

        PatientActivity activity = referenceData.requireActivity(organizationId, patientId);
        String outcome;
        if (NO_SHOW.equals(status)) {
            activity.registerNoShow();
            outcome = "Inasistencia registrada";
        } else if (ATTENDED.equals(status) || COMPLETED.equals(status)) {
            activity.registerAttendedAppointment(scheduledOn);
            outcome = "Visita registrada";
        } else {
            // Una cita agendada o cancelada no cambia la actividad; anotarla igual seria inventar historia.
            outcome = "El estado '" + status + "' no altera la actividad comercial";
        }

        payload.uuid("campaignId").ifPresent(campaignId ->
                attributeConversion(organizationId, campaignId, patientId,
                        payload.uuid("appointmentId").orElse(null)));
        return outcome;
    }

    /**
     * Atribuye la cita a la campana que la origino.
     *
     * <p>La atribucion solo toca al destinatario que ya estaba en la audiencia: si el paciente no
     * figuraba, la campana no puede haberla causado y anotarla inflaria la tasa de conversion.
     */
    private void attributeConversion(UUID organizationId, UUID campaignId, UUID patientId, UUID appointmentId) {
        Optional<Campaign> campaign = campaigns.findByPublicIdAndOrganizationPublicId(campaignId, organizationId);
        if (campaign.isEmpty()) {
            log.debug("Se ignoro la atribucion a la campana {}: no pertenece al tenant {}", campaignId,
                    organizationId);
            return;
        }
        Optional<CampaignRecipient> recipient =
                recipients.findByCampaignIdAndPatientPublicId(campaign.get().getId(), patientId);
        if (recipient.isEmpty()) {
            log.debug("Se ignoro la atribucion: el paciente {} no estaba en la audiencia de {}", patientId,
                    campaignId);
            return;
        }
        if (recipient.get().markConverted(appointmentId)) {
            campaign.get().registerConversion();
        }
    }

    // ---------------------------------------------------------------------------- ms-facturacion

    /**
     * Proyecta una factura: suma el gasto, recuerda la prestacion y acumula puntos.
     *
     * <p>La acumulacion usa el {@code eventId} como clave de idempotencia, de modo que un reenvio del
     * evento no otorga los puntos dos veces ni siquiera si el inbox se hubiera purgado.
     */
    private String applyInvoice(UUID organizationId, EventPayload payload, UUID eventId) {
        UUID patientId = payload.requireUuid("patientId");
        UUID invoiceId = payload.requireUuid("invoiceId");
        BigDecimal totalAmountBob = payload.decimal("totalAmountBob").orElse(BigDecimal.ZERO);
        Optional<UUID> serviceOfferingId = payload.uuid("serviceOfferingId");

        // La prestacion se replica antes de referenciarla: patient_activity la apunta por FK.
        serviceOfferingId.ifPresent(id -> referenceData.findOrHydrateServiceOffering(organizationId, id));

        PatientActivity activity = referenceData.requireActivity(organizationId, patientId);
        activity.registerBilledAmount(totalAmountBob, serviceOfferingId.orElse(null));
        payload.date("issuedOn").ifPresent(activity::registerAttendedAppointment);

        String accrual = loyalty.accrueForInvoice(organizationId, patientId, totalAmountBob, invoiceId, eventId)
                .map(movement -> " y se acumularon " + movement.getPoints() + " puntos")
                .orElse("");
        return "Gasto de " + totalAmountBob + " BOB proyectado" + accrual;
    }

    // ------------------------------------------------------------------------------ ms-historial

    /**
     * Proyecta el unico dato que ms-historial comparte con marketing: la fecha del proximo control.
     *
     * <p>Merece detenerse un momento. {@code EncuentroFirmado} es un evento del expediente clinico, y
     * aqui se lee <b>un solo campo</b>: {@code suggestedNextVisitOn}. No hay diagnostico, no hay
     * motivo, no hay codigo CIE-10 -- ni en este metodo, ni en la firma de
     * {@code PatientActivity#setSuggestedNextVisit}, ni en el esquema. Marketing sabe cuando volver a
     * llamar; de que padece el paciente no se entera. Si el productor incluyera el motivo en el
     * cuerpo, el guardian de privacidad devuelve 422 antes de llegar hasta aqui.
     */
    private String applySignedEncounter(UUID organizationId, EventPayload payload) {
        UUID patientId = payload.requireUuid("patientId");
        Optional<LocalDate> suggested = payload.date("suggestedNextVisitOn");
        if (suggested.isEmpty()) {
            return "El evento no trae fecha de control sugerida; no hay nada que proyectar";
        }
        referenceData.requireActivity(organizationId, patientId).setSuggestedNextVisit(suggested.get());
        return "Fecha de control sugerida actualizada, sin ningun dato clinico";
    }

    // ------------------------------------------------------------------------- ms-notificaciones

    /** Proyecta el resultado de entrega de un mensaje sobre el destinatario y sus contadores. */
    private String applyNotification(UUID organizationId, EventPayload payload) {
        UUID campaignId = payload.requireUuid("campaignId");
        UUID patientId = payload.requireUuid("patientId");
        String status = payload.text("status").orElse("SENT").toUpperCase(Locale.ROOT);

        Campaign campaign = campaigns.findByPublicIdAndOrganizationPublicId(campaignId, organizationId)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.CAMPAIGN_NOT_FOUND,
                        "La campana '" + campaignId + "' no existe en esta organizacion"));
        CampaignRecipient recipient = recipients
                .findByCampaignIdAndPatientPublicId(campaign.getId(), patientId)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.EVENT_NOT_SUPPORTED,
                        "El paciente '" + patientId + "' no figura en la audiencia de la campana"));

        return switch (status) {
            case "SENT", "DELIVERED" -> {
                recipient.markSent(payload.uuid("notificationId").orElse(null));
                campaign.registerSent();
                yield "Entrega registrada";
            }
            case "READ" -> {
                recipient.markRead();
                campaign.registerRead();
                yield "Lectura registrada";
            }
            case "FAILED" -> {
                recipient.markFailed(payload.text("reason").orElse("Entrega fallida"));
                yield "Fallo de entrega registrado";
            }
            default -> throw new BusinessRuleException(ErrorCode.EVENT_NOT_SUPPORTED,
                    "El estado de mensaje '" + status + "' no es SENT, DELIVERED, READ ni FAILED");
        };
    }
}
