# MedicalSys — revisión técnica y correcciones posteriores a la demo

**Fecha de revisión:** 27 de agosto de 2026  
**Rama revisada:** `develop` (estado local)  
**Alcance:** los diez microservicios del MVP, `ms-gateway`, migraciones Flyway, configuración Docker Compose, pruebas automatizadas y cumplimiento de `MedicalSys_Guia_Induccion_Sprint_2.pdf`.

> Este documento es una revisión del código actual, no una certificación de producción. La copia local estaba un commit detrás de `origin/develop` y tenía cambios sin confirmar, principalmente en `ms-pacientes`, `compose.yaml` y `.env.example`. Por ello, los hallazgos deben volver a comprobarse después de integrar y limpiar la rama.

## 1. Conclusión ejecutiva

El proyecto ya demuestra una arquitectura válida para un MVP local: los servicios están separados, todos tienen migraciones, el gateway conoce las rutas, varios servicios aplican roles y aislamiento por organización, y los documentos se almacenan en MinIO con verificación SHA-256.

Sin embargo, **la demo no prueba todavía una integración real de los diez servicios**. Agenda, Historial y Facturación tienen modos locales o validaciones desactivadas; al activar los modos remotos aparecen contratos HTTP incompatibles. Además, Agenda, Profesionales y Pacientes tienen problemas importantes de autorización multi-tenant.

### Correcciones que bloquean la siguiente demo integrada

1. Unificar el UUID de la organización demo entre Identidad y Tenencia.
2. Corregir los contratos Agenda → Pacientes, Facturación → Pacientes e Historial → Catálogos.
3. Activar los modos remotos de Agenda, Historial y Facturación en Compose y demostrar que no usan dobles locales.
4. Aplicar autenticación y aislamiento por tenant en Agenda, Profesionales y Pacientes.
5. Hacer que el gateway rechace rutas privadas sin JWT y respete el cierre de sesión de Identidad.
6. Reparar la configuración de pruebas con Mockito/JDK 21 y ejecutar los tests de contenedor en CI.
7. Unificar el formato de error y la propagación de `X-Correlation-Id` definidos en la guía.
8. Resolver las rutas contradictorias de la propia guía (`/api/v1/citas` frente a `/api/v1/scheduling/appointments`) y congelar una sola versión.

## 2. Prioridades usadas

| Prioridad | Significado |
|---|---|
| **P0** | Bloquea la integración, permite acceso indebido o puede comprometer datos clínicos. Corregir antes de la próxima demo integrada. |
| **P1** | Funciona para una demo controlada, pero es incorrecto o frágil para continuar desarrollando. Corregir en el siguiente sprint. |
| **P2** | Mejora de mantenibilidad, documentación, observabilidad o preparación para producción. |

## 3. Resultado de las pruebas

Se ejecutó `./mvnw test` individualmente en los once proyectos con el JDK disponible en la máquina.

| Servicio | Ejecutadas | Errores | Omitidas | Resultado |
|---|---:|---:|---:|---|
| ms-agenda | 7 | 0 | 0 | Pasa |
| ms-catalogos | 1 | 0 | 0 | Pasa, cobertura muy baja |
| ms-criptografia | 2 | 1 | 0 | Falla por configuración Mockito/Byte Buddy |
| ms-documental | 15 | 0 | 0 | Pasa |
| ms-facturacion | 42 | 6 | 18 | Fallan tests Mockito; tests de contenedor omitidos |
| ms-gateway | 1 | 1 | 0 | Falta `JWT_SECRET` en el perfil de test |
| ms-historial | 68 | 21 | 22 | Fallan tests Mockito; tests de contenedor omitidos |
| ms-identidad | 6 | 0 | 0 | Pasa |
| ms-pacientes | 10 | 0 | 0 | Pasa |
| ms-profesionales | 3 | 2 | 0 | Falla por configuración Mockito/Byte Buddy |
| ms-tenencia | 18 | 0 | 17 | Solo una prueba efectiva; contenedores omitidos |

**Interpretación:** los errores de Criptografía, Facturación, Historial y Profesionales tienen la misma causa de infraestructura de test: Mockito inline intenta adjuntar Byte Buddy dinámicamente y el JDK lo impide. Esto no demuestra una falla funcional, pero sí deja la suite inutilizable en esta máquina y posiblemente en CI.

### Corrección común de pruebas

- Configurar Surefire con el agente de Mockito recomendado para la versión usada, o cambiar al mock maker `subclass` si no se necesita simular clases finales.
- Crear valores seguros exclusivos de test para `JWT_SECRET`, bases y claves demo.
- No considerar una prueba de Testcontainers “aprobada” cuando aparece como `Skipped`.
- Añadir un pipeline que ejecute: compilación, tests unitarios, migraciones sobre MySQL/PostgreSQL reales, tests de contrato y una prueba E2E del flujo principal.

## 4. Correcciones transversales

### INT-01 — Los modos locales ocultan integraciones rotas — **P0**

En el Compose actual no se configuran `SCHEDULING_VALIDATION_ENABLED`, `PATIENTS_MODE`, `TENANCY_MODE`, `CRYPTO_MODE` ni `CATALOGS_VALIDATION_MODE`. Sus valores por defecto permiten que cada servicio simule dependencias.

**Corrección:** crear dos perfiles explícitos:

