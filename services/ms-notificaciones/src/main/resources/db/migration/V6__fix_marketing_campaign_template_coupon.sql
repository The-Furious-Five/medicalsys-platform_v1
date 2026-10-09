-- ms-notificaciones · notifications_db · corrige la plantilla MARKETING_CAMPAIGN de V5.
--
-- V5 declaraba `couponCode` como variable obligatoria de la plantilla, pero el evento
-- marketing.campana solo trae couponCode cuando la campana tiene una promocion asociada
-- (MarketingEventPublisher.publishCampaign lo omite si campaign.getPromotion() es null). El
-- renderer es estricto por diseno: cualquier variable declarada y ausente aborta el mensaje
-- (TemplateRenderer.render, MISSING_TEMPLATE_VARIABLE). Una campana sin cupon quedaba entonces
-- atascada reintentando para siempre en el listener de Kafka. Se quita couponCode de la
-- plantilla; la unica variable que el evento garantiza siempre es campaignName.

SET @demo_org = '00000000-0000-4000-8000-0000000000a1';

UPDATE message_template
SET body = 'Hola. {{campaignName}}. Te esperamos en tu proxima visita. Si no deseas recibir estos mensajes, respondenos "BAJA" en cualquier momento.',
    variables = JSON_ARRAY('campaignName')
WHERE organization_id = @demo_org AND code = 'MARKETING_CAMPAIGN';
