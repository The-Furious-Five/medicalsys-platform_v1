-- ms-tenencia · tenancy_db · enriquecimiento del tenant de demostracion.
--
-- V2 dejo el tenant minimo del guion del sprint: una sucursal y dos prestaciones con
-- "(demostracion)" en el nombre, NIT 0000000000 y direccion "entorno local". Servia para
-- probar los endpoints, pero no para ver la aplicacion funcionando: un catalogo de dos
-- lineas no ejercita el filtro por especialidad, y con una sola sucursal la agenda nunca
-- muestra la columna de sede.
--
-- Esta migracion sustituye esos valores por los de un policlinico privado pacenio
-- verosimil, conservando los UUID publicos que V2 fijo -ms-facturacion y ms-agenda los
-- referencian- y anadiendo dos sucursales mas y un catalogo de once prestaciones con
-- precios de mercado en BOB.
--
-- Los datos siguen siendo ficticios. El NIT no pertenece a ningun contribuyente
-- registrado, los dominios son .invalid (RFC 2606, reservado y no resoluble) y los
-- telefonos usan el rango 2-2xx-xxxx / 7xx que no esta asignado.

-- ------------------------------------------------------------ plan del tenant demo
-- Tres sucursales no caben en el plan DEMO (max_branches = 1). Se mueve el contrato a
-- PRO, que es el plan que un centro de este tamano contrataria de verdad; asi el limite
-- que comprueba BranchService deja de ser el que bloquea la propia siembra.
UPDATE subscription_plan
   SET name = 'Plan profesional', monthly_price_bob = 1450.00,
       max_users = 60, max_branches = 10, max_storage_gb = 100
 WHERE code = 'PRO';

UPDATE subscription s
  JOIN organization o      ON o.id = s.organization_id
  JOIN subscription_plan p ON p.code = 'PRO'
   SET s.subscription_plan_id = p.id,
       s.agreed_price_bob     = 1450.00,
       s.billing_cycle        = 'MONTHLY',
       s.status               = 'ACTIVE'
 WHERE o.code = 'DEMO';

-- --------------------------------------------------------------- ficha del centro
UPDATE organization
   SET legal_name    = 'POLICLINICO SAN GABRIEL S.R.L.',
       trade_name    = 'Policlinico San Gabriel',
       nit           = '1023847026',
       contact_email = 'contacto@sangabriel.clinica.invalid',
       phone_e164    = '+59122791450',
       city          = 'La Paz',
       department    = 'LA_PAZ'
 WHERE code = 'DEMO';

-- ------------------------------------------------------------------- sucursales
-- La casa matriz conserva su public_id: ms-agenda referencia esa sede en sus recursos.
UPDATE branch b
  JOIN organization o ON o.id = b.organization_id
   SET b.name       = 'Sede Sopocachi',
       b.address    = 'Av. Ecuador 2456, esq. Rosendo Gutierrez, Sopocachi',
       b.city       = 'La Paz',
       b.department = 'LA_PAZ',
       b.phone_e164 = '+59122791450'
 WHERE o.code = 'DEMO' AND b.code = 'CENTRAL';

INSERT INTO branch
    (public_id, organization_id, code, name, is_headquarters, address, city, department,
     phone_e164, status)
SELECT UUID_TO_BIN('00000000-0000-4000-8000-0000000000a3'), o.id,
       'MIRAFLORES', 'Sede Miraflores', FALSE,
       'Av. Saavedra 1845, frente al Hospital Obrero, Miraflores',
       'La Paz', 'LA_PAZ', '+59122224318', 'ACTIVE'
FROM organization o WHERE o.code = 'DEMO'
ON DUPLICATE KEY UPDATE name = VALUES(name), address = VALUES(address), status = VALUES(status);

INSERT INTO branch
    (public_id, organization_id, code, name, is_headquarters, address, city, department,
     phone_e164, status)
SELECT UUID_TO_BIN('00000000-0000-4000-8000-0000000000a4'), o.id,
       'EL_ALTO', 'Sede El Alto - Ceja', FALSE,
       'Av. Jorge Carrasco 780, zona Ceja de El Alto',
       'El Alto', 'LA_PAZ', '+59122845190', 'ACTIVE'
FROM organization o WHERE o.code = 'DEMO'
ON DUPLICATE KEY UPDATE name = VALUES(name), address = VALUES(address), status = VALUES(status);

-- ----------------------------------------------------------------- prestaciones
-- Las dos que V2 creo mantienen su public_id y su precio: ms-facturacion los replica en
-- su catalogo local y una discrepancia haria que la misma factura valiera distinto segun
-- el modo del servicio.
UPDATE service_offering so
  JOIN organization o ON o.id = so.organization_id
   SET so.name        = 'Consulta de medicina general',
       so.description = 'Consulta ambulatoria de primer contacto, con anamnesis y examen fisico.'
 WHERE o.code = 'DEMO' AND so.code = 'CONSULTATION';

UPDATE service_offering so
  JOIN organization o ON o.id = so.organization_id
   SET so.name        = 'Radiografia simple',
       so.description = 'Estudio radiologico simple de una region, con informe del radiologo.'
 WHERE o.code = 'DEMO' AND so.code = 'X_RAY';

INSERT INTO service_offering
    (public_id, organization_id, code, name, description, type, specialty_code,
     duration_minutes, base_price_bob, requires_consent, required_resource_type,
     tax_product_code, tax_unit_code, active)
SELECT v.public_id, o.id, v.code, v.name, v.description, v.type, v.specialty_code,
       v.duration_minutes, v.base_price_bob, v.requires_consent, v.required_resource_type,
       v.tax_product_code, v.tax_unit_code, TRUE