- `demo-local`: permite dobles locales y los marca claramente en logs/respuestas.
- `integrated`: activa validación de Agenda y clientes remotos de Historial y Facturación. La próxima demo técnica debe usar este perfil.

**Criterio de aceptación:** detener Pacientes, Catálogos, Criptografía o Tenencia debe hacer fallar de forma controlada el caso de uso dependiente; no debe continuar con datos inventados.

### INT-02 — No existe una fuente única de contratos — **P0**

Actualmente los endpoints y DTO internos están duplicados manualmente. Ya divergen en tres integraciones.

**Corrección:** versionar OpenAPI por servicio en `docs/contracts`, congelar las rutas `/internal/v1`, y añadir tests de contrato del consumidor. No es necesario compartir entidades JPA ni crear una librería de dominio común.

**Criterio de aceptación:** el pipeline detecta un cambio incompatible antes de hacer merge.

### SEC-01 — Los puertos internos quedan publicados al host — **P0/P1**

Compose publica el puerto de cada microservicio. Los servicios confían en `X-Organization-Id`, `X-User-Id` y `X-Roles`; entrar directamente al puerto evita el filtro anti-suplantación del gateway.

**Corrección:** en el perfil integrado publicar únicamente el gateway; dejar los puertos individuales en un perfil `debug` enlazados a `127.0.0.1`. Proteger `/internal/v1/**` con credenciales entre servicios o, como mínimo para el MVP, con aislamiento de red y sin exposición al host.

### OPS-01 — Falta una forma única de levantar y comprobar la plataforma — **P1**

- Añadir healthchecks a los microservicios y dependencias saludables al gateway.
- Fijar versiones de `minio/minio` y `minio/mc`; no usar `latest`.
- Completar el `README.md` raíz, que actualmente está vacío.
- Documentar comandos de arranque, perfil utilizado, usuario demo, recorrido E2E y apagado limpio.
- Añadir un script de smoke test que consulte salud y un endpoint funcional de cada servicio.

### GUIDE-01 — Auditoría explícita contra la Guía de Inducción — **P0/P1**

Se comparó el código con [MedicalSys_Guia_Induccion_Sprint_2.pdf](<../MedicalSys_Guia_Induccion_Sprint_2 (1).pdf>), en particular las secciones 3.4, 3.5, 4.1-4.6, 5.1-5.4, 6.2-6.4, 9, 11, 13 y el Anexo B.

La guía **no contiene un catálogo completo de todos los requests y responses de negocio**. Define convenciones comunes, algunos DTO mínimos y ejemplos de rutas. Los contratos completos debían quedar en `docs/contracts` y en el Excel. Por tanto, esta auditoría distingue entre:

- incumplimientos claros de una regla de la guía;
- ejemplos ambiguos o contradictorios que primero requieren una decisión documental;
- contratos entre servicios descubiertos directamente en el código.

Durante la revisión, `docs/contracts` no contenía ningún archivo. En consecuencia, hoy no existe en el repositorio una especificación normativa completa de los JSON exitosos. Los DTO Java permiten conocer lo implementado, pero no sustituyen un contrato acordado con sus consumidores.

#### Matriz de puertos, bases y usuarios

| Componente | Guía | Código/Compose | Estado |
|---|---|---|---|
| Gateway | 8080 | 8080 | Cumple en puerto |
| ms-identidad | 8081, `identity_db`, `identity_user` | Coincide | Cumple |
| ms-tenencia | 8082, `tenancy_db`, `tenancy_user` | Coincide | Cumple |
| ms-catalogos | 8083, `catalogs_db`, `catalogs_user` | Coincide | Cumple |
| ms-criptografia | 8084, `cryptography_db`, `cryptography_user` | Coincide | Cumple |
| ms-pacientes | 8085, `patients_db`, `patients_user` | Coincide | Cumple |
| ms-profesionales | 8086, `professionals_db`, `professionals_user` | Coincide | Cumple |
| ms-agenda | 8087, `scheduling_db`, `scheduling_user` | Coincide y usa PostgreSQL | Cumple |
| ms-historial | 8088, `medical_records_db`, `medical_records_user` | Coincide | Cumple |
| ms-documental | 8089, `documents_db`, `documents_user` | Coincide | Cumple |
| ms-facturacion | 8090, `billing_db`, `billing_user` | Coincide | Cumple |

Todos los proyectos usan Java 21 y Spring Boot 4.1.1, cumpliendo la versión común. También poseen Maven Wrapper, Dockerfile multi-stage, usuario no root y `.dockerignore`; los diez servicios con persistencia poseen migraciones Flyway, mientras que el gateway no necesita migración.

#### Incumplimientos de Compose respecto a las páginas 13 y 24

1. La guía exige tres redes: `medicalsys-borde`, `medicalsys-interna` y `medicalsys-datos`, con las dos últimas `internal: true`. `compose.yaml` no declara ninguna red explícita; todos los componentes quedan en la red por defecto.
2. La guía exige que solo el gateway publique `8080`. Compose publica los diez servicios, MySQL, PostgreSQL, Redis, API y consola de MinIO.
3. La guía llama al componente `api-gateway` y define `http://api-gateway:8080`; Compose lo llama `ms-gateway`. El puerto es correcto, pero el nombre DNS no cumple literalmente. Conviene conservar `ms-gateway` y actualizar la guía, o renombrarlo de forma coordinada.
4. La guía pide `depends_on: condition: service_healthy` y healthchecks de aplicación. Solo los motores tienen healthcheck; ningún microservicio ni el gateway lo declara en Compose.
5. No se configuraron límites/rotación de logs, recomendados para el Compose de equipo.

