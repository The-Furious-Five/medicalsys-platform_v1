package com.medicalsys.notificaciones;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import com.medicalsys.notificaciones.repository.ConversationWindowStore;
import com.medicalsys.notificaciones.repository.MessageTemplateRepository;
import com.medicalsys.notificaciones.repository.NotificationRepository;
import com.medicalsys.notificaciones.repository.NotificationRuleRepository;
import com.medicalsys.notificaciones.repository.WhatsappWebhookStore;
import com.medicalsys.notificaciones.service.MessageTemplateService;
import com.medicalsys.notificaciones.service.NotificationRuleService;
import com.medicalsys.notificaciones.service.ReferenceSyncService;
import com.medicalsys.notificaciones.service.WhatsappWebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.r2dbc.core.DatabaseClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Comprueba la selección de constructores sin necesitar MySQL ni Kafka. */
class SpringWiringTest {

    @Test
    void productionConstructorsCanBeAutowiredBySpring() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ObjectMapper.class, () -> JsonMapper.builder().build());
            context.registerBean(MessageTemplateRepository.class, () -> mock(MessageTemplateRepository.class));
            context.registerBean(NotificationRuleRepository.class, () -> mock(NotificationRuleRepository.class));
            context.registerBean(NotificationRepository.class, () -> mock(NotificationRepository.class));
            context.registerBean(DatabaseClient.class, () -> mock(DatabaseClient.class));
            context.registerBean(ReferenceSyncService.class, () -> mock(ReferenceSyncService.class));
            context.registerBean(WhatsappWebhookStore.class, () -> mock(WhatsappWebhookStore.class));
            context.register(MessageTemplateService.class, NotificationRuleService.class,
                    ConversationWindowStore.class, WhatsappWebhookService.class);
            context.refresh();

            assertNotNull(context.getBean(MessageTemplateService.class));
            assertNotNull(context.getBean(NotificationRuleService.class));
            assertNotNull(context.getBean(ConversationWindowStore.class));
            assertNotNull(context.getBean(WhatsappWebhookService.class));
        }
    }
}
