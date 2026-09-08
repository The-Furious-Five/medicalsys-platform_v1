-- ms-pacientes · patients_db · padron de demostracion.
--
-- Datos exclusivamente ficticios. Esta ubicacion solo se habilita desde compose.yaml
-- mediante FLYWAY_LOCATIONS, de modo que un despliegue real nunca la carga.
--
-- Por que son verosimiles y no "Paciente Uno": el padron es lo que se ve en casi todas
-- las pantallas -la tabla de pacientes, el selector al citar, la cabecera del expediente,
-- el destinatario de la factura- y con nombres de relleno no se puede juzgar si la
-- interfaz aguanta apellidos largos, un CI con complemento, un menor con tutor o una
-- persona sin correo. Aqui hay catorce personas con la distribucion de edad, sexo y
-- procedencia de un policlinico pacenio.
--
-- Que sigue siendo ficticio, deliberadamente:
--   · los numeros de CI no corresponden a ninguna cedula emitida;
--   · los correos usan .invalid (RFC 2606), un TLD reservado que no resuelve;
--   · las direcciones cifradas son bytes de relleno, no texto cifrado real.
--
-- Idempotencia: la siembra borra primero sus propias filas -las de public_id fijo del
-- rango 10000000-0000-4000-8000-*- y las vuelve a insertar. Asi un cambio en este
-- archivo se refleja al reiniciar, cosa que INSERT IGNORE no permitia. Los pacientes
-- creados durante una demostracion llevan UUID aleatorio y no se tocan.

-- ------------------------------------------------------------------- limpieza
-- El menor apunta a su tutora por FK, asi que se borra antes que el resto.
DELETE FROM patient_merge
 WHERE surviving_patient_id IN (SELECT id FROM (
        SELECT id FROM patient WHERE HEX(public_id) LIKE '10000000000040008000%') t)
    OR merged_patient_id IN (SELECT id FROM (
        SELECT id FROM patient WHERE HEX(public_id) LIKE '10000000000040008000%') t);

UPDATE patient SET guardian_patient_id = NULL
 WHERE HEX(public_id) LIKE '10000000000040008000%';

DELETE FROM patient WHERE HEX(public_id) LIKE '10000000000040008000%';

-- ------------------------------------------------------------------ pacientes
INSERT INTO patient (
    public_id, user_account_public_id, document_type, document_number,
    document_complement, issued_department, given_names, paternal_surname,
    maternal_surname, birth_date, sex, marital_status, blood_type, occupation,
    phone_e164, alternate_phone, email, encrypted_address, city, department,
    emergency_contact_name, emergency_contact_phone, emergency_contact_relationship,
    encryption_key_public_id, data_consent_at, status, death_date
) VALUES
-- Paciente con cuenta de usuario: la unica que puede entrar al portal.
(UUID_TO_BIN('10000000-0000-4000-8000-000000000001'),
 UUID_TO_BIN('20000000-0000-4000-8000-000000000001'),
 'CI', '4829173', '', 'LA_PAZ',
 'Maria Elena', 'Quispe', 'Mamani', '1988-03-14',
 'F', 'MARRIED', 'O+', 'Arquitecta',
 '+59171548392', '+59122412876', 'melena.quispe@correo.invalid',
 UNHEX('9F2C41A8B7D0E35614F8'), 'La Paz', 'LA_PAZ',
 'Ruben Quispe Colque', '+59171548400', 'Esposo',
 UUID_TO_BIN('00000000-0000-4000-8000-0000000000c4'),
 '2025-11-18 09:24:00', 'ACTIVE', NULL),

-- Homonimia de documento resuelta por complemento: mismo CI, sufijo distinto.
-- Es el caso que rompe cualquier busqueda que ignore document_complement.
(UUID_TO_BIN('10000000-0000-4000-8000-000000000002'),
 NULL, 'CI', '4829173', '1K', 'LA_PAZ',
 'Ana Beatriz', 'Choque', 'Villarroel', '1994-07-22',
 'F', 'SINGLE', 'A+', 'Ingeniera industrial',
 '+59176203841', NULL, 'ana.choque@correo.invalid',
 NULL, 'El Alto', 'LA_PAZ',
 'Gladys Villarroel Aruquipa', '+59176203900', 'Madre',
 NULL, '2026-01-09 15:40:00', 'ACTIVE', NULL),

