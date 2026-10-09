-- ms-agenda · scheduling_db (PostgreSQL) · agenda de demostracion.
--
-- Datos ficticios. Solo se carga cuando Compose pasa FLYWAY_LOCATIONS con classpath:db/demo.
--
-- Sin esta siembra el modulo de agenda no se puede mirar: el tablero del dia sale vacio, no
-- hay disponibilidad con la que calcular huecos libres y la pantalla de consultorios no
-- muestra ninguna sala. Y como el tablero es lo primero que abre recepcion, era la pantalla
-- mas visible del sistema y la que menos decia.
--
-- Las citas se colocan RELATIVAS a la fecha de ejecucion (CURRENT_DATE +/- n dias) y no en
-- fechas fijas. Una agenda sembrada con fechas absolutas envejece: a las dos semanas el
-- tablero de "hoy" vuelve a estar vacio y hay que resembrar. Asi el dia de hoy siempre tiene
-- consulta.
--
-- Los UUID de paciente, profesional, organizacion y sucursal son los que siembran
-- ms-pacientes, ms-profesionales y ms-tenencia. professional_organization_public_id es el
-- identificador del VINCULO, no el del profesional: es lo que la disponibilidad necesita
-- para saber en que sede se atiende.
--
-- Idempotencia: se borra la agenda sembrada por este archivo -reconocible por el codigo de
-- recurso y por el rango de fechas- y se vuelve a insertar, para que reiniciar el contenedor
-- reposicione las citas sobre la semana en curso.

-- ------------------------------------------------------------------- limpieza
DELETE FROM appointment_status_history
 WHERE appointment_id IN (
     SELECT id FROM appointment
      WHERE organization_public_id = '00000000-0000-4000-8000-0000000000a1'
        AND created_by_user_public_id = '00000000-0000-4000-8000-000000000902');

DELETE FROM resource_reservation
 WHERE organization_public_id = '00000000-0000-4000-8000-0000000000a1'
   AND created_by_user_public_id = '00000000-0000-4000-8000-000000000902';

DELETE FROM appointment
 WHERE organization_public_id = '00000000-0000-4000-8000-0000000000a1'
   AND created_by_user_public_id = '00000000-0000-4000-8000-000000000902';

DELETE FROM schedule_block
 WHERE organization_public_id = '00000000-0000-4000-8000-0000000000a1'
   AND created_by_user_public_id = '00000000-0000-4000-8000-000000000902';

DELETE FROM schedule_availability
 WHERE professional_organization_public_id IN (
     '60000000-0000-4000-8000-000000000001',
     '60000000-0000-4000-8000-000000000002',
     '60000000-0000-4000-8000-000000000003',
     '60000000-0000-4000-8000-000000000004',
     '60000000-0000-4000-8000-000000000006',
     '60000000-0000-4000-8000-000000000008');

DELETE FROM physical_resource
 WHERE organization_public_id = '00000000-0000-4000-8000-0000000000a1'
   AND code IN ('C-101','C-102','C-103','C-201','RX-01','ECO-01','PROC-01','LAB-01');

-- ---------------------------------------------------------------- consultorios
INSERT INTO physical_resource
    (public_id, organization_public_id, branch_public_id, code, name, type, floor,
     people_capacity, preparation_minutes, requires_sterilization, equipment, status)
VALUES
('70000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-0000000000a1',
 '00000000-0000-4000-8000-0000000000a2', 'C-101', 'Consultorio 101 - Medicina Interna',
 'OFFICE', 'Planta baja', 3, 5, FALSE,
 '{"camilla": true, "tensiometro": "digital", "negatoscopio": true}'::jsonb, 'AVAILABLE'),

('70000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-0000000000a1',
 '00000000-0000-4000-8000-0000000000a2', 'C-102', 'Consultorio 102 - Pediatria',
 'OFFICE', 'Planta baja', 4, 5, FALSE,
 '{"camilla_pediatrica": true, "balanza_infantil": true, "tallimetro": true}'::jsonb, 'AVAILABLE'),

