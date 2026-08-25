-- ms-tenencia · tenancy_db · datos de demostracion del entorno local.
--
-- Siembra el tenant "Clinica Demo" con su casa matriz y las dos prestaciones del guion
-- del sprint: CONSULTATION y X_RAY. Todos los valores son ficticios; el NIT, el telefono
-- y el correo no corresponden a ninguna organizacion real.
--
-- La migracion es idempotente: cada INSERT lleva ON DUPLICATE KEY UPDATE sobre su clave
-- unica o comprueba antes la ausencia de la fila. Reaplicarla sobre una base ya sembrada
-- no duplica datos ni altera los identificadores publicos.
--
-- Los UUID publicos son fijos a proposito. ms-facturacion los usa como catalogo local de
-- prestaciones en su perfil LOCAL, de modo que su modo REMOTE contra este servicio
-- devuelve exactamente los mismos codigos y precios.

-- ------------------------------------------------------------- planes comerciales
INSERT INTO subscription_plan
    (code, name, monthly_price_bob, max_users, max_branches, max_storage_gb,
     includes_whatsapp, includes_billing, active)
VALUES
    ('DEMO',  'Plan de demostracion',   0.00,   5,    1,   1, TRUE, TRUE, TRUE),
    ('BASIC', 'Plan basico',          350.00,  10,    2,   5, TRUE, TRUE, TRUE),
    ('PRO',   'Plan profesional',     900.00,  50,   10,  50, TRUE, TRUE, TRUE)
ON DUPLICATE KEY UPDATE
    name              = VALUES(name),
    monthly_price_bob = VALUES(monthly_price_bob),
    max_users         = VALUES(max_users),
    max_branches      = VALUES(max_branches),
    max_storage_gb    = VALUES(max_storage_gb),
    includes_whatsapp = VALUES(includes_whatsapp),
    includes_billing  = VALUES(includes_billing),
    active            = VALUES(active);

-- ------------------------------------------------------------ organizacion demo
-- Se crea ACTIVE, y no ONBOARDING, para que la demo local pueda registrar sucursales
-- y prestaciones desde un arranque limpio sin un paso manual previo.
INSERT INTO organization
    (public_id, code, type, legal_name, trade_name, nit, contact_email, phone_e164,
     city, department, time_zone, currency, status)
VALUES (
    UUID_TO_BIN('00000000-0000-4000-8000-0000000000a1'),
    'DEMO',
    'PRIVATE_CLINIC',
    'CLINICA DEMO MEDICALSYS S.R.L.',
    'Clinica Demo',
    '0000000000',
    'contacto@clinica-demo.invalid',
    '+59120000000',
    'La Paz',
    'LA_PAZ',
    'America/La_Paz',
    'BOB',
    'ACTIVE'
)
ON DUPLICATE KEY UPDATE
    legal_name    = VALUES(legal_name),
    trade_name    = VALUES(trade_name),
    contact_email = VALUES(contact_email),
    status        = VALUES(status);

-- ---------------------------------------------------------------- casa matriz demo
INSERT INTO branch
    (public_id, organization_id, code, name, is_headquarters, address, city, department,
     phone_e164, status)
SELECT
    UUID_TO_BIN('00000000-0000-4000-8000-0000000000a2'),
    o.id,
    'CENTRAL',
    'Sucursal Central',
    TRUE,
    'Av. Demostracion 100, entorno local',
    'La Paz',
    'LA_PAZ',
    '+59120000001',
    'ACTIVE'
FROM organization o
WHERE o.code = 'DEMO'
ON DUPLICATE KEY UPDATE
    name    = VALUES(name),
    address = VALUES(address),
    status  = VALUES(status);

-- ------------------------------------------------------------- prestaciones demo
-- Los precios coinciden con el catalogo local de ms-facturacion: 150.00 y 250.00 BOB.
-- tax_unit_code 58 es la unidad del catalogo del SIN; los codigos de producto son
-- simulados y no habilitan ninguna emision con validez tributaria.
INSERT INTO service_offering
    (public_id, organization_id, code, name, description, type, specialty_code,
     duration_minutes, base_price_bob, requires_consent, required_resource_type,
     tax_product_code, tax_unit_code, active)