#### Rutas ambiguas o incompatibles con la guía

La propia guía mezcla convenciones:

- En el diagrama de la página 15 y el ejemplo de error de la página 16 usa `POST /api/v1/citas`.
- En la página 18 usa `/api/v1/patients` y `/internal/v1/patients/{id}`.
- En otros fragmentos textuales aparece `/api/v1/pacientes`.

El código implementa `POST /api/v1/scheduling/appointments` y `/api/v1/patients`. Por ello:

- `/api/v1/citas` devuelve 404 en el gateway actual;
- la ruta inglesa de Pacientes coincide con la tabla de la página 18;
- no debe añadirse una tercera ruta por comodidad sin decidir primero el contrato oficial.

**Corrección:** crear una tabla normativa en `docs/contracts/routes-v1.md` con método, ruta, productor, consumidor y rol. Actualizar la guía o implementar aliases temporales con fecha de retiro. Los scripts, Postman y pruebas deben usar solo la ruta congelada.

#### Formato de error común

La guía exige exactamente los campos base:

```json
{
  "timestamp": "2026-08-24T18:03:51Z",
  "status": 409,
  "code": "APPOINTMENT_OVERLAP",
  "message": "El profesional ya tiene una cita en esa franja",
  "correlationId": "uuid",
  "path": "/api/v1/citas"
}
```

| Servicio | Estado del contrato de error |
|---|---|
| ms-tenencia, ms-historial, ms-facturacion, ms-documental | Cumplen los seis campos; agregan `details` opcional |
| ms-catalogos, ms-criptografia | Cumplen los seis campos |
| ms-pacientes | Tiene el formato correcto, pero posee dos filtros de correlación, dos `ApiError` y dos `GlobalExceptionHandler` registrados |
| ms-agenda, ms-identidad | Devuelven RFC 7807 `ProblemDetail` (`title`, `detail`, etc.); falta `code`, `message` y `path` con el contrato acordado |
| ms-profesionales | Solo devuelve `code` y `message`; faltan timestamp, status, correlationId y path |
| ms-gateway | El 401 del filtro JWT se escribe manualmente solo con `title` y `detail` |

**Corrección:** adoptar un único schema base y testearlo en los once proyectos. `details` puede ser una extensión opcional; no debe reemplazar los seis campos. El gateway debe preservar el status y correlation ID del downstream.

#### Correlation ID y saltos internos

- Profesionales no tiene filtro/interceptor de `X-Correlation-Id`.
- Agenda recibe el header, pero `ExternalDirectoryClient` no lo reenvía a Pacientes/Profesionales.
- Agenda declara `SCHEDULING_VALIDATION_TIMEOUT`, pero el cliente no lo aplica a la fábrica HTTP.
- Historial y Facturación sí configuran timeouts y reenvían correlation ID en sus `RestClient`.
- Pacientes tiene dos filtros distintos para el mismo header; pueden generar IDs diferentes para una misma solicitud.
- El gateway reutiliza cualquier correlation ID no vacío sin validar longitud/formato y su 401 no mantiene el contrato común.

**Criterio de aceptación:** un UUID enviado o generado en el gateway aparece sin cambios en response, error y logs de todos los saltos de una misma operación.

#### JWT, roles y prefijos públicos

- Los claims técnicos `sub`, `organizationId`, `roles`, `iat`, `exp` e `iss` sí son emitidos por Identidad con la forma conceptual de la guía.
- La guía nombra roles demo `ADMIN`, `RECEPCIONISTA` y `PROFESIONAL`; el código congela `ADMINISTRATOR`, `RECEPTIONIST` y `PHYSICIAN`. Esta traducción es válida técnicamente, pero es un contrato distinto: Postman, frontend y documentación deben usar los códigos ingleses o el equipo debe cambiarlos coordinadamente.
- La guía exige Bearer token, rol y tenant en `/api/v1`, salvo login/health acordados. Catálogos carece de seguridad y Gateway permite todas las rutas sin token; Agenda y Profesionales tampoco la aplican. Deben protegerse o documentarse como excepciones concretas.
- El gateway enruta `/api/v1/cryptography/**`, pero Criptografía solo expone `/internal/v1/crypto/**`. Esa ruta pública no llega a ningún controlador y contradice la intención de que el cifrado sea interno.

#### Readiness y salud

La guía exige `/actuator/health`, probes y que readiness incluya la base. Solo Tenencia, Historial y Facturación configuran explícitamente probes/readiness con DB. Agenda, Catálogos, Criptografía, Documental, Identidad, Pacientes, Profesionales y Gateway exponen salud, pero no cumplen toda la configuración de readiness acordada.

**Corrección:** normalizar `management.endpoint.health.probes.enabled`, grupos liveness/readiness y healthchecks de Compose. Exponer solo salud básica; varios servicios usan actualmente `show-details: always`.

#### Variables que no coinciden con el Anexo B

| Guía | Implementación | Acción |
|---|---|---|
| `JWT_KEY` | `JWT_SECRET` | Congelar un nombre y actualizar guía/Compose |
| `REDIS_URL` | `REDIS_HOST`/`REDIS_PORT` y overrides Spring | Documentar la forma elegida |
| `MINIO_URL` | `MINIO_ENDPOINT` | Congelar un nombre |
| `SIN_MODE=DEMO` | `TAX_AUTHORITY_MODE=SIMULATED` | Actualizar la guía o soportar alias de configuración |
| `CATALOGS_BASE_URL` en Profesionales | No existe cliente/configuración | Implementar junto con PRO-03 |

