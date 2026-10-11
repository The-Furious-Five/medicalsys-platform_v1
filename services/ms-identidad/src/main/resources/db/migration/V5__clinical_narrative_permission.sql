-- RBAC-03 (SINS-26) - Permiso para leer en claro la narrativa clinica cifrada.
--
-- ms-historial descifraba los antecedentes y los resultados de estudios solo para una lista fija de
-- roles en el codigo (PHYSICIAN, ADMINISTRATOR y PLATFORM_ADMIN). Desde RBAC-03 lo decide este
-- permiso, que se asigna desde la API de roles. La asignacion inicial replica esa lista para no
-- cambiar quien lee que.
INSERT INTO permission (code, module, description, is_sensitive) VALUES
    ('clinical.narrative.read', 'CLINICAL',
     'Leer en claro el contenido clinico cifrado (antecedentes y resultados de estudios)', TRUE);

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p
WHERE p.code = 'clinical.narrative.read'
  AND r.code IN ('PLATFORM_ADMIN', 'ADMINISTRATOR', 'PHYSICIAN');