SELECT
    UUID_TO_BIN('00000000-0000-4000-8000-000000000501'),
    o.id,
    'CONSULTATION',
    'Consulta medica general (demostracion)',
    'Consulta ambulatoria de medicina general para el entorno de demostracion.',
    'CONSULTATION',
    'MEDICINA_GENERAL',
    30,
    150.00,
    FALSE,
    'OFFICE',
    '99100',
    '58',
    TRUE
FROM organization o
WHERE o.code = 'DEMO'
ON DUPLICATE KEY UPDATE
    name             = VALUES(name),
    description      = VALUES(description),
    duration_minutes = VALUES(duration_minutes),
    base_price_bob   = VALUES(base_price_bob),
    tax_product_code = VALUES(tax_product_code),
    tax_unit_code    = VALUES(tax_unit_code),
    active           = VALUES(active);

INSERT INTO service_offering
    (public_id, organization_id, code, name, description, type, specialty_code,
     duration_minutes, base_price_bob, requires_consent, required_resource_type,
     tax_product_code, tax_unit_code, active)
SELECT
    UUID_TO_BIN('00000000-0000-4000-8000-000000000502'),
    o.id,
    'X_RAY',
    'Estudio de rayos X (demostracion)',
    'Estudio de imagen simple para el entorno de demostracion.',
    'STUDY',
    'RADIOLOGIA',
    20,
    250.00,
    FALSE,
    'EQUIPMENT',
    '99200',
    '58',
    TRUE
FROM organization o
WHERE o.code = 'DEMO'
ON DUPLICATE KEY UPDATE
    name             = VALUES(name),
    description      = VALUES(description),
    duration_minutes = VALUES(duration_minutes),
    base_price_bob   = VALUES(base_price_bob),
    tax_product_code = VALUES(tax_product_code),
    tax_unit_code    = VALUES(tax_unit_code),
    active           = VALUES(active);

-- ------------------------------------------------------------- suscripcion demo
-- subscription no tiene clave unica de negocio en el diccionario, asi que la
-- idempotencia se resuelve comprobando la ausencia del contrato antes de insertarlo.
INSERT INTO subscription
    (organization_id, subscription_plan_id, billing_cycle, start_date, end_date,
     agreed_price_bob, status)
SELECT o.id, p.id, 'MONTHLY', '2026-01-01', NULL, 0.00, 'ACTIVE'
FROM organization o
JOIN subscription_plan p ON p.code = 'DEMO'
WHERE o.code = 'DEMO'
  AND NOT EXISTS (
      SELECT 1 FROM subscription s WHERE s.organization_id = o.id
  );

-- --------------------------------------------------------------- consumo del plan
-- Contadores materializados del tenant demo. BRANCHES arranca en 1 porque la casa
-- matriz ya existe; el resto en cero.
INSERT INTO plan_usage (organization_id, metric, current_value, period_start, period_end)
SELECT o.id, 'BRANCHES', 1, '2026-01-01', NULL FROM organization o WHERE o.code = 'DEMO'
ON DUPLICATE KEY UPDATE current_value = VALUES(current_value);

INSERT INTO plan_usage (organization_id, metric, current_value, period_start, period_end)
SELECT o.id, 'USERS', 0, '2026-01-01', NULL FROM organization o WHERE o.code = 'DEMO'
ON DUPLICATE KEY UPDATE current_value = VALUES(current_value);

INSERT INTO plan_usage (organization_id, metric, current_value, period_start, period_end)
SELECT o.id, 'STORAGE_BYTES', 0, '2026-01-01', NULL FROM organization o WHERE o.code = 'DEMO'
ON DUPLICATE KEY UPDATE current_value = VALUES(current_value);
