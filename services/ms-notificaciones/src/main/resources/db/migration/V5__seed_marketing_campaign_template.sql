-- ms-notificaciones · notifications_db · plantilla y regla para el evento MARKETING_CAMPAIGN.
--
-- ms-marketing publica marketing.campana cuando lanza, pausa, finaliza o cancela una campana, pero
-- solo el listener de este servicio actua sobre "LANZADA": arma un NotificationDomainEvent por cada
-- destinatario SELECTED de la campana y lo entrega al mismo pipeline generico de reglas que agenda,
-- historial y facturacion. Sin esta plantilla y esta regla, ese pipeline no encuentra con que
-- renderizar el mensaje y la campana no produce ninguna notificacion, igual que le pasaria a
-- cualquier otro evento de dominio sin regla configurada.
--
-- category = MARKETING (no UTILITY): respeta el opt-out propio de marketing en
-- notification_preference_reference, distinto del de citas/resultados/facturas.

SET @demo_org = '00000000-0000-4000-8000-0000000000a1';

INSERT INTO message_template
    (public_id, organization_id, code, channel, category, provider_template_name, language_code,
     subject, body, variables, approval_status, active)
VALUES (
    '00000000-0000-4000-8000-0000000000c7',
    @demo_org,
    'MARKETING_CAMPAIGN',
    'WHATSAPP',
    'MARKETING',
    'campana_marketing',
    'es',
    NULL,
    'Hola. {{campaignName}}. Si te interesa, usa el codigo {{couponCode}} en tu proxima visita. Si no deseas recibir estos mensajes, respondenos "BAJA" en cualquier momento.',
    JSON_ARRAY('campaignName', 'couponCode'),
    'DRAFT',
    TRUE
)
ON DUPLICATE KEY UPDATE
    provider_template_name = VALUES(provider_template_name),
    body                    = VALUES(body),
    variables               = VALUES(variables),
    active                  = VALUES(active);

INSERT INTO notification_rule
    (public_id, organization_id, event_type, channel, template_id, offset_minutes,
     sending_window_from, sending_window_to, active)
SELECT '00000000-0000-4000-8000-0000000000c8', @demo_org, 'MARKETING_CAMPAIGN', 'WHATSAPP',
       t.id, 0, NULL, NULL, TRUE
FROM message_template t
WHERE t.organization_id = @demo_org AND t.code = 'MARKETING_CAMPAIGN'
ON DUPLICATE KEY UPDATE template_id = VALUES(template_id), active = VALUES(active);