`PATIENTS_BASE_URL`, `PROFESSIONALS_BASE_URL`, `CRYPTO_BASE_URL`, `CATALOGS_BASE_URL` de Historial y `TENANCY_BASE_URL` existen en los `application.yaml`, pero Compose no los declara ni activa los modos remotos. Los defaults DNS son correctos, aunque la integración permanece apagada.

#### DTO mínimos mencionados por la guía

- Pacientes → Agenda: `patientId`, nombre y estado. `SchedulingPatientSummary` cumple, pero Agenda llama otra ruta.
- Pacientes → Facturación: ID, nombre y documento. Pacientes devuelve esos datos, pero nombres/ruta no coinciden con `FiscalCustomer`.
- Documental → Historial: `documentId` y hash. `DocumentResponse` e `InternalDocumentSummary` cumplen.
- Profesionales → Agenda: ID, organización, especialidad y activo. El endpoint interno devuelve el `ProfessionalResponse` público completo, con listas y datos adicionales; no ofrece una proyección mínima tenant-scoped.
- Factura demo: `InvoiceResponse` sí incluye `demo`, `caption`, correlativo y totales `BigDecimal`, cumpliendo la guía.

## 5. Correcciones por microservicio

## 5.1 ms-identidad — Álvaro

### ID-01 — Un administrador puede operar sobre otra organización — **P0**

`UserController` acepta `organizationPublicId` en búsqueda y creación, y las operaciones por `publicId` no comparan la organización del actor con la del usuario objetivo. Tener el permiso global `identity.user.*` basta para consultar o modificar otra clínica.

**Corrección:** obtener la organización del JWT en todas las operaciones y limitar repositorios y comandos a ella. Solo `PLATFORM_ADMIN` debe poder cruzar tenants, mediante una decisión explícita y auditada.

**Criterio de aceptación:** un administrador de organización A recibe 404/403 al buscar, leer, editar o asignar roles a un usuario de B.

### ID-02 — Cerrar sesión no invalida el token en el gateway — **P0**

Identidad elimina la sesión/JTI en Redis al cerrar sesión, bloquear un usuario o cambiar contraseña. El gateway solo valida firma y expiración, por lo que ese JWT todavía sirve para llamar a los demás servicios hasta que expire.

**Corrección:** validar JTI/sesión en el gateway, usar introspección, o adoptar access tokens muy cortos con refresh token revocable. Mantener una sola estrategia documentada.

### ID-03 — La organización demo no coincide con Tenencia — **P0**

Identidad usa por defecto `11111111-1111-1111-1111-111111111111`; Tenencia siembra la clínica demo como `00000000-0000-4000-8000-0000000000a1`. Compose no pasa `IDENTITY_DEMO_ORGANIZATION`.

**Corrección:** declarar el UUID una sola vez en `.env.example` y pasarlo a ambos seeders/configuraciones.

### ID-04 — Configuración demo y Actuator — **P1**

- Los usuarios demo y su contraseña conocida deben existir únicamente con perfil `local/demo`, nunca activados por defecto.
- Cambiar `health.show-details: always` por `when_authorized` o exponer solo liveness/readiness.
- Añadir validación de `issuer` al decoder JWT, no solo firma/algoritmo.

### ID-05 — Pruebas insuficientes — **P1**

Agregar pruebas de aislamiento entre tenants, asignación de roles, expiración/revocación JTI, bloqueo, cambio de contraseña y acceso a `/internal/v1`.

## 5.2 ms-tenencia — Franco

### TEN-01 — Alinear el tenant demo — **P0**

Aplicar la corrección ID-03 y hacer que el smoke test compruebe que el `organizationId` del token existe en Tenencia antes de continuar.

### TEN-02 — Endpoints internos basados solo en headers — **P1**

El aislamiento por organización está bien aplicado en repositorios y servicios, pero `/internal/v1/**` confía en headers que serían falsificables si se accede al puerto directamente.

**Corrección:** retirar la publicación externa del puerto y autenticar llamadas servicio-a-servicio. Conservar el filtrado actual por organización.

### TEN-03 — Los tests de persistencia no se ejecutaron — **P1**

Diecisiete pruebas quedaron omitidas porque Testcontainers no tuvo Docker disponible durante la ejecución. Hacerlas obligatorias en CI y probar Flyway, restricciones únicas, scoping de organización y concurrencia.

### TEN-04 — Contratos internos — **P1**

Añadir tests de consumidor para:

- `GET /internal/v1/prestaciones/{id}` usado por Facturación.
- `GET /internal/v1/prestaciones/{id}/agendable` que debe usar Agenda.
- Referencias de organización y sucursal.

## 5.3 ms-catalogos — Juanjo

### CAT-01 — El catálogo de medicamentos está solo en la base — **P0**

La migración crea `medication`, pero no existen entidad, repositorio, servicio ni endpoint. Historial llama a `/internal/v1/medicamentos/{code}` y siempre fallará en modo remoto.

**Corrección:** implementar el endpoint de medicamento activo o acordar una nueva ruta inglesa y cambiar ambos lados. Añadir datos demo para los códigos usados en recetas.