('70000000-0000-4000-8000-000000000003', '00000000-0000-4000-8000-0000000000a1',
 '00000000-0000-4000-8000-0000000000a2', 'C-103', 'Consultorio 103 - Ginecologia',
 'OFFICE', 'Primer piso', 2, 10, TRUE,
 '{"camilla_ginecologica": true, "lampara_cuello_cisne": true, "ecografo_portatil": true}'::jsonb, 'AVAILABLE'),

('70000000-0000-4000-8000-000000000004', '00000000-0000-4000-8000-0000000000a1',
 '00000000-0000-4000-8000-0000000000a3', 'C-201', 'Consultorio 201 - Cardiologia',
 'OFFICE', 'Segundo piso', 2, 5, FALSE,
 '{"electrocardiografo": "12 derivaciones", "camilla": true}'::jsonb, 'AVAILABLE'),

('70000000-0000-4000-8000-000000000005', '00000000-0000-4000-8000-0000000000a1',
 '00000000-0000-4000-8000-0000000000a2', 'RX-01', 'Sala de rayos X',
 'IMAGING_EQUIPMENT', 'Subsuelo', 1, 15, FALSE,
 '{"equipo": "radiologia digital directa", "chaleco_plomado": 3}'::jsonb, 'AVAILABLE'),

('70000000-0000-4000-8000-000000000006', '00000000-0000-4000-8000-0000000000a1',
 '00000000-0000-4000-8000-0000000000a2', 'ECO-01', 'Sala de ecografia',
 'IMAGING_EQUIPMENT', 'Primer piso', 2, 10, FALSE,
 '{"ecografo": "multiproposito", "transductores": ["convexo", "lineal", "endocavitario"]}'::jsonb, 'AVAILABLE'),

('70000000-0000-4000-8000-000000000007', '00000000-0000-4000-8000-0000000000a1',
 '00000000-0000-4000-8000-0000000000a2', 'PROC-01', 'Sala de procedimientos menores',
 'PROCEDURE_ROOM', 'Primer piso', 3, 20, TRUE,
 '{"electrobisturi": true, "carro_de_paro": true}'::jsonb, 'AVAILABLE'),

-- Fuera de servicio: la pantalla de consultorios tiene que poder mostrar algo que no sea
-- AVAILABLE, porque de eso depende que se vea el filtro por estado.
('70000000-0000-4000-8000-000000000008', '00000000-0000-4000-8000-0000000000a1',
 '00000000-0000-4000-8000-0000000000a4', 'LAB-01', 'Toma de muestras - El Alto',
 'LABORATORY', 'Planta baja', 2, 0, FALSE,
 '{"centrifuga": true, "refrigerador_muestras": true}'::jsonb, 'UNDER_MAINTENANCE');

-- --------------------------------------------------------------- disponibilidad
-- day_of_week 1 = lunes ... 7 = domingo. Las horas son locales de America/La_Paz; el servicio
-- las traduce a instantes con SCHEDULING_ZONE.
INSERT INTO schedule_availability
    (professional_organization_public_id, day_of_week, start_time, end_time,
     slot_duration_minutes, simultaneous_capacity, modality, preferred_resource_id,
     valid_from, valid_until, active)
SELECT v.link::uuid, v.dow, v.starts::time, v.ends::time, v.slot, 1, v.modality,
       r.id, CURRENT_DATE - INTERVAL '6 months', NULL, TRUE
