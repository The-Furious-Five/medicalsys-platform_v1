-- ms-notificaciones · notifications_db · plantillas de WhatsApp para el ciclo de vida de citas.
--
-- Siembra 3 plantillas (UTILITY) y sus reglas de disparo automatico para la organizacion
-- demo (00000000-0000-4000-8000-0000000000a1, la misma que siembra ms-tenencia).
--
-- Las 3 comparten el mismo conjunto de variables para que agenda pueda reutilizar el mismo
-- mapa al publicar el evento: patientName, doctorName, specialty, date, time, office, floor
-- (la de recordatorio agrega cutoffTime). El texto en `body` usa placeholders con nombre
-- ({{patientName}}) porque asi los entiende TemplateRenderer; el texto que Meta aprueba usa
-- placeholders posicionales ({{1}}, {{2}}...) en el mismo orden que la columna `variables`.
--
-- APPOINTMENT_CREATED y APPOINTMENT_RESCHEDULED usan offset_minutes = 0: la notificacion se
-- programa sobre `occurredAt` (ahora mismo) porque el evento de agenda para estos dos casos
-- no debe traer `targetAt`, solo `occurredAt`. APPOINTMENT_REMINDER_DUE es un evento aparte
-- porque su calculo de fecha SI depende de `targetAt` (la fecha de la cita): con
-- offset_minutes = -1500 (25 horas), la notificacion queda SCHEDULED para "cita - 25h" y el
-- NotificationDispatchWorker ya existente la despacha solo cuando llega esa hora. Agenda debe
-- publicar este evento con `targetAt` = fecha/hora de la cita (puede hacerlo en el mismo
-- instante en que agenda la cita; no hace falta un cron aparte).
--
-- `approval_status` arranca en DRAFT: hay que enviar el texto de Meta a revision y, una vez
-- aprobado, actualizar la fila a APPROVED (o via PUT /api/v1/notifications/templates/{id}).
-- La migracion es idempotente (ON DUPLICATE KEY UPDATE) para poder reaplicarla sin duplicar.

SET @demo_org = '00000000-0000-4000-8000-0000000000a1';

-- ------------------------------------------------------------- cita agendada (confirmacion)
INSERT INTO message_template
    (public_id, organization_id, code, channel, category, provider_template_name, language_code,
     subject, body, variables, approval_status, active)
VALUES (
    '00000000-0000-4000-8000-0000000000c1',
    @demo_org,
    'APPOINTMENT_CREATED',
    'WHATSAPP',
    'UTILITY',
    'cita_agendada_confirmacion',
    'es',
    NULL,
    'Cita agendada. Hola {{patientName}}, tu cita con el Dr./Dra. {{doctorName}} ({{specialty}}) fue registrada para el {{date}} a las {{time}} hrs, en el consultorio {{office}}, piso {{floor}}. Te pedimos llegar puntual o unos minutos antes; de lo contrario podriamos necesitar reagendar tu cita. Si tienes algun inconveniente o deseas reagendar, contactanos hasta 24 horas antes. Escribenos si tienes dudas. Gracias por confiar en nosotros.',
    JSON_ARRAY('patientName', 'doctorName', 'specialty', 'date', 'time', 'office', 'floor'),
    'DRAFT',
    TRUE
)
ON DUPLICATE KEY UPDATE
    provider_template_name = VALUES(provider_template_name),
    body                    = VALUES(body),
    variables               = VALUES(variables),
    active                  = VALUES(active);

-- ---------------------------------------------------------------- cita reagendada (aviso)
INSERT INTO message_template
    (public_id, organization_id, code, channel, category, provider_template_name, language_code,
     subject, body, variables, approval_status, active)
