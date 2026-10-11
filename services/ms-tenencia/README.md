# ms-tenencia

Organizacion, sucursal, prestaciones y precios de MedicalSys.

Es el **unico propietario de escritura** de `tenancy_db`. No guarda citas, facturas ni datos
clinicos, y no declara ninguna clave foranea hacia otra base.

| Dato | Valor |
| --- | --- |
| Puerto interno | `8082` |
| Base logica | `tenancy_db` (MySQL 8) |
| Usuario de base | `tenancy_user` |
| Direccion interna | `http://ms-tenencia:8082` |
| Ruta en el gateway | `/api/v1/tenancy/**` |
| Responsable Sprint 2 | Franco |

## Responsabilidad

Propietario de las seis tablas del capitulo 3 del diccionario: `subscription_plan`,
`organization`, `subscription`, `branch`, `service_offering` y `plan_usage`.

Lo que **no** guarda: usuarios y credenciales (ms-identidad), catalogos globales como CIE-10 o
especialidades (ms-catalogos), citas (ms-agenda), historia clinica (ms-historial) y facturas
(ms-facturacion).

## Variables de entorno

Compose las inyecta; el repositorio no versiona ningun valor secreto.

| Variable | Uso | Default |
| --- | --- | --- |
| `SERVER_PORT` | Puerto del contenedor | `8082` |
| `DB_URL` | JDBC hacia `tenancy_db` | requerido |
| `DB_USER` | Usuario exclusivo del servicio | requerido |
| `DB_PASSWORD` | Contrasena del usuario | requerido |
| `DB_POOL_SIZE` | Tamano del pool Hikari | `10` |
| `LOG_LEVEL` | Nivel de log del servicio | `INFO` |
| `TENANCY_DEFAULT_TIME_ZONE` | Zona de una organizacion nueva | `America/La_Paz` |
| `TENANCY_DEFAULT_CURRENCY` | Moneda de una organizacion nueva | `BOB` |
| `TENANCY_DEFAULT_PLAN_CODE` | Plan inicial al dar de alta un tenant | `DEMO` |
| `TENANCY_TRIAL_PERIOD` | Duracion del periodo de prueba | `30d` |
| `TENANCY_ENFORCE_PLAN_LIMITS` | Aplica los limites del plan | `true` |

Este servicio **no llama a ningun otro microservicio**: no necesita `*_BASE_URL`.

## Contrato

Todas las rutas exigen la identidad que el gateway propaga (`X-Organization-Id`, `X-User-Id`,
`X-Roles`, `X-Permissions`) y devuelven el JSON de error comun con `code`, `correlationId` y `path`. El
`organizationId` **nunca** se acepta desde el cuerpo ni desde la query.

### Rutas publicas — `/api/v1/tenancy`

Desde RBAC-03 cada endpoint exige un permiso del catálogo de ms-identidad (`@RequiresPermission`), no un rol. La columna de roles indica quién lo tiene en la asignación inicial; el permiso exacto de cada endpoint está en [`docs/seguridad/rbac-01-endpoints-permisos.csv`](../../docs/seguridad/rbac-01-endpoints-permisos.csv).

| Metodo y ruta | Roles | Descripcion |
| --- | --- | --- |
| `GET /ping` | — | Comprobacion de enrutamiento |
| `POST /organizacion` | PLATFORM_ADMIN | Alta de un tenant con su suscripcion y contadores |
| `GET /organizacion` | todos los del tenant | Ficha de la organizacion del token |
| `PUT /organizacion` | ADMINISTRATOR | Datos administrativos (`code` y `nit` no se editan) |
| `PATCH /organizacion/estado` | ADMINISTRATOR | Cambia el estado; activar exige suscripcion vigente |
| `GET /planes` | ADMINISTRATOR, BILLING_CLERK | Catalogo de planes activos |
| `POST /suscripcion` | ADMINISTRATOR | Contrata un plan |
| `GET /suscripcion` | ADMINISTRATOR, BILLING_CLERK | Suscripcion vigente |
| `GET /suscripcion/historial` | ADMINISTRATOR | Historial de contratos |
| `PATCH /suscripcion/estado` | ADMINISTRATOR | Cambia el estado del contrato |
| `POST /sucursales` | ADMINISTRATOR | Alta de sede; consume cuota `BRANCHES` |
| `GET /sucursales` | todos los del tenant | Lista paginada; filtro `status` |
| `GET /sucursales/{branchId}` | todos los del tenant | Ficha de una sede |
| `PUT /sucursales/{branchId}` | ADMINISTRATOR | Actualiza la sede |
| `PATCH /sucursales/{branchId}/estado` | ADMINISTRATOR | Baja logica; libera cuota |
| `POST /prestaciones` | ADMINISTRATOR | Publica una prestacion |
| `GET /prestaciones` | todos los del tenant | Lista paginada; filtros `active`, `specialtyCode` |
| `GET /prestaciones/{id}` | todos los del tenant | Ficha de una prestacion |
| `PUT /prestaciones/{id}` | ADMINISTRATOR | Actualiza la prestacion |
| `PATCH /prestaciones/{id}/vigencia` | ADMINISTRATOR | Activa o desactiva |
| `GET /consumo` | ADMINISTRATOR | Cuotas frente a los limites del plan |
| `POST /consumo` | ADMINISTRATOR | Registra consumo (`delta` o `value`, nunca ambos) |

### Rutas internas — `/internal/v1`

Solo dentro de `medicalsys-interna`. Devuelven proyecciones minimas y aplican el mismo
aislamiento por organizacion que las rutas publicas.