FROM (VALUES
    -- Dr. Salazar, medicina interna, sede Sopocachi: mananas de lunes a viernes.
    ('60000000-0000-4000-8000-000000000001', 1, '08:00', '12:00', 30, 'IN_PERSON', 'C-101'),
    ('60000000-0000-4000-8000-000000000001', 2, '08:00', '12:00', 30, 'IN_PERSON', 'C-101'),
    ('60000000-0000-4000-8000-000000000001', 3, '08:00', '12:00', 30, 'IN_PERSON', 'C-101'),
    ('60000000-0000-4000-8000-000000000001', 4, '08:00', '12:00', 30, 'IN_PERSON', 'C-101'),
    ('60000000-0000-4000-8000-000000000001', 5, '08:00', '12:00', 30, 'IN_PERSON', 'C-101'),
    -- El mismo medico, tardes de teleconsulta en la sede Miraflores (vinculo distinto).
    ('60000000-0000-4000-8000-000000000008', 2, '15:00', '18:00', 20, 'TELECONSULTATION', 'C-201'),
    ('60000000-0000-4000-8000-000000000008', 4, '15:00', '18:00', 20, 'TELECONSULTATION', 'C-201'),
    -- Dra. Nunez, pediatria: tardes.
    ('60000000-0000-4000-8000-000000000002', 1, '14:00', '19:00', 30, 'IN_PERSON', 'C-102'),
    ('60000000-0000-4000-8000-000000000002', 3, '14:00', '19:00', 30, 'IN_PERSON', 'C-102'),
    ('60000000-0000-4000-8000-000000000002', 5, '14:00', '19:00', 30, 'IN_PERSON', 'C-102'),
    -- Dra. Mendoza, ginecologia: turnos largos por la duracion de la consulta.
    ('60000000-0000-4000-8000-000000000003', 2, '09:00', '13:00', 40, 'IN_PERSON', 'C-103'),
    ('60000000-0000-4000-8000-000000000003', 4, '09:00', '13:00', 40, 'IN_PERSON', 'C-103'),
    -- Dr. Arispe, cardiologia, sede Miraflores: dos manianas.
    ('60000000-0000-4000-8000-000000000004', 3, '08:30', '12:30', 40, 'IN_PERSON', 'C-201'),
    ('60000000-0000-4000-8000-000000000004', 5, '08:30', '12:30', 40, 'IN_PERSON', 'C-201'),
    -- Lic. Terceros, nutricion, sede Miraflores.
    ('60000000-0000-4000-8000-000000000006', 1, '10:00', '13:00', 45, 'BOTH', 'C-201'),
    ('60000000-0000-4000-8000-000000000006', 4, '10:00', '13:00', 45, 'BOTH', 'C-201')
) AS v(link, dow, starts, ends, slot, modality, resource_code)
LEFT JOIN physical_resource r
       ON r.code = v.resource_code
      AND r.organization_public_id = '00000000-0000-4000-8000-0000000000a1';

-- --------------------------------------------------------------------- bloqueos
-- Feriado nacional y un mantenimiento del equipo de rayos X. Sin bloqueos, el calculo de
-- huecos libres nunca demuestra que sabe descontarlos.
INSERT INTO schedule_block
    (organization_public_id, health_professional_public_id, physical_resource_id,
     branch_public_id, type, starts_at, ends_at, reason, created_by_user_public_id)
SELECT '00000000-0000-4000-8000-0000000000a1', '50000000-0000-4000-8000-000000000001', NULL,
       '00000000-0000-4000-8000-0000000000a2', 'TRAINING',
       ((CURRENT_DATE + 9) + TIME '08:00') AT TIME ZONE 'America/La_Paz',
       ((CURRENT_DATE + 9) + TIME '12:00') AT TIME ZONE 'America/La_Paz',
       'Curso de actualizacion en manejo de diabetes tipo 2',
       '00000000-0000-4000-8000-000000000902';

INSERT INTO schedule_block
    (organization_public_id, health_professional_public_id, physical_resource_id,
     branch_public_id, type, starts_at, ends_at, reason, created_by_user_public_id)
SELECT '00000000-0000-4000-8000-0000000000a1', NULL, r.id,
       '00000000-0000-4000-8000-0000000000a2', 'MAINTENANCE',
       ((CURRENT_DATE + 4) + TIME '13:00') AT TIME ZONE 'America/La_Paz',
       ((CURRENT_DATE + 4) + TIME '18:00') AT TIME ZONE 'America/La_Paz',
       'Calibracion anual del equipo de radiologia',
       '00000000-0000-4000-8000-000000000902'