-- Apellido compuesto y nombre largo: sirve para ver si la tabla trunca bien.
(UUID_TO_BIN('10000000-0000-4000-8000-000000000004'),
 NULL, 'CI', '3157094', '', 'COCHABAMBA',
 'Juan Carlos', 'Rodriguez', 'del Castillo', '1976-11-05',
 'M', 'DIVORCED', 'B+', 'Contador publico',
 '+59170914725', '+59144251037', 'jc.rodriguez@correo.invalid',
 UNHEX('3B77E0142FA95C8D61B4'), 'Cochabamba', 'COCHABAMBA',
 'Silvia del Castillo Rojas', '+59170914800', 'Hermana',
 UUID_TO_BIN('00000000-0000-4000-8000-0000000000c4'),
 '2026-02-03 11:12:00', 'ACTIVE', NULL),

-- Adulto mayor sin correo ni telefono propio: contacto solo por su hija.
(UUID_TO_BIN('10000000-0000-4000-8000-000000000005'),
 NULL, 'CI', '1204583', '', 'ORURO',
 'Gregorio', 'Mamani', 'Condori', '1948-06-30',
 'M', 'WIDOWED', 'O-', 'Jubilado (ex minero)',
 NULL, '+59172609154', NULL,
 NULL, 'Oruro', 'ORURO',
 'Felicidad Mamani Cruz', '+59172609154', 'Hija',
 NULL, '2025-09-27 10:05:00', 'ACTIVE', NULL),

-- Registro cerrado por fallecimiento: la ficha debe abrirse en solo lectura.
(UUID_TO_BIN('10000000-0000-4000-8000-000000000006'),
 NULL, 'CI', '0895317', '', 'POTOSI',
 'Rosa Angelica', 'Flores', 'Ticona', '1939-02-11',
 'F', 'WIDOWED', 'AB+', 'Ama de casa',
 NULL, NULL, NULL,
 NULL, 'Potosi', 'POTOSI',
 'Marcelo Flores Ticona', '+59167412298', 'Hijo',
 NULL, '2024-04-16 08:50:00', 'DECEASED', '2026-03-22'),

-- Extranjero con pasaporte: no tiene CI ni departamento de emision boliviano.
(UUID_TO_BIN('10000000-0000-4000-8000-000000000007'),
 NULL, 'PASSPORT', 'AB2947315', '', 'FOREIGN',
 'Alexandre', 'Moreira', 'Da Silva', '1985-09-19',
 'M', 'MARRIED', 'A-', 'Consultor de proyectos',
 '+59178035162', NULL, 'a.moreira@correo.invalid',
 NULL, 'Santa Cruz de la Sierra', 'SANTA_CRUZ',
 'Carolina Moreira Lima', '+59178035200', 'Esposa',
 NULL, '2026-05-14 16:30:00', 'ACTIVE', NULL),

-- Paciente inactivo: se mudo y ya no se atiende en el centro.
(UUID_TO_BIN('10000000-0000-4000-8000-000000000008'),
 NULL, 'CI', '6712408', '', 'SANTA_CRUZ',
 'Vanessa', 'Aguilera', 'Suarez', '1991-12-08',
 'F', 'DOMESTIC_PARTNERSHIP', 'B-', 'Disenadora grafica',
 '+59173289056', NULL, 'v.aguilera@correo.invalid',
 NULL, 'Santa Cruz de la Sierra', 'SANTA_CRUZ',
 NULL, NULL, NULL,
 NULL, '2025-07-02 13:15:00', 'INACTIVE', NULL),