VALUES (
    '00000000-0000-4000-8000-0000000000c2',
    @demo_org,
    'APPOINTMENT_RESCHEDULED',
    'WHATSAPP',
    'UTILITY',
    'cita_reagendada_confirmacion',
    'es',
    NULL,
    'Cita reagendada. Hola {{patientName}}, tu cita con el Dr./Dra. {{doctorName}} ({{specialty}}) fue reprogramada. La nueva fecha es el {{date}} a las {{time}} hrs, en el consultorio {{office}}, piso {{floor}}. Por favor confirmanos tu asistencia o escribenos si necesitas hacer otro cambio. Gracias por tu comprension.',
    JSON_ARRAY('patientName', 'doctorName', 'specialty', 'date', 'time', 'office', 'floor'),
    'DRAFT',
    TRUE
)
ON DUPLICATE KEY UPDATE
    provider_template_name = VALUES(provider_template_name),
    body                    = VALUES(body),
    variables               = VALUES(variables),
    active                  = VALUES(active);

-- ------------------------------------------------ recordatorio 25h (ultima hora para cambios)
INSERT INTO message_template
    (public_id, organization_id, code, channel, category, provider_template_name, language_code,
     subject, body, variables, approval_status, active)
VALUES (
    '00000000-0000-4000-8000-0000000000c3',
    @demo_org,
    'APPOINTMENT_REMINDER_25H',
    'WHATSAPP',
    'UTILITY',
    'cita_recordatorio_25h',
    'es',
    NULL,
    'Recordatorio de cita. Hola {{patientName}}, esta es la ultima oportunidad para reagendar tu cita con el Dr./Dra. {{doctorName}} ({{specialty}}): a partir de las {{cutoffTime}} de hoy ya no podremos hacer cambios. Recuerda que tu cita es manana {{date}} a las {{time}} hrs, en el consultorio {{office}}, piso {{floor}}. Si necesitas reagendar o cancelar, escribenos antes de esa hora. Te esperamos puntual, gracias.',
    JSON_ARRAY('patientName', 'doctorName', 'specialty', 'date', 'time', 'office', 'floor', 'cutoffTime'),
    'DRAFT',
    TRUE
)
ON DUPLICATE KEY UPDATE
    provider_template_name = VALUES(provider_template_name),
    body                    = VALUES(body),
    variables               = VALUES(variables),
    active                  = VALUES(active);

-- ------------------------------------------------------------------- reglas de disparo
INSERT INTO notification_rule
    (public_id, organization_id, event_type, channel, template_id, offset_minutes,
     sending_window_from, sending_window_to, active)
SELECT '00000000-0000-4000-8000-0000000000c4', @demo_org, 'APPOINTMENT_CREATED', 'WHATSAPP',
       t.id, 0, NULL, NULL, TRUE
FROM message_template t
WHERE t.organization_id = @demo_org AND t.code = 'APPOINTMENT_CREATED'
ON DUPLICATE KEY UPDATE template_id = VALUES(template_id), active = VALUES(active);

INSERT INTO notification_rule
    (public_id, organization_id, event_type, channel, template_id, offset_minutes,
     sending_window_from, sending_window_to, active)
SELECT '00000000-0000-4000-8000-0000000000c5', @demo_org, 'APPOINTMENT_RESCHEDULED', 'WHATSAPP',
       t.id, 0, NULL, NULL, TRUE
FROM message_template t
WHERE t.organization_id = @demo_org AND t.code = 'APPOINTMENT_RESCHEDULED'
ON DUPLICATE KEY UPDATE template_id = VALUES(template_id), active = VALUES(active);

INSERT INTO notification_rule
    (public_id, organization_id, event_type, channel, template_id, offset_minutes,
     sending_window_from, sending_window_to, active)
SELECT '00000000-0000-4000-8000-0000000000c6', @demo_org, 'APPOINTMENT_REMINDER_DUE', 'WHATSAPP',
       t.id, -1500, NULL, NULL, TRUE
FROM message_template t
WHERE t.organization_id = @demo_org AND t.code = 'APPOINTMENT_REMINDER_25H'
ON DUPLICATE KEY UPDATE template_id = VALUES(template_id), offset_minutes = VALUES(offset_minutes),
    active = VALUES(active);
