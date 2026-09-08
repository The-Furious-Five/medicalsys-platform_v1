-- =====================================================================================
-- ms-historial - marcador de la semilla de demostracion.
--
-- Los expedientes de demostracion ya no se siembran desde SQL. La version anterior de este
-- archivo tenia dos defectos que la hacian inservible:
--
--   1. Apuntaba al paciente 00000000-...-0000000000b1, que la semilla de ms-pacientes nunca
--      crea; la cabecera del expediente quedaba sin persona.
--   2. No podia sembrar narrativa clinica, porque se persiste cifrada y un script SQL no
--      puede producir ese ciphertext. El expediente aparecia vacio en la interfaz.
--
-- Ambas cosas las resuelve DemoMedicalRecordSeeder (service/DemoMedicalRecordSeeder.java),
-- que usa el mismo NarrativeCipher que el servicio y los UUID reales de ms-pacientes y
-- ms-profesionales. Se desactiva con MEDICAL_RECORDS_DEMO_SEED=false.
--
-- El archivo se conserva -vacio de efectos- porque Flyway registra las migraciones
-- repetibles por nombre: borrarlo dejaria una entrada huerfana en flyway_schema_history.
-- =====================================================================================

SELECT 1;