FROM physical_resource r
WHERE r.code = 'RX-01' AND r.organization_public_id = '00000000-0000-4000-8000-0000000000a1';

-- ----------------------------------------------------------------------- citas
-- El tablero necesita ver el ciclo completo, no solo citas futuras: una atendida, una que
-- no se presento, una cancelada, una en sala de espera y varias por venir.
INSERT INTO appointment
    (public_id, organization_public_id, branch_public_id, patient_public_id,
     health_professional_public_id, service_offering_public_id, starts_at, ends_at,
     modality, status, channel_origin, reason_for_visit, requires_consent,
     estimated_price_bob, teleconsultation_link, created_by_user_public_id,
     confirmed_at, arrived_at, care_started_at, care_ended_at,
     cancelled_at, cancelled_by_user_public_id, cancellation_reason)
SELECT v.public_id::uuid,
       '00000000-0000-4000-8000-0000000000a1',
       v.branch::uuid, v.patient::uuid, v.professional::uuid, v.offering::uuid,
       ((CURRENT_DATE + v.day_offset) + v.start_time::time)
           AT TIME ZONE 'America/La_Paz',
       ((CURRENT_DATE + v.day_offset) + v.start_time::time
           + (v.minutes || ' minutes')::interval) AT TIME ZONE 'America/La_Paz',
       v.modality, v.status, v.channel, v.reason, v.consent, v.price, v.link,
       '00000000-0000-4000-8000-000000000902',
       CASE WHEN v.status IN ('CONFIRMED','WAITING','IN_PROGRESS','ATTENDED','NO_SHOW')
            THEN ((CURRENT_DATE + v.day_offset) - INTERVAL '1 day')
                 AT TIME ZONE 'America/La_Paz' END,
       CASE WHEN v.status IN ('WAITING','IN_PROGRESS','ATTENDED')
            THEN ((CURRENT_DATE + v.day_offset) + v.start_time::time
                 - INTERVAL '12 minutes') AT TIME ZONE 'America/La_Paz' END,
       CASE WHEN v.status IN ('IN_PROGRESS','ATTENDED')
            THEN ((CURRENT_DATE + v.day_offset) + v.start_time::time)
                 AT TIME ZONE 'America/La_Paz' END,
       CASE WHEN v.status = 'ATTENDED'
            THEN ((CURRENT_DATE + v.day_offset) + v.start_time::time
                 + (v.minutes || ' minutes')::interval) AT TIME ZONE 'America/La_Paz' END,
       CASE WHEN v.status = 'CANCELLED'
            THEN ((CURRENT_DATE + v.day_offset) - INTERVAL '2 days')
                 AT TIME ZONE 'America/La_Paz' END,
       CASE WHEN v.status = 'CANCELLED' THEN '00000000-0000-4000-8000-000000000902'::uuid END,
       CASE WHEN v.status = 'CANCELLED' THEN v.reason_cancel END
