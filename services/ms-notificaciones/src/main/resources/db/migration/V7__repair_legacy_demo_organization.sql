-- ms-notificaciones · notifications_db · repara la organizacion ficticia heredada de identidad.
--
-- La primera cuenta demo de identidad utilizo temporalmente el UUID ficticio
-- 11111111-1111-1111-1111-111111111111 (bug ya corregido en ms-identidad). Los eventos
-- pacientes.paciente publicados mientras ese JWT seguia vigente llegaron con ese organizationId
-- y quedaron asi en patient_reference/conversation_window de este servicio -- son tablas
-- alimentadas por Kafka, no se autocorrigen solas. ms-pacientes ya reparo su propia tabla
-- `patient` para el mismo bug (V3__repair_legacy_demo_organization.sql, servicio ms-pacientes);
-- esta migracion es el equivalente para las tablas locales de notificaciones.
--
-- Efecto practico visto en produccion: un paciente que si le escribio al numero de WhatsApp
-- (conversation_window con expires_at vigente) nunca lograba recibir la notificacion automatica
-- de "cita agendada", porque ConversationWindowStore.isOpen() filtra por organization_id y la
-- cita se crea con el organizationId real (00000000-0000-4000-8000-0000000000a1), que nunca
-- coincidia con el ficticio guardado en estas dos tablas.

SET @legacy_org = '11111111-1111-1111-1111-111111111111';
SET @demo_org = '00000000-0000-4000-8000-0000000000a1';

UPDATE patient_reference
SET organization_id = @demo_org
WHERE organization_id = @legacy_org;

UPDATE conversation_window
SET organization_id = @demo_org
WHERE organization_id = @legacy_org;
