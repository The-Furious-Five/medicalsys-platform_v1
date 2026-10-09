-- La primera cuenta demo de identidad utilizo temporalmente el UUID ficticio
-- 11111111-1111-1111-1111-111111111111. Los pacientes registrados mientras
-- ese JWT seguia vigente quedaron asociados a un tenant que no existe y, al
-- iniciar sesion con la organizacion demo real, se mostraban como pacientes de
-- otra institucion (sin documento ni fecha de nacimiento).
--
-- La organizacion demo canonica ya esta sembrada por ms-tenencia y configurada
-- en ms-identidad como 00000000-0000-4000-8000-0000000000a1.

UPDATE patient
SET registered_by_organization_public_id =
        UNHEX(REPLACE('00000000-0000-4000-8000-0000000000a1', '-', ''))
WHERE registered_by_organization_public_id =
        UNHEX(REPLACE('11111111-1111-1111-1111-111111111111', '-', ''));
