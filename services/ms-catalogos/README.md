# ms-catalogos

Servicio propietario de los catálogos globales `specialty`, `icd10_catalog`, `medication`, `study_type`, `tax_catalog`, `medication_active_ingredient` y `catalog_sync`.

- Puerto interno: `8083`
- Base: `catalogs_db`
- Usuario: `catalogs_user`
- Variable de contraseña: `CATALOGOS_DB_PASSWORD`
- Ping: `GET /api/v1/catalogs/ping`

Las migraciones Flyway se ejecutan contra MySQL al iniciar el servicio. Para pruebas locales de Java, H2 crea un esquema efímero; no sustituye la validación de la migración MySQL.

## Contrato para ms-historial

`GET /internal/v1/icd10/{code}` valida un diagnóstico antes de persistirlo. Devuelve `200` con el código y sus metadatos si está activo, o `404` con `ICD10_NOT_FOUND` si no existe o está inactivo.

Ejemplo: `GET http://ms-catalogos:8083/internal/v1/icd10/J00`

```powershell
mvn test
docker build -t medicalsys/ms-catalogos:local .
```