### CAT-02 — Ruta de tipos de estudio incompatible — **P0**

Historial llama `/internal/v1/tipos-estudio/{code}`; Catálogos expone `/internal/v1/study-types/{code}`.

**Corrección:** escoger una sola convención. Para evitar romper consumidores, se recomienda mantener la ruta inglesa existente y adaptar Historial; cualquier cambio posterior debe ir a `/internal/v2`.

### CAT-03 — Seeds incompletos — **P1**

El seed incluye `X_RAY`, pero no los medicamentos locales de Historial ni `LAB_HEMOGRAM`. Sembrar exactamente los códigos usados por la demo integrada y comprobarlos con tests.

### CAT-04 — Cobertura y seguridad — **P1**

Solo se ejecutó una prueba. Añadir pruebas por catálogo, búsquedas/paginación, códigos inactivos y contratos internos. Evitar publicar el puerto directamente al host. Definir autenticación para `/api/v1/catalogs/**` o registrar explícitamente la excepción a la regla de la guía; actualmente no existe seguridad.

## 5.4 ms-criptografia — Juanjo

### CRY-01 — Las claves por tenant son solo metadatos — **P0**

`resolveActiveKey` selecciona una fila por organización, pero `aesKey()` ignora `wrapped_dek` y deriva siempre la misma clave global desde `DEMO_MASTER_KEY`. En la práctica, todas las organizaciones cifran con la misma clave.

**Corrección:** para el MVP integrado, derivar una subclave distinta por tenant y versión mediante HKDF, o desenvolver el DEK de la fila. Para producción, usar Vault/KMS. El ciphertext debe registrar versión/key ID suficiente para descifrar tras una rotación.

### CRY-02 — Falta vincular el ciphertext a su contexto — **P1**

AES-GCM no usa AAD con organización, propósito y versión. Añadir esos valores como datos autenticados para impedir mover un ciphertext válido a otro contexto.

### CRY-03 — La auditoría de descifrado fallido puede revertirse — **P1**

El fallo se registra dentro de la misma transacción que luego lanza la excepción. Verificar y, si corresponde, guardar `key_usage` fallido con `REQUIRES_NEW` para que sobreviva al rollback.

### CRY-04 — Validación y acceso interno — **P1**

Los records de entrada no usan Jakarta Validation. Un propósito, UUID, hexadecimal o Base64 inválido termina en el handler genérico como 500.

**Corrección:** validar DTOs, mapear entrada inválida a 400 y autenticar al servicio solicitante. No exponer el oráculo de cifrado/descifrado al host. Eliminar la ruta pública inútil `/api/v1/cryptography/**` del gateway, salvo que se diseñe deliberadamente una API pública distinta.

### CRY-05 — El contrato remoto con Historial no coincide — **P0**

Criptografía expone `/internal/v1/crypto/encrypt` y `/decrypt`. Exige `organizationId`, `purpose`, `requestingService` y `correlationId`, y al cifrar responde únicamente `ciphertext`. Historial llama `/internal/v1/cifrado` y `/descifrado`, usa otro body y espera además `keyPublicId`.

**Corrección:** acordar un solo contrato versionado. La respuesta de cifrado debe incluir el identificador/version de clave que Historial persiste. Añadir test de contrato productor-consumidor y una prueba de cifrar/reiniciar/descifrar.

### CRY-06 — Tests — **P1**

Reparar Mockito y agregar pruebas que demuestren aislamiento entre organizaciones, AAD, rotación y persistencia de auditoría en éxito/fallo.

## 5.5 ms-pacientes — Nicole

### PAT-01 — El paciente no pertenece a ninguna organización — **P0**

El filtro exige un `X-Organization-Id`, pero el modelo, tabla y consultas de `Patient` no guardan ni filtran ese valor. Un usuario autorizado de A puede leer al paciente de B si conoce su UUID o documento.

**Corrección de diseño:** decidir si el paciente es global con vinculaciones por organización o si pertenece a un tenant. La opción más flexible es `patient` global + tabla `patient_organization` con estado, número de historia local y fechas. Todas las lecturas deben comprobar una vinculación activa con el tenant del JWT.

### PAT-02 — Contratos internos incompatibles — **P0**

- Agenda llama `GET /internal/v1/patients/{id}`; Pacientes expone `/{id}/scheduling-summary`.
- Facturación llama `/internal/v1/pacientes/{id}/fiscal`; Pacientes expone `/internal/v1/patients/{id}/billing-summary`.
- El DTO de Pacientes devuelve `fullName` y `status`; Facturación espera `legalName` y `email`. La deserialización remota deja `legalName` nulo y falla.

**Corrección:** congelar los dos DTOs y rutas en OpenAPI, adaptar consumidores y añadir tests de contrato reales.

### PAT-03 — Apellido materno inconsistente — **P1**

La base y entidad permiten `maternal_surname` nulo, pero `CreatePatientRequest` usa `@NotBlank` y `Patient.create` exige no nulo. Permitir nulo/vacío y normalizarlo coherentemente; no todas las personas poseen apellido materno.

### PAT-04 — Alcance funcional mínimo — **P1**

El servicio solo crea y consulta; no actualiza contacto/estado ni pagina búsquedas. Acordar si esto queda fuera del Sprint 2. Si se incluye, añadir actualización parcial, desactivación y búsqueda paginada sin exponer datos innecesarios.

