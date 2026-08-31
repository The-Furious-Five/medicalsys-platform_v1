# Error HTTP v1 y política de correlación

Todos los servicios que participan en el Sprint deben responder errores JSON con estos seis campos:

```json
{
  "timestamp": "2026-08-29T21:00:00Z",
  "status": 404,
  "code": "ICD10_NOT_FOUND",
  "message": "El codigo CIE-10 no existe en el catalogo vigente",
  "correlationId": "11111111-2222-4333-8444-555555555555",
  "path": "/internal/v1/cie10/NO_EXISTE"
}
```

## Política `X-Correlation-Id`

1. Si la petición trae un valor no vacío de hasta 128 caracteres, el servicio lo conserva.
2. Si falta, está vacío o supera el límite, el servicio genera un UUID nuevo.
3. El mismo valor se devuelve en el header `X-Correlation-Id` para respuestas exitosas y errores.
4. Los clientes internos deben reenviar el header en cada salto y registrarlo en logs sin incluir
   texto clínico, ciphertext ni secretos.

`details` puede añadirse como extensión; no reemplaza ninguno de los seis campos base.
