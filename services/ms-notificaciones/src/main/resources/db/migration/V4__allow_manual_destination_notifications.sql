-- Los envios manuales pueden dirigirse a un numero E.164 aunque aun no exista
-- una referencia local de paciente o usuario. El destino sigue siendo obligatorio.
ALTER TABLE notification DROP CHECK ck_notification_recipient;