### PAT-05 — Tipos de documento — **P2**

El enum/esquema contemplan varios tipos, pero el servicio rechaza todos excepto `CI`. Es aceptable como restricción MVP si el contrato y la interfaz lo indican; de lo contrario, implementar los tipos declarados.

### PAT-06 — Infraestructura web duplicada — **P1**

Existen dos `CorrelationIdFilter`, dos `ApiError` y dos `GlobalExceptionHandler` en paquetes diferentes, todos registrados como componentes/advice. Esto puede producir dos correlation IDs o handlers distintos según el error.

**Corrección:** conservar una sola implementación común y probar que header de respuesta, cuerpo de error y MDC contienen el mismo valor.

## 5.6 ms-profesionales — Jhon

### PRO-01 — No existe autenticación ni aislamiento por tenant — **P0**

Crear y listar profesionales no requiere roles. El cliente envía `organizationId` y `userAccountId` en el body/query, por lo que puede escoger cualquier organización.

**Corrección:** implementar el mismo contexto confiable usado por Tenencia/Historial. Derivar organización y usuario del JWT; aplicar roles a creación, edición y consulta.

### PRO-02 — Validación interna no considera la organización — **P0**

`findActive(id)` acepta al profesional si tiene alguna relación activa, aunque sea con otra clínica. Agenda podría agendar un profesional de B dentro de A.

**Corrección:** exigir `X-Organization-Id` y buscar `professionalId + organizationId + vínculo activo`.

### PRO-03 — Referencias externas sin validar — **P1**

Antes de crear, validar que el usuario exista en Identidad, la organización/sucursal en Tenencia y los códigos de especialidad en Catálogos. No crear FK entre bases; usar contratos HTTP y manejar 404/503.

### PRO-04 — Ciclo de vida y pruebas — **P1**

Agregar actualización, activación/desactivación, mantenimiento de vínculos y especialidades si forman parte del alcance. Reparar Mockito y probar multi-tenancy, licencia duplicada y vínculo activo.

### PRO-05 — DTO, errores y trazabilidad no cumplen la guía — **P1**

El endpoint interno entrega el DTO público completo en lugar de la proyección mínima para Agenda. Además, el servicio no tiene filtro de correlation ID y su error solo contiene `code`/`message`.

**Corrección:** crear `InternalProfessionalSummary` con profesional, organización, especialidad/estado mínimos; agregar filtro de correlación y el JSON de error común.

## 5.7 ms-agenda — Álvaro

### AGE-01 — El tenant y el actor llegan desde el body/query — **P0**

Los controladores no tienen autorización. `organizationPublicId` y `createdByUserPublicId` provienen del cliente; lecturas/cambios por UUID tampoco comprueban la organización. Esto permite crear citas en otro tenant y leer/cambiar citas ajenas.

**Corrección:** añadir contexto de gateway y roles. El tenant y usuario deben venir de claims; todas las consultas por cita/recurso/disponibilidad/reserva deben filtrar por organización.

### AGE-02 — La validación de Pacientes apunta a una ruta inexistente — **P0**

Cambiar la llamada a `/internal/v1/patients/{id}/scheduling-summary` o adaptar el contrato acordado. Activar `SCHEDULING_VALIDATION_ENABLED=true` en el perfil integrado.

### AGE-03 — Validación externa incompleta — **P1**

Además de paciente y profesional, validar:

- que el profesional está activo en la misma organización;
- que sucursal y prestación pertenecen a ella;
- que la prestación es agendable y su duración coincide;
- que el recurso físico pertenece a esa sede.

Utilizar el endpoint agendable de Tenencia ya existente.

### AGE-04 — Auditoría falsificable — **P1**

Los IDs de quien crea/cambia estados vienen en solicitudes. Reemplazarlos por `X-User-Id`; conservar el motivo como dato del body.

### AGE-05 — Tests — **P1**

Siete pruebas son pocas para el tamaño del servicio. Añadir transiciones, solapamientos concurrentes, huso horario, aislamiento tenant, reprogramación, recursos y fallos de dependencias.

### AGE-06 — Ruta pública, error y cliente HTTP divergen de la guía — **P0/P1**

- La guía muestra `POST /api/v1/citas`; el código usa `/api/v1/scheduling/appointments`.
- Agenda responde errores como `ProblemDetail`, no con los seis campos comunes.
- `ExternalDirectoryClient` no reenvía `X-Correlation-Id` ni contexto de organización.
- La propiedad de timeout existe, pero no se aplica al `RestClient`.

**Corrección:** congelar la ruta oficial, unificar errores y configurar cliente con connect/read timeout e interceptores de headers confiables.

## 5.8 ms-historial — Franco

### HIS-01 — Cifrado local efímero en Compose — **P0**

Historial usa `CRYPTO_MODE=LOCAL` por defecto y permite `DEMO_MASTER_KEY` vacío. En ese caso genera una clave nueva al arrancar; después de reiniciar, la narrativa clínica almacenada deja de poder descifrarse. Compose pasa `DEMO_MASTER_KEY` a `ms-criptografia`, pero no a `ms-historial`.

**Corrección:** en perfil integrado usar `CRYPTO_MODE=REMOTE` y URL de Criptografía. Si se conserva modo local, fallar el arranque cuando la clave no está definida; nunca generar una clave efímera para datos persistidos.

### HIS-02 — Modo remoto de Catálogos está roto — **P0**

