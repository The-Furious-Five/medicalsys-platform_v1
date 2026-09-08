-- ms-profesionales · professionals_db · plantilla medica de demostracion.
--
-- Datos ficticios. Esta ubicacion solo se carga cuando compose.yaml pasa FLYWAY_LOCATIONS
-- con classpath:db/demo, de modo que un despliegue real arranca con la tabla vacia.
--
-- Sin esta siembra el modulo entero queda inservible aunque el codigo funcione: el listado
-- devuelve [], el desplegable de la agenda no ofrece a nadie, no hay
-- professionalOrganizationPublicId con el que pedir huecos libres y el filtro por
-- especialidad no tiene nada que filtrar.
--
-- user_account_public_id apunta a las cuentas que siembra DemoUserSeeder de ms-identidad,
-- con los mismos UUID fijos (rango ...0000000009xx). Son bases separadas y no hay FK entre
-- ellas: el identificador acordado es lo unico que las mantiene alineadas.
--
-- Las matriculas siguen el formato del Colegio Medico de Bolivia (LP-#####) pero los
-- numeros no corresponden a ningun profesional colegiado.
--
-- Idempotencia: se borran las propias filas -public_id fijo del rango
-- 50000000-0000-4000-8000-*- y se vuelven a insertar, para que un cambio en este archivo
-- se refleje al reiniciar. Las altas hechas desde la interfaz llevan UUID aleatorio.

DELETE po FROM professional_organization po
  JOIN health_professional hp ON hp.id = po.health_professional_id
 WHERE HEX(hp.public_id) LIKE '50000000000040008000%';

DELETE ps FROM professional_specialty ps
  JOIN health_professional hp ON hp.id = ps.health_professional_id
 WHERE HEX(hp.public_id) LIKE '50000000000040008000%';

DELETE FROM health_professional WHERE HEX(public_id) LIKE '50000000000040008000%';

-- ------------------------------------------------------------------ plantilla
INSERT INTO health_professional (
    public_id, user_account_public_id, document_type, document_number, document_complement,
    given_names, paternal_surname, maternal_surname, professional_type,
    professional_license_number, title, biography, status
) VALUES
(UUID_TO_BIN('50000000-0000-4000-8000-000000000001'),
 UUID_TO_BIN('00000000-0000-4000-8000-000000000903'),
 'CI', '3820947', '', 'Ricardo', 'Salazar', 'Peredo', 'PHYSICIAN', 'LP-14892',
 'Medico Cirujano - Especialista en Medicina Interna',
 'Egresado de la Universidad Mayor de San Andres. Veinte anios de consulta ambulatoria en medicina interna, con enfoque en el control de hipertension y diabetes tipo 2.',
 'ACTIVE'),

(UUID_TO_BIN('50000000-0000-4000-8000-000000000002'),
 UUID_TO_BIN('00000000-0000-4000-8000-000000000905'),
 'CI', '5147203', '', 'Gabriela', 'Nunez', 'Arce', 'PHYSICIAN', 'LP-20713',
 'Medico Pediatra',
 'Especialista en pediatria por la Universidad Catolica Boliviana. Atiende control de crecimiento y desarrollo, vacunacion y patologia respiratoria infantil.',
 'ACTIVE'),

(UUID_TO_BIN('50000000-0000-4000-8000-000000000003'),
 UUID_TO_BIN('00000000-0000-4000-8000-000000000906'),
 'CI', '4059318', '', 'Silvia', 'Mendoza', 'Rocha', 'PHYSICIAN', 'LP-17604',
 'Medico Ginecologo-Obstetra',
 'Control prenatal de bajo y alto riesgo, planificacion familiar y ecografia obstetrica.',
 'ACTIVE'),

(UUID_TO_BIN('50000000-0000-4000-8000-000000000004'),
 UUID_TO_BIN('00000000-0000-4000-8000-000000000907'),
 'CI', '2947165', '', 'Fernando', 'Arispe', 'Calderon', 'PHYSICIAN', 'LP-11238',
 'Medico Cardiologo',
 'Valoracion de riesgo cardiovascular, electrocardiografia y seguimiento de insuficiencia cardiaca.',
 'ACTIVE'),

-- Vinculo suspendido: esta de licencia. El listado no debe ofrecerlo para citar, pero su
-- ficha si tiene que abrirse; es el caso que distingue GET /api/v1/professionals/{id} de
-- GET /internal/v1/professionals/{id}.
(UUID_TO_BIN('50000000-0000-4000-8000-000000000005'),
 UUID_TO_BIN('00000000-0000-4000-8000-000000000908'),
 'CI', '6318420', '', 'Oscar', 'Ledezma', 'Fuentes', 'PHYSICIAN', 'LP-19457',
 'Medico Traumatologo y Ortopedista',
 'Traumatologia deportiva y cirugia de mano. Actualmente en licencia por formacion en el exterior.',
 'ACTIVE'),

-- Profesional no medico: el modulo no es solo de medicos y el tipo lo refleja.
(UUID_TO_BIN('50000000-0000-4000-8000-000000000006'),
 UUID_TO_BIN('00000000-0000-4000-8000-000000000909'),
 'CI', '7204851', '', 'Andrea', 'Terceros', 'Blanco', 'NUTRITIONIST', 'LP-N-3092',
 'Licenciada en Nutricion y Dietetica',
 'Valoracion nutricional, planes alimentarios para diabetes y obesidad, y consejeria en lactancia.',
 'ACTIVE'),

(UUID_TO_BIN('50000000-0000-4000-8000-000000000007'),
 UUID_TO_BIN('00000000-0000-4000-8000-00000000090a'),
 'FOREIGN_ID_CARD', 'AR-28417305', '', 'Martin', 'Ibanez', 'Sosa', 'DENTIST', 'LP-O-5741',
 'Odontologo General',
 'Odontologia restauradora y cirugia oral menor. Matricula convalidada por el Colegio de Odontologos.',
 'ACTIVE');

-- --------------------------------------------------------------- especialidades
-- Los codigos son los de specialty en ms-catalogos: si no coincidieran, el filtro por
-- especialidad de la agenda no encontraria a nadie.
INSERT INTO professional_specialty (health_professional_id, specialty_code, is_primary, graduation_year)
SELECT hp.id, v.specialty_code, v.is_primary, v.graduation_year
FROM health_professional hp
JOIN (
    SELECT '50000000-0000-4000-8000-000000000001' AS pid, 'MEDICINA_INTERNA' AS specialty_code, TRUE AS is_primary, 2004 AS graduation_year
    UNION ALL SELECT '50000000-0000-4000-8000-000000000001', 'MEDICINA_GENERAL', FALSE, 1999
    UNION ALL SELECT '50000000-0000-4000-8000-000000000002', 'PEDIATRIA',        TRUE,  2012
    UNION ALL SELECT '50000000-0000-4000-8000-000000000003', 'GINECOLOGIA',      TRUE,  2008
    UNION ALL SELECT '50000000-0000-4000-8000-000000000004', 'CARDIOLOGIA',      TRUE,  2001
    UNION ALL SELECT '50000000-0000-4000-8000-000000000004', 'MEDICINA_INTERNA', FALSE, 1997
    UNION ALL SELECT '50000000-0000-4000-8000-000000000005', 'TRAUMATOLOGIA',    TRUE,  2010
    UNION ALL SELECT '50000000-0000-4000-8000-000000000006', 'NUTRICION',        TRUE,  2015
    UNION ALL SELECT '50000000-0000-4000-8000-000000000007', 'ODONTOLOGIA',      TRUE,  2013
) v ON hp.public_id = UUID_TO_BIN(v.pid);

-- ------------------------------------------------------------------- vinculos
-- El public_id de cada vinculo es el professionalOrganizationPublicId que ms-agenda exige
-- para calcular disponibilidad; por eso tambien es fijo.
INSERT INTO professional_organization (
    public_id, health_professional_id, organization_public_id, branch_public_id,
    modality, commission_percentage, start_date, end_date, status
)
SELECT UUID_TO_BIN(v.link_id), hp.id,
       UUID_TO_BIN('00000000-0000-4000-8000-0000000000a1'),
       UUID_TO_BIN(v.branch_id), v.modality, v.commission, v.start_date, v.end_date, v.status
FROM health_professional hp
JOIN (
    SELECT '50000000-0000-4000-8000-000000000001' AS pid,
           '60000000-0000-4000-8000-000000000001' AS link_id,
           '00000000-0000-4000-8000-0000000000a2' AS branch_id,
           'STAFF' AS modality, 0.00 AS commission,
           '2019-03-01' AS start_date, CAST(NULL AS DATE) AS end_date, 'ACTIVE' AS status
    UNION ALL SELECT '50000000-0000-4000-8000-000000000002', '60000000-0000-4000-8000-000000000002',
           '00000000-0000-4000-8000-0000000000a2', 'STAFF', 0.00, '2021-02-15', NULL, 'ACTIVE'
    UNION ALL SELECT '50000000-0000-4000-8000-000000000003', '60000000-0000-4000-8000-000000000003',
           '00000000-0000-4000-8000-0000000000a2', 'FEE_BASED', 35.00, '2020-08-03', NULL, 'ACTIVE'
    UNION ALL SELECT '50000000-0000-4000-8000-000000000004', '60000000-0000-4000-8000-000000000004',
           '00000000-0000-4000-8000-0000000000a3', 'EXTERNAL_CONSULTANT', 45.00, '2022-05-09', NULL, 'ACTIVE'
    -- De licencia: SUSPENDED, no ENDED. El vinculo se reanuda al volver.
    UNION ALL SELECT '50000000-0000-4000-8000-000000000005', '60000000-0000-4000-8000-000000000005',
           '00000000-0000-4000-8000-0000000000a2', 'FEE_BASED', 40.00, '2018-11-20', NULL, 'SUSPENDED'
    UNION ALL SELECT '50000000-0000-4000-8000-000000000006', '60000000-0000-4000-8000-000000000006',
           '00000000-0000-4000-8000-0000000000a3', 'PARTNER', 50.00, '2023-01-16', NULL, 'ACTIVE'
    UNION ALL SELECT '50000000-0000-4000-8000-000000000007', '60000000-0000-4000-8000-000000000007',
           '00000000-0000-4000-8000-0000000000a4', 'FEE_BASED', 45.00, '2024-04-02', NULL, 'ACTIVE'
    -- Segunda sede del internista: un mismo profesional atiende en dos sucursales, que es
    -- lo que obliga a que el vinculo -y no el profesional- sea la unidad de la agenda.
    UNION ALL SELECT '50000000-0000-4000-8000-000000000001', '60000000-0000-4000-8000-000000000008',
           '00000000-0000-4000-8000-0000000000a3', 'STAFF', 0.00, '2023-06-01', NULL, 'ACTIVE'
) v ON hp.public_id = UUID_TO_BIN(v.pid);
