# ms-profesionales

Autoridad de escritura de profesionales de salud, sus especialidades y sus vínculos con organizaciones. Usa su propia base `professionals_db`; los UUID de identidad, catálogos y tenencia son referencias externas, sin claves foráneas entre bases.

## Contrato MVP

- `POST /api/v1/professionals`: crea un profesional activo con matrícula única, una o más especialidades (exactamente una principal) y su vínculo activo con una organización.
- `GET /api/v1/professionals?organizationId={uuid}&specialtyCode={opcional}`: lista únicamente profesionales y vínculos activos del tenant solicitado.
- `GET /internal/v1/professionals/{id}`: contrato consumido por `ms-agenda`; responde 404 si el profesional no existe o no tiene un vínculo activo.
- `GET /actuator/health`: health check del contenedor.

Ejemplo de alta:

```json
{
  "userAccountId": "11111111-1111-1111-1111-111111111111",
  "documentType": "CI",
  "documentNumber": "DEMO-0001",
  "givenNames": "Ana",
  "paternalSurname": "Pérez",
  "professionalType": "PHYSICIAN",
  "professionalLicenseNumber": "MED-DEMO-001",
  "specialties": [{ "code": "MEDICINA_GENERAL", "primary": true, "graduationYear": 2020 }],
  "organizationId": "22222222-2222-2222-2222-222222222222",
  "modality": "STAFF",
  "startDate": "2026-08-25"
}
```

## Autorización

El gateway valida el JWT y propaga la identidad en `X-Organization-Id`, `X-User-Id` y `X-Permissions`. Cada endpoint declara el permiso que exige con `@RequiresPermission`, tomado de la hoja endpoint → permiso de RBAC-01; lo que no lo declara se deniega por defecto (RBAC-04). Sin identidad responde `401` y sin el permiso, `403`. El ping es `@PublicEndpoint` y la ruta que consume ms-agenda, `@InternalEndpoint`.

## Ejecutar y verificar

```powershell
mvn test
docker compose up -d --build mysql ms-profesionales
Invoke-RestMethod http://localhost:8086/actuator/health
```

Limitación explícita del MVP: el servicio conserva los UUID externos y no llama todavía a `ms-identidad`, `ms-catalogos` ni `ms-tenencia` para validar su existencia. Esa validación debe integrarse por contrato HTTP antes del flujo E2E final.