| Ruta | Consumidor | Devuelve |
| --- | --- | --- |
| `GET /prestaciones/{id}` | **ms-facturacion** | `id, code, name, basePriceBob, taxProductCode, taxUnitCode, active` |
| `GET /prestaciones/{id}/agendable` | ms-agenda | duracion, recurso requerido y consentimiento |
| `GET /organizaciones/{id}` | cualquiera | existencia y estado del propio tenant |
| `GET /sucursales/{id}` | ms-agenda, ms-facturacion | existencia y estado de una sede |

> `GET /internal/v1/prestaciones/{id}` es **contrato congelado** con ms-facturacion: los nombres de
> campo coinciden uno a uno con su cliente. Un cambio incompatible exige `/internal/v2`, no un
> renombrado en sitio.

### Codigos de error

| HTTP | `code` representativo | Cuando |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Campo invalido; `details` lleva el campo exacto |
| 401 | `UNAUTHENTICATED` | El gateway no propago identidad |
| 403 | `FORBIDDEN` | El token no trae el permiso que exige la operacion |
| 404 | `ORGANIZATION_NOT_FOUND`, `BRANCH_NOT_FOUND`, `SERVICE_OFFERING_NOT_FOUND` | No existe **o pertenece a otro tenant** |
| 409 | `BRANCH_CODE_ALREADY_USED`, `SERVICE_OFFERING_CODE_ALREADY_USED`, `ORGANIZATION_NIT_ALREADY_USED` | Unicidad |
| 422 | `PLAN_LIMIT_REACHED`, `ORGANIZATION_NOT_ACTIVE` | Regla de negocio no procesable |

Un recurso de otra organizacion responde **404 y no 403**: confirmar su existencia ya seria una
fuga de aislamiento.

## Datos

`V1__create_schema.sql` crea las seis tablas del diccionario. Los ENUM del documento se
implementan como `VARCHAR` + `CHECK`, que controla los mismos valores y permite la validacion de
Hibernate. **Esta migracion ya fue aplicada y compartida: no se edita, se crea una V3.**

`V2__seed_demo.sql` siembra el entorno local y es idempotente (reaplicarla no duplica filas):

| Dato | Valor |
| --- | --- |
| Planes | `DEMO` (1 sucursal, 5 usuarios, 1 GB), `BASIC`, `PRO` |
| Organizacion | `Clinica Demo` · `00000000-0000-4000-8000-0000000000a1` |
| Sucursal | `CENTRAL` · `00000000-0000-4000-8000-0000000000a2` |
| Prestaciones | `CONSULTATION` 150.00 BOB · `...501` — `X_RAY` 250.00 BOB · `...502` |

Los UUID de las prestaciones son fijos a proposito: son los mismos que ms-facturacion declara en
su catalogo local, de modo que su modo `REMOTE` contra este servicio devuelve codigos y precios
identicos.

Todos los valores son ficticios. El NIT `0000000000`, el correo `.invalid` y los telefonos no
corresponden a ninguna organizacion real.

## Como probar

```bash
# Suite completa. Las pruebas de contenedor necesitan Docker activo;
# sin el se omiten en vez de fallar.
./mvnw clean test

# Levantar el servicio con su base desde la raiz del repositorio
docker compose up -d --build ms-tenencia
docker compose logs -f --tail=100 ms-tenencia

# Salud (la readiness incluye la base)
curl -s http://localhost:8082/actuator/health

# Contrato que consume ms-facturacion
curl -s -H "X-Organization-Id: 00000000-0000-4000-8000-0000000000a1" \
     http://localhost:8082/internal/v1/prestaciones/00000000-0000-4000-8000-000000000501

# Limite del plan: el plan DEMO admite una sucursal, la segunda devuelve 422
curl -s -X POST http://localhost:8082/api/v1/tenancy/sucursales \
  -H "X-Organization-Id: 00000000-0000-4000-8000-0000000000a1" \
  -H "X-Roles: ADMINISTRATOR" -H "Content-Type: application/json" \
  -d '{"code":"SUR","name":"Sucursal Sur","headquarters":false,
       "address":"Av. Demo 200","city":"La Paz","department":"LA_PAZ"}'
```

Fuera de Docker se usa el perfil `local`, que no altera la configuracion del contenedor:

```bash
DB_PASSWORD=tenencia-local ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Pruebas

| Prueba | Comprueba |
| --- | --- |
| `TenenciaApplicationTests` | Contexto, health con dependencia de base y migraciones desde base vacia |
| `SchemaConformanceContainerTest` | Las seis tablas, tipos, UNIQUE, CHECK, indices, FK solo locales y ausencia de tablas ajenas |
| `TenancyPersistenceContainerTest` | Unicidad por organizacion, limite del plan, precio DECIMAL, aislamiento entre tenants y contrato interno |
| `PlanLimitsTest` | Limite nulo = ilimitado, conversion GB→bytes y contador que nunca baja de cero |
| `TenancyPingControllerTest` | Enrutamiento del servicio |

## Limites conocidos de este sprint

- El servicio **no valida la firma del JWT**: confia en los headers que el gateway recrea desde
  claims firmados. El endurecimiento (verificar la firma tambien aqui) queda en el backlog.
- `plan_usage` es una proyeccion que otros servicios reportan por HTTP. Sin eventos ni
  outbox, un reporte perdido deja el contador desfasado hasta la siguiente reconciliacion
  (`POST /consumo` con `value`).
- `features` de `subscription_plan` y `opening_hours` de `branch` se conservan como JSON sin
  interpretar.
- En `compose.yaml` este servicio publica el puerto `8082` al host, util para depurar durante el
  Dia 1. Segun la guia, en la configuracion compartida final **solo el gateway deberia publicar
  puerto**; ese archivo pertenece a la tarea de plantilla/Compose y no se modifico aqui.