FROM (VALUES
    -- ---- pasado
    ('80000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-0000000000a2',
     '10000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000001',
     '00000000-0000-4000-8000-000000000501', -42, '09:00', 30, 'IN_PERSON', 'ATTENDED',
     'RECEPTION', 'Control anual de salud', FALSE, 150.00, NULL, NULL),

    ('80000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-0000000000a3',
     '10000000-0000-4000-8000-000000000004', '50000000-0000-4000-8000-000000000004',
     '00000000-0000-4000-8000-000000000506', -21, '09:10', 40, 'IN_PERSON', 'ATTENDED',
     'PHONE', 'Control trimestral de hipertension', FALSE, 320.00, NULL, NULL),

    ('80000000-0000-4000-8000-000000000003', '00000000-0000-4000-8000-0000000000a2',
     '10000000-0000-4000-8000-000000000003', '50000000-0000-4000-8000-000000000002',
     '00000000-0000-4000-8000-000000000504', -11, '15:00', 30, 'IN_PERSON', 'ATTENDED',
     'WHATSAPP', 'Fiebre y tos de cuatro dias', FALSE, 200.00, NULL, NULL),

    ('80000000-0000-4000-8000-000000000004', '00000000-0000-4000-8000-0000000000a2',
     '10000000-0000-4000-8000-000000000005', '50000000-0000-4000-8000-000000000001',
     '00000000-0000-4000-8000-000000000501', -6, '10:30', 30, 'IN_PERSON', 'ATTENDED',
     'RECEPTION', 'Dolor lumbar de tres semanas', FALSE, 150.00, NULL, NULL),

    ('80000000-0000-4000-8000-000000000005', '00000000-0000-4000-8000-0000000000a2',
     '10000000-0000-4000-8000-00000000000a', '50000000-0000-4000-8000-000000000003',
     '00000000-0000-4000-8000-000000000505', -8, '09:40', 40, 'IN_PERSON', 'NO_SHOW',
     'WEB_PORTAL', 'Control ginecologico anual', TRUE, 280.00, NULL, NULL),

    ('80000000-0000-4000-8000-000000000006', '00000000-0000-4000-8000-0000000000a2',
     '10000000-0000-4000-8000-00000000000c', '50000000-0000-4000-8000-000000000002',
     '00000000-0000-4000-8000-000000000504', -3, '16:00', 30, 'IN_PERSON', 'CANCELLED',
     'MOBILE_APP', 'Consulta pediatrica', FALSE, 200.00, NULL,
     'El paciente aviso que no podra asistir por viaje'),

    -- ---- hoy: es lo que abre recepcion al empezar el dia
    ('80000000-0000-4000-8000-000000000007', '00000000-0000-4000-8000-0000000000a2',
     '10000000-0000-4000-8000-000000000009', '50000000-0000-4000-8000-000000000001',
     '00000000-0000-4000-8000-000000000508', 0, '08:00', 20, 'IN_PERSON', 'ATTENDED',
     'RECEPTION', 'Control de resultados de laboratorio', FALSE, 80.00, NULL, NULL),

    ('80000000-0000-4000-8000-000000000008', '00000000-0000-4000-8000-0000000000a2',
     '10000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000001',
     '00000000-0000-4000-8000-000000000508', 0, '08:30', 20, 'IN_PERSON', 'WAITING',
     'RECEPTION', 'Revision de perfil de laboratorio', FALSE, 80.00, NULL, NULL),

    ('80000000-0000-4000-8000-000000000009', '00000000-0000-4000-8000-0000000000a2',
     '10000000-0000-4000-8000-00000000000b', '50000000-0000-4000-8000-000000000001',
     '00000000-0000-4000-8000-000000000501', 0, '09:00', 30, 'IN_PERSON', 'CONFIRMED',
     'PHONE', 'Primera consulta por dolor articular', FALSE, 150.00, NULL, NULL),

    ('80000000-0000-4000-8000-00000000000a', '00000000-0000-4000-8000-0000000000a2',
     '10000000-0000-4000-8000-00000000000d', '50000000-0000-4000-8000-000000000001',
     '00000000-0000-4000-8000-000000000501', 0, '10:00', 30, 'IN_PERSON', 'REQUESTED',
     'WEB_PORTAL', 'Certificado medico laboral', FALSE, 150.00, NULL, NULL),

    ('80000000-0000-4000-8000-00000000000b', '00000000-0000-4000-8000-0000000000a2',
     '10000000-0000-4000-8000-000000000003', '50000000-0000-4000-8000-000000000002',
     '00000000-0000-4000-8000-000000000504', 0, '14:30', 30, 'IN_PERSON', 'CONFIRMED',
     'WHATSAPP', 'Control post tratamiento', FALSE, 200.00, NULL, NULL),

    -- ---- proximos dias
    ('80000000-0000-4000-8000-00000000000c', '00000000-0000-4000-8000-0000000000a3',
     '10000000-0000-4000-8000-000000000004', '50000000-0000-4000-8000-000000000004',
     '00000000-0000-4000-8000-00000000050b', 2, '09:10', 20, 'IN_PERSON', 'CONFIRMED',
     'RECEPTION', 'Electrocardiograma solicitado en control', FALSE, 150.00, NULL, NULL),

    ('80000000-0000-4000-8000-00000000000d', '00000000-0000-4000-8000-0000000000a2',
     '10000000-0000-4000-8000-00000000000e', '50000000-0000-4000-8000-000000000003',
     '00000000-0000-4000-8000-000000000505', 3, '09:00', 40, 'IN_PERSON', 'REQUESTED',
     'WEB_PORTAL', 'Consulta ginecologica de rutina', TRUE, 280.00, NULL, NULL),

    ('80000000-0000-4000-8000-00000000000e', '00000000-0000-4000-8000-0000000000a3',
     '10000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000001',
     '00000000-0000-4000-8000-00000000050c', 4, '15:00', 20, 'TELECONSULTATION', 'CONFIRMED',
     'MOBILE_APP', 'Teleconsulta de seguimiento', FALSE, 120.00,
     'https://teleconsulta.sangabriel.clinica.invalid/sala/7f3a91', NULL),

    ('80000000-0000-4000-8000-00000000000f', '00000000-0000-4000-8000-0000000000a2',
     '10000000-0000-4000-8000-000000000005', '50000000-0000-4000-8000-000000000001',
     '00000000-0000-4000-8000-000000000508', 5, '11:00', 20, 'IN_PERSON', 'REQUESTED',
     'PHONE', 'Control con resultado de radiografia', FALSE, 80.00, NULL, NULL),

    ('80000000-0000-4000-8000-000000000010', '00000000-0000-4000-8000-0000000000a3',
     '10000000-0000-4000-8000-00000000000b', '50000000-0000-4000-8000-000000000006',
     '00000000-0000-4000-8000-000000000501', 7, '10:00', 45, 'IN_PERSON', 'REQUESTED',
     'RECEPTION', 'Valoracion nutricional inicial', FALSE, 150.00, NULL, NULL)
) AS v(public_id, branch, patient, professional, offering, day_offset, start_time, minutes,
       modality, status, channel, reason, consent, price, link, reason_cancel);