Corregir las rutas descritas en CAT-01/CAT-02 y activar `CATALOGS_VALIDATION_MODE=REMOTE`. Los códigos locales no deben ser una segunda fuente de verdad silenciosa.

### HIS-02B — Modo remoto de Criptografía también está roto — **P0**

Además del problema de clave local, las rutas y JSON descritos en CRY-05 no coinciden. Activar `CRYPTO_MODE=REMOTE` antes de corregirlos hará que la creación/lectura de narrativa falle.

### HIS-03 — Encuentros y documentos no validan sus referencias — **P1**

Al abrir un encuentro solo se evita duplicar `appointmentId`; no se consulta Agenda para comprobar cita, paciente, profesional, tenant y estado. Al adjuntar un resultado se guarda `documentPublicId` sin consultar Documental.

**Corrección:** consumir los endpoints internos de Agenda y Documental, verificando organización y estado. Añadir timeouts y devolver 422/503 según corresponda.

### HIS-04 — Seguridad interna — **P1**

El scoping por tenant en repositorios está bien aplicado. Mantenerlo, pero cerrar el acceso directo al puerto y autenticar `/internal/v1`.

### HIS-05 — Pruebas — **P0/P1**

La suite es amplia, pero 21 pruebas fallaron por Mockito y 22 fueron omitidas. Reparar la infraestructura antes de cambiar lógica; luego ejecutar nuevamente para descubrir fallos funcionales reales. Hacer obligatorias las pruebas del trigger de inmutabilidad clínica.

## 5.9 ms-documental — Nicole

### DOC-01 — Un archivo se marca disponible sin verificar su contenido — **P1**

El MIME se toma del header del cliente; `application/octet-stream` se acepta para imágenes/DICOM. El documento queda inmediatamente `AVAILABLE` y `antivirus_status=NOT_APPLICABLE`.

**Corrección:** detectar el tipo por magic bytes, limitar formatos y usar `UPLOAD_PENDING/PENDING` hasta escaneo o validación. Para el MVP local, si no habrá antivirus, documentar explícitamente la limitación y no permitir tipos ejecutables/genéricos.

### DOC-02 — La aplicación usa credenciales root de MinIO — **P1**

Compose entrega `MINIO_ROOT_USER/PASSWORD` al microservicio, y este intenta crear el bucket en cada upload aunque `minio-init` ya lo aprovisiona.

**Corrección:** crear un usuario/policy de aplicación con acceso solo al bucket; aprovisionar el bucket una vez y retirar `ensureBucket()` del camino de cada solicitud.

### DOC-03 — Posibles objetos huérfanos — **P1**

Si falla la base tras subir a MinIO se intenta borrar el objeto, pero un error de borrado solo se registra. Añadir un estado/outbox o tarea de reconciliación que elimine objetos sin metadata persistida.

### DOC-04 — Cifrado, metadata y privacidad — **P1/P2**

- El modelo registra `server_side_encryption=NONE`; no afirmar cifrado en reposo hasta configurarlo.
- Limitar tamaño y profundidad del JSON `metadata`, no solo cantidad de campos.
- Verificar que logs y errores nunca incluyan nombres clínicos o contenido.
- Crear README propio con política de archivos, tamaño, retención y limitaciones del MVP.

### DOC-05 — Nombre de bucket y variable distintos a la guía — **P2**

La guía menciona bucket `medicalsys-demo` y variable `MINIO_URL`; el repositorio usa `medicalsys-documents-local` y `MINIO_ENDPOINT`. El nombre implementado es más descriptivo, pero la documentación y los ejemplos deben coincidir para evitar configuraciones paralelas.

## 5.10 ms-facturacion — Franco

### FAC-01 — La integración con Pacientes falla en modo remoto — **P0**

Corregir la ruta y DTO descritos en PAT-02. Activar `PATIENTS_MODE=REMOTE` en el perfil integrado.

### FAC-02 — Tenencia también queda simulada por defecto — **P0/P1**

Compose no activa `TENANCY_MODE=REMOTE`, por lo que una factura puede usar prestaciones inventadas localmente. Activarlo y comprobar precio/servicio contra el tenant real.

### FAC-03 — Emisión no idempotente — **P1**

El propio controlador advierte que repetir un POST consume otro correlativo y crea otra factura. Agregar `Idempotency-Key` con restricción única por organización y devolver la factura original al repetir una petición equivalente.

### FAC-04 — Validaciones de trazabilidad — **P1**

Cuando se informa `appointmentId` o `clinicalEncounterId`, validar que existan, pertenezcan a la organización/paciente y no hayan sido facturados indebidamente.

### FAC-05 — SIN simulado y pruebas — **P1**

Mantener la autoridad tributaria simulada, pero marcarla de forma visible en respuesta, UI y PDF. Reparar Mockito; ejecutar los 18 tests de contenedor omitidos y probar concurrencia del correlativo e idempotencia.

## 5.11 ms-gateway — responsable de integración

### GAT-01 — Typo en la URL predeterminada de Identidad — **P0**

El valor por defecto es `htto://localhost:8081`. Compose lo sobrescribe, pero ejecutar el gateway fuera de Compose rompe la ruta. Cambiarlo a `http://localhost:8081` y agregar un test de carga de configuración.

### GAT-02 — Política `permitAll` deja la seguridad a servicios incompletos — **P0**

