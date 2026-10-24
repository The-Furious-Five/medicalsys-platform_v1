-- LOG-04 / ISO-01 - El OSI revisa la bitacora de toda la plataforma.
--
-- audit.log.read solo muestra la organizacion de la sesion. El OSI tiene alcance de plataforma y sus
-- acciones (niveles MAC, roles, cuentas) caen en la organizacion de la cuenta afectada, asi que con un
-- solo tenant no veia ni lo que el mismo habia hecho. ms-auditoria (AuditScope) respeta este permiso:
-- todas las organizaciones, o la que el OSI elija. Es RESTRICTED: cruza tenants.
INSERT INTO permission (code, module, description, is_sensitive, classification) VALUES
    ('audit.log.read_platform', 'AUDIT',
     'Consultar la bitacora de todas las organizaciones de la plataforma (OSI, ISO 27001 A.8.15)', TRUE,
     'RESTRICTED');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p
WHERE r.code = 'OSI' AND p.code = 'audit.log.read_platform';