(UUID_TO_BIN('10000000-0000-4000-8000-000000000009'),
 NULL, 'CI', '5093862', '', 'LA_PAZ',
 'Freddy Ramiro', 'Poma', 'Huanca', '1969-01-27',
 'M', 'MARRIED', 'O+', 'Chofer de transporte publico',
 '+59169427310', NULL, NULL,
 UNHEX('C41D9082E6B37F5A2C90'), 'El Alto', 'LA_PAZ',
 'Nieves Huanca Apaza', '+59169427388', 'Esposa',
 UUID_TO_BIN('00000000-0000-4000-8000-0000000000c4'),
 '2025-10-30 07:45:00', 'ACTIVE', NULL),

(UUID_TO_BIN('10000000-0000-4000-8000-00000000000a'),
 NULL, 'CI', '7845201', '', 'LA_PAZ',
 'Camila Andrea', 'Vargas', 'Nogales', '2001-05-03',
 'F', 'SINGLE', 'A+', 'Estudiante de enfermeria',
 '+59177103654', NULL, 'camila.vargas@correo.invalid',
 NULL, 'La Paz', 'LA_PAZ',
 'Patricia Nogales Ayala', '+59177103700', 'Madre',
 NULL, '2026-06-21 18:20:00', 'ACTIVE', NULL),

(UUID_TO_BIN('10000000-0000-4000-8000-00000000000b'),
 NULL, 'CI', '2946130', '', 'CHUQUISACA',
 'Hernan', 'Zerda', 'Paredes', '1958-08-16',
 'M', 'MARRIED', 'AB-', 'Docente universitario jubilado',
 '+59174520918', '+59146453027', 'h.zerda@correo.invalid',
 NULL, 'Sucre', 'CHUQUISACA',
 'Marta Paredes Solis', '+59174521000', 'Esposa',
 NULL, '2025-12-11 09:00:00', 'ACTIVE', NULL),

(UUID_TO_BIN('10000000-0000-4000-8000-00000000000c'),
 NULL, 'CI', '8103574', '', 'LA_PAZ',
 'Daniela', 'Ticona', 'Alarcon', '1997-10-29',
 'F', 'SINGLE', 'O+', 'Comerciante',
 '+59175640213', NULL, 'daniela.ticona@correo.invalid',
 NULL, 'La Paz', 'LA_PAZ',
 'Jose Ticona Mamani', '+59175640300', 'Padre',
 NULL, '2026-04-08 12:35:00', 'ACTIVE', NULL),

(UUID_TO_BIN('10000000-0000-4000-8000-00000000000d'),
 NULL, 'FOREIGN_ID_CARD', 'PE-40518273', '', 'FOREIGN',
 'Lucia', 'Ramos', 'Huaman', '1983-04-12',
 'F', 'MARRIED', 'B+', 'Enfermera',
 '+59179304182', NULL, 'lucia.ramos@correo.invalid',
 NULL, 'La Paz', 'LA_PAZ',
 'Cesar Ramos Quiroz', '+59179304200', 'Esposo',
 NULL, '2026-07-30 08:10:00', 'ACTIVE', NULL),

(UUID_TO_BIN('10000000-0000-4000-8000-00000000000e'),
 NULL, 'CI', '9250417', '', 'TARIJA',
 'Sebastian', 'Ortiz', 'Baldivieso', '1979-02-24',
 'M', 'SINGLE', 'A+', 'Ingeniero de sistemas',
 '+59170582934', NULL, 's.ortiz@correo.invalid',
 NULL, 'Tarija', 'TARIJA',
 NULL, NULL, NULL,
 NULL, '2026-08-19 17:05:00', 'ACTIVE', NULL);

-- ------------------------------------------------------------ menor con tutora
-- El certificado de nacimiento es el documento del menor hasta que tramita su CI, y el
-- vinculo con la tutora es una FK dentro de esta misma tabla: quien autoriza un
-- procedimiento sobre el nino es una persona del padron, no un nombre suelto.
INSERT INTO patient (
    public_id, document_type, document_number, document_complement, issued_department,
    given_names, paternal_surname, maternal_surname, birth_date, sex, marital_status,
    blood_type, phone_e164, city, department, emergency_contact_name,
    emergency_contact_phone, emergency_contact_relationship, guardian_patient_id, status
)
SELECT
    UUID_TO_BIN('10000000-0000-4000-8000-000000000003'),
    'BIRTH_CERTIFICATE', 'LP-2017-0483920', '', 'LA_PAZ',
    'Mateo Sebastian', 'Quispe', 'Iturralde', '2017-09-08',
    'M', 'UNDECLARED', 'O+', '+59171548392', 'La Paz', 'LA_PAZ',
    'Maria Elena Quispe Mamani', '+59171548392', 'Madre',
    tutora.id, 'ACTIVE'