FROM organization o
JOIN (
    SELECT UUID_TO_BIN('00000000-0000-4000-8000-000000000503') AS public_id,
           'CONSULTA_INTERNA' AS code, 'Consulta de medicina interna' AS name,
           'Evaluacion y seguimiento de enfermedad cronica del adulto.' AS description,
           'CONSULTATION' AS type, 'MEDICINA_INTERNA' AS specialty_code,
           40 AS duration_minutes, 250.00 AS base_price_bob, FALSE AS requires_consent,
           'OFFICE' AS required_resource_type, '99100' AS tax_product_code, '58' AS tax_unit_code
    UNION ALL SELECT UUID_TO_BIN('00000000-0000-4000-8000-000000000504'),
           'CONSULTA_PEDIATRIA', 'Consulta pediatrica',
           'Control de crecimiento y desarrollo, y atencion de patologia aguda infantil.',
           'CONSULTATION', 'PEDIATRIA', 30, 200.00, FALSE, 'OFFICE', '99100', '58'
    UNION ALL SELECT UUID_TO_BIN('00000000-0000-4000-8000-000000000505'),
           'CONSULTA_GINECO', 'Consulta ginecologica',
           'Control ginecologico anual, planificacion familiar y control prenatal.',
           'CONSULTATION', 'GINECOLOGIA', 40, 280.00, TRUE, 'OFFICE', '99100', '58'
    UNION ALL SELECT UUID_TO_BIN('00000000-0000-4000-8000-000000000506'),
           'CONSULTA_CARDIO', 'Consulta cardiologica',
           'Valoracion de riesgo cardiovascular y seguimiento de hipertension.',
           'CONSULTATION', 'CARDIOLOGIA', 40, 320.00, FALSE, 'OFFICE', '99100', '58'
    UNION ALL SELECT UUID_TO_BIN('00000000-0000-4000-8000-000000000507'),
           'CONSULTA_TRAUMA', 'Consulta traumatologica',
           'Evaluacion de lesiones del aparato locomotor y control postoperatorio.',
           'CONSULTATION', 'TRAUMATOLOGIA', 30, 280.00, FALSE, 'OFFICE', '99100', '58'
    UNION ALL SELECT UUID_TO_BIN('00000000-0000-4000-8000-000000000508'),
           'CONTROL', 'Consulta de control',
           'Seguimiento dentro de los 30 dias de una consulta previa por el mismo motivo.',
           'FOLLOW_UP', 'MEDICINA_GENERAL', 20, 80.00, FALSE, 'OFFICE', '99100', '58'
    UNION ALL SELECT UUID_TO_BIN('00000000-0000-4000-8000-000000000509'),
           'ECOGRAFIA_ABD', 'Ecografia abdominal',
           'Ecografia abdominal completa. Requiere ayuno de seis horas.',
           'STUDY', 'RADIOLOGIA', 30, 350.00, FALSE, 'EQUIPMENT', '99200', '58'
    UNION ALL SELECT UUID_TO_BIN('00000000-0000-4000-8000-00000000050a'),
           'LAB_BASICO', 'Perfil de laboratorio basico',
           'Hemograma completo, glucemia en ayunas y examen general de orina.',
           'STUDY', 'LABORATORIO', 15, 180.00, FALSE, 'NONE', '99200', '58'
    UNION ALL SELECT UUID_TO_BIN('00000000-0000-4000-8000-00000000050b'),
           'ELECTROCARDIO', 'Electrocardiograma',
           'Registro de 12 derivaciones en reposo, con lectura del cardiologo.',
           'STUDY', 'CARDIOLOGIA', 20, 150.00, FALSE, 'EQUIPMENT', '99200', '58'
    UNION ALL SELECT UUID_TO_BIN('00000000-0000-4000-8000-00000000050c'),
           'TELECONSULTA', 'Teleconsulta de medicina general',
           'Consulta remota por videollamada; no habilita emision de receta controlada.',
           'TELECONSULTATION', 'MEDICINA_GENERAL', 20, 120.00, FALSE, 'NONE', '99100', '58'
    UNION ALL SELECT UUID_TO_BIN('00000000-0000-4000-8000-00000000050d'),
           'VACUNA_INFLUENZA', 'Vacunacion contra influenza',
           'Aplicacion de vacuna antigripal estacional, incluye insumo.',
           'VACCINATION', 'MEDICINA_GENERAL', 15, 90.00, TRUE, 'PROCEDURE_ROOM', '99100', '58'
) v
WHERE o.code = 'DEMO'
ON DUPLICATE KEY UPDATE
    name             = VALUES(name),
    description      = VALUES(description),
    duration_minutes = VALUES(duration_minutes),
    base_price_bob   = VALUES(base_price_bob),
    active           = VALUES(active);

-- ------------------------------------------------------------ consumo del plan
-- Los contadores tienen que reflejar lo que acaba de sembrarse: tres sucursales y las
-- diez cuentas que crea DemoUserSeeder de ms-identidad. Dejarlos en 1 y 0 haria que la
-- pantalla de consumo mostrara menos sucursales de las que el propio listado devuelve.
UPDATE plan_usage pu
  JOIN organization o ON o.id = pu.organization_id
   SET pu.current_value = 3
 WHERE o.code = 'DEMO' AND pu.metric = 'BRANCHES';

UPDATE plan_usage pu
  JOIN organization o ON o.id = pu.organization_id
   SET pu.current_value = 10
 WHERE o.code = 'DEMO' AND pu.metric = 'USERS';

UPDATE plan_usage pu
  JOIN organization o ON o.id = pu.organization_id
   SET pu.current_value = 2415919104
 WHERE o.code = 'DEMO' AND pu.metric = 'STORAGE_BYTES';
