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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drena el outbox de eventos salientes.
 *
 * <p>La plataforma todavia no tiene broker, asi que este relay es la costura donde ira. Hoy hace lo
 * unico honesto que puede hacer sin uno: registrar el evento y marcarlo publicado
 * ({@code MARKETING_OUTBOX_MODE=LOG}), o dejarlo acumulado para que otro proceso lo lea por la ruta
 * interna ({@code RETAIN}).
 *
 * <p>Lo que importa es que el resto del servicio ya esta escrito contra el outbox y no contra el
 * transporte: el dia que exista un broker, cambia este archivo y ningun caso de uso se entera. Esa
 * es toda la razon de que el patron exista.
 *
 * <p>Los eventos en ERROR se vuelven a tomar junto con los pendientes. Es un reintento simple y sin
 * backoff, suficiente para el MVP; con un broker real, el backoff y la cola de descarte se anaden
 * aqui.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private static final List<OutboxStatus> DRAINABLE = List.of(OutboxStatus.PENDING, OutboxStatus.ERROR);

    private final OutboxEventRepository outbox;
    private final MarketingProperties properties;
    private final Clock clock;

    public OutboxRelay(OutboxEventRepository outbox, MarketingProperties properties, Clock clock) {
        this.outbox = outbox;
        this.properties = properties;
        this.clock = clock;
        if (properties.outbox().mode() == OutboxMode.LOG) {
            log.warn("No hay broker configurado: el relay del outbox marcara los eventos como publicados "
                    + "tras registrarlos en el log (MARKETING_OUTBOX_MODE=LOG). Use RETAIN para conservarlos "
                    + "pendientes y drenarlos desde /internal/v1/eventos/salientes.");
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
            // El payload no contiene ningun dato clinico ni ninguna credencial: se arma desde la
            // campana y desde ref_patient, y ninguno de los dos los tiene. Por eso puede registrarse.
            log.info("outbox -> {} evento={} agregado={} particion={} payload={}",
                    event.getEventType(), event.getEventPublicId(), event.getAggregatePublicId(),
                    event.getPartitionKey(), event.getPayload());
            event.markPublished(clock.instant());
        }
        log.debug("El relay drenó {} evento(s) del outbox", batch.size());
    }
}