El filtro elimina headers confiables falsificados y valida un token cuando está presente; eso está bien. Pero si no hay token, el gateway permite continuar. Como Agenda, Profesionales y varios endpoints internos no se protegen, quedan públicos.

**Corrección:** definir una matriz mínima por rutas:

- públicas: login, salud y pings decididos explícitamente;
- autenticadas: todas las rutas funcionales del MVP;
- internas: no deben enrutarse desde Internet.

La autorización fina por rol debe permanecer también en cada servicio.

### GAT-03 — Validación JWT incompleta — **P0/P1**

- Validar `issuer` y claims obligatorios además de firma/expiración.
- Resolver la revocación de sesión descrita en ID-02.
- A medio plazo, preferir firma asimétrica/JWKS para no compartir el secreto HS256 con el gateway.

### GAT-04 — Correlation ID y disponibilidad — **P1**

- Validar formato y longitud de `X-Correlation-Id` antes de reenviarlo.
- Añadir timeout, respuesta uniforme y métricas por ruta.
- Agregar healthcheck del gateway y readiness basado en configuración; no es necesario que todas las dependencias estén siempre disponibles para arrancar, pero el estado debe ser visible.

### GAT-05 — Test roto y cobertura mínima — **P0/P1**

El único test falla porque no define `JWT_SECRET`. Crear `application-test.yaml` y agregar pruebas de: ruta correcta, token válido/expirado, token revocado, eliminación de headers falsificados, claims faltantes y servicio destino no disponible.

### GAT-06 — Nombre, redes y errores no cumplen la guía — **P0/P1**

La guía denomina al servicio `api-gateway`, requiere tres redes y un único puerto publicado. Compose usa `ms-gateway`, red por defecto y publica todos los componentes. El 401 generado por `JwtPropagationFilter` tampoco usa el error común ni garantiza el correlation ID en la respuesta.

**Corrección:** aplicar GUIDE-01 y agregar tests sobre la topología resultante con `docker compose config`.

## 6. Orden recomendado de trabajo

### Primera tanda — integración que realmente funcione

1. **Álvaro:** AGE-01, AGE-02 e ID-03.
2. **Nicole:** PAT-01 y PAT-02.
3. **Juanjo:** CAT-01, CAT-02 y CRY-01.
4. **Franco:** HIS-01, HIS-02, FAC-01 y FAC-02.
5. **Jhon:** PRO-01 y PRO-02.
6. **Gateway/integración:** GAT-01, GAT-02, GAT-05 y perfil `integrated`.

### Segunda tanda — seguridad y confiabilidad

ID-01/ID-02, validaciones externas de Agenda/Historial/Facturación, idempotencia de factura, seguridad de archivos y autenticación de tráfico interno.

### Tercera tanda — calidad sostenible

Pipeline, Mockito/Testcontainers, contratos OpenAPI, READMEs, healthchecks, versiones de imágenes y observabilidad.

## 7. Definition of Done para la próxima revisión

El incremento se considera integrado cuando:

- [ ] Los once proyectos compilan y sus pruebas terminan sin errores ni omisiones inesperadas.
- [ ] `docker compose config --quiet` pasa y el perfil integrado levanta los diez servicios, gateway y dependencias.
- [ ] Solo el gateway está expuesto en el perfil integrado.
- [ ] Compose declara `medicalsys-borde`, `medicalsys-interna` y `medicalsys-datos` según la guía.
- [ ] El mismo `organizationId` se usa en Identidad, Tenencia y datos demo.
- [ ] La demo registra un paciente y Agenda lo valida realmente mediante HTTP.
- [ ] Agenda valida profesional, sucursal y prestación del mismo tenant.
- [ ] Historial valida la cita y cifra mediante `ms-criptografia` remoto.
- [ ] Historial valida CIE-10, estudio y medicamento mediante `ms-catalogos` remoto.
- [ ] Documental almacena el estudio, verifica hash y Historial valida el documento.
- [ ] Facturación obtiene paciente y precio mediante Pacientes/Tenencia remotos.
- [ ] Un usuario de organización A no puede leer ni modificar datos de B.
- [ ] Un token cerrado/revocado deja de funcionar en el gateway.
- [ ] Repetir la emisión con la misma clave de idempotencia no duplica factura.
- [ ] Todos los errores respetan `timestamp`, `status`, `code`, `message`, `correlationId` y `path`.
- [ ] Un único correlation ID se conserva en todos los saltos internos.
- [ ] Todos los servicios poseen liveness/readiness y Compose reporta su healthcheck.
- [ ] El recorrido E2E parte de volúmenes limpios y se ejecuta sin editar datos manualmente.

## 8. Aspectos correctos que deben conservarse

- Migraciones Flyway separadas por servicio y `ddl-auto: validate`.
- Bases de datos separadas y referencias entre servicios mediante UUID públicos, sin FK entre bases.
- Eliminación de headers de identidad enviados por el cliente en el gateway.
- Scoping por organización ya aplicado en Tenencia, Historial, Facturación y Documental.
- Restricciones de transición e inmutabilidad clínica en Agenda/Historial.
- Hash SHA-256 y verificación de integridad al descargar documentos.
- Compensación inicial entre MinIO y base de datos.
- Separación explícita del SIN simulado para el MVP local.

Estas bases son buenas; las correcciones propuestas buscan que el flujo que ya se mostró en la demo sea reproducible, seguro entre tenants y verdaderamente distribuido, no reconstruir el proyecto desde cero.