FROM patient tutora
WHERE tutora.public_id = UUID_TO_BIN('10000000-0000-4000-8000-000000000001');

-- ------------------------------------------------ preferencias de notificacion
-- Tres consentimientos distintos sobre el mismo eje: quien acepta el recordatorio
-- clinico por WhatsApp, quien solo acepta lo administrativo por correo y quien lo
-- rechazo. ms-marketing respeta este opt-out y no incluye a esas personas en campanas.
INSERT INTO notification_preference
    (patient_id, channel, category, opt_in, consent_source, opt_in_at, opt_out_at)
SELECT p.id, v.channel, v.category, v.opt_in, v.consent_source, v.opt_in_at, v.opt_out_at
FROM patient p
JOIN (
    SELECT '10000000-0000-4000-8000-000000000001' AS pid, 'WHATSAPP' AS channel,
           'CLINICAL' AS category, TRUE AS opt_in, 'RECEPTION' AS consent_source,
           '2025-11-18 09:26:00' AS opt_in_at,
           NULL AS opt_out_at
    UNION ALL SELECT '10000000-0000-4000-8000-000000000001', 'EMAIL', 'ADMINISTRATIVE',
           TRUE, 'WEB_FORM', '2025-11-18 09:27:00', NULL
    UNION ALL SELECT '10000000-0000-4000-8000-000000000002', 'WHATSAPP', 'ADMINISTRATIVE',
           FALSE, 'RECEPTION', NULL, '2026-01-09 15:45:00'
    UNION ALL SELECT '10000000-0000-4000-8000-000000000004', 'WHATSAPP', 'CLINICAL',
           TRUE, 'RECEPTION', '2026-02-03 11:14:00', NULL
    UNION ALL SELECT '10000000-0000-4000-8000-000000000009', 'SMS', 'CLINICAL',
           TRUE, 'RECEPTION', '2025-10-30 07:48:00', NULL
    UNION ALL SELECT '10000000-0000-4000-8000-00000000000a', 'EMAIL', 'ADMINISTRATIVE',
           TRUE, 'WEB_FORM', '2026-06-21 18:22:00', NULL
    UNION ALL SELECT '10000000-0000-4000-8000-00000000000c', 'WHATSAPP', 'CLINICAL',
           TRUE, 'WHATSAPP_RESPONSE', '2026-04-08 12:37:00', NULL
) v ON p.public_id = UUID_TO_BIN(v.pid)
ON DUPLICATE KEY UPDATE
    opt_in     = VALUES(opt_in),
    opt_in_at  = VALUES(opt_in_at),
    opt_out_at = VALUES(opt_out_at);

-- ----------------------------------------------------- fusion ya documentada
-- Vanessa Aguilera quedo INACTIVE porque su ficha se fusiono con la de Maria Elena tras
-- detectarse un registro duplicado en recepcion. La tabla conserva el rastro: una fusion
-- no borra al duplicado, lo deja apuntando al registro superviviente.
INSERT INTO patient_merge (
    surviving_patient_id, merged_patient_id, reason,
    merged_by_user_public_id, merged_at, event_public_id
)
SELECT superviviente.id, duplicado.id,
       'Duplicado detectado en recepcion: misma persona registrada dos veces al cambiar de sede',
       UUID_TO_BIN('20000000-0000-4000-8000-000000000099'),
       '2026-07-15 10:22:00',
       UUID_TO_BIN('40000000-0000-4000-8000-000000000001')
FROM patient superviviente
JOIN patient duplicado
  ON duplicado.public_id = UUID_TO_BIN('10000000-0000-4000-8000-000000000008')
WHERE superviviente.public_id = UUID_TO_BIN('10000000-0000-4000-8000-000000000001');
