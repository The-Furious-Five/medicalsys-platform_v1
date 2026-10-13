# ms-catalogos

Servicio propietario de los catálogos globales `specialty`, `icd10_catalog`, `medication`, `study_type`, `tax_catalog`, `medication_active_ingredient` y `catalog_sync`.

- Puerto interno: `8083`
- Base: `catalogs_db`
- Usuario: `catalogs_user`
- Variable de contraseña: `CATALOGOS_DB_PASSWORD`
- Ping: `GET /api/v1/catalogs/ping`

Cada consulta exige el permiso de lectura de su catálogo (`catalogs.icd10.read`, `catalogs.medication.read`, `catalogs.specialty.read`, `catalogs.study_type.read`, `catalogs.tax.read`), que el gateway propaga en `X-Permissions`. Lo que no declara su control se deniega por defecto (RBAC-04): sin identidad responde `401` y sin el permiso, `403`. Las rutas `/internal/v1/**` que valida ms-historial son `@InternalEndpoint`.

Las migraciones Flyway se ejecutan contra MySQL al iniciar el servicio. Para pruebas locales de Java, H2 crea un esquema efímero; no sustituye la validación de la migración MySQL.

```powershell
mvn test
docker build -t medicalsys/ms-catalogos:local .
```