-- ------------------------------------------------------ historial de estados
-- Toda cita nace REQUESTED; el resto de estados se registra como transicion. El tablero
-- muestra este rastro al abrir una cita.
INSERT INTO appointment_status_history
    (appointment_id, previous_status, new_status, occurred_at, user_account_public_id, origin, reason)
SELECT a.id, NULL, 'REQUESTED', a.created_at,
       '00000000-0000-4000-8000-000000000902', 'USER', NULL
FROM appointment a
WHERE a.created_by_user_public_id = '00000000-0000-4000-8000-000000000902';

INSERT INTO appointment_status_history
    (appointment_id, previous_status, new_status, occurred_at, user_account_public_id, origin, reason)
SELECT a.id, 'REQUESTED', a.status, COALESCE(a.confirmed_at, a.cancelled_at, a.created_at),
       '00000000-0000-4000-8000-000000000902', 'USER', a.cancellation_reason
FROM appointment a
WHERE a.created_by_user_public_id = '00000000-0000-4000-8000-000000000902'
  AND a.status <> 'REQUESTED';

-- ------------------------------------------------------- reservas de recurso
-- Las citas de imagen ocupan la sala, no solo al profesional. Es lo que hace que la sala de
-- rayos no se pueda reservar dos veces a la misma hora.
INSERT INTO resource_reservation
    (organization_public_id, physical_resource_id, appointment_id, starts_at, ends_at,
     purpose, status, created_by_user_public_id)
SELECT '00000000-0000-4000-8000-0000000000a1', r.id, a.id, a.starts_at, a.ends_at,
       'Electrocardiograma programado', 'RESERVED',
       '00000000-0000-4000-8000-000000000902'
FROM appointment a
JOIN physical_resource r
  ON r.code = 'C-201' AND r.organization_public_id = '00000000-0000-4000-8000-0000000000a1'
WHERE a.public_id = '80000000-0000-4000-8000-00000000000c';
