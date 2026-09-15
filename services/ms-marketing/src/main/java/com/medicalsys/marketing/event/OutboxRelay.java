package com.medicalsys.marketing.event;

import com.medicalsys.marketing.config.MarketingProperties;
import com.medicalsys.marketing.config.MarketingProperties.OutboxMode;
import com.medicalsys.marketing.domain.enums.OutboxStatus;
import com.medicalsys.marketing.domain.model.OutboxEvent;
import com.medicalsys.marketing.repository.OutboxEventRepository;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drena el outbox de eventos salientes.
 *
 * <p>Segun {@code MARKETING_OUTBOX_MODE}: publica de verdad en Kafka ({@code KAFKA}), registra el
 * evento en el log y lo marca publicado sin transporte real ({@code LOG}), o lo deja acumulado para
 * que otro proceso lo lea por la ruta interna ({@code RETAIN}).
 *
 * <p>Lo que importa es que el resto del servicio ya esta escrito contra el outbox y no contra el
 * transporte: cambiar de modo no hace que ningun caso de uso se entere. Esa es toda la razon de que
 * el patron exista.
 *
 * <p>Los eventos en ERROR se vuelven a tomar junto con los pendientes. Es un reintento simple y sin
 * backoff, suficiente para el MVP; un backoff y una cola de descarte quedan para mas adelante.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private static final List<OutboxStatus> DRAINABLE = List.of(OutboxStatus.PENDING, OutboxStatus.ERROR);

    private final OutboxEventRepository outbox;
    private final MarketingProperties properties;
    private final Clock clock;
    private final KafkaTemplate<String, String> kafka;

    public OutboxRelay(OutboxEventRepository outbox, MarketingProperties properties, Clock clock,
            KafkaTemplate<String, String> kafka) {
        this.outbox = outbox;
        this.properties = properties;
        this.clock = clock;
        this.kafka = kafka;
        if (properties.outbox().mode() == OutboxMode.LOG) {
            log.warn("El relay del outbox marcara los eventos como publicados tras registrarlos en el log "
                    + "(MARKETING_OUTBOX_MODE=LOG) sin publicarlos de verdad. Use KAFKA para publicarlos en el "
                    + "broker o RETAIN para conservarlos pendientes y drenarlos desde /internal/v1/eventos/salientes.");
        }
    }

    /** Toma un lote de eventos pendientes y los drena segun el modo configurado. */
    @Scheduled(fixedDelayString = "${medicalsys.outbox.interval-ms:5000}")
    @Transactional
    public void drain() {
        if (properties.outbox().mode() == OutboxMode.RETAIN) {
            return;
        }
        List<OutboxEvent> batch = outbox.findByStatusInOrderByIdAsc(DRAINABLE,
                PageRequest.of(0, properties.outbox().batchSize()));
        if (batch.isEmpty()) {
            return;
        }
        for (OutboxEvent event : batch) {
            if (properties.outbox().mode() == OutboxMode.KAFKA) {
                publish(event);
            } else {
                // El payload no contiene ningun dato clinico ni ninguna credencial: se arma desde la
                // campana y desde ref_patient, y ninguno de los dos los tiene. Por eso puede registrarse.
                log.info("outbox -> {} evento={} agregado={} particion={} payload={}",
                        event.getEventType(), event.getEventPublicId(), event.getAggregatePublicId(),
                        event.getPartitionKey(), event.getPayload());
                event.markPublished(clock.instant());
            }
        }
        log.debug("El relay drenó {} evento(s) del outbox", batch.size());
    }

    private void publish(OutboxEvent event) {
        try {
            // send() sincrono a proposito: la transaccion que marca el evento publicado debe reflejar
            // si Kafka realmente lo acepto, no un envio en curso que todavia puede fallar.
            kafka.send(event.getEventType(), event.getPartitionKey(), event.getPayload()).get();
            event.markPublished(clock.instant());
        } catch (Exception exception) {
            log.error("No se pudo publicar en Kafka el evento {} ({})", event.getEventPublicId(),
                    event.getEventType(), exception);
            event.markFailed(exception.getMessage());
        }
    }
}
