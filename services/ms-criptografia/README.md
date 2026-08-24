# ms-criptografia

Servicio propietario de los metadatos y auditoría de claves: `encryption_key`, `digital_certificate` y `key_usage`.

- Puerto interno: `8084`
- Base: `cryptography_db`
- Usuario: `cryptography_user`
- Variable de contraseña: `CRIPTOGRAFIA_DB_PASSWORD`
- Variable local obligatoria: `DEMO_MASTER_KEY`
- Ping: `GET /api/v1/cryptography/ping`

Las operaciones internas usan AES-256-GCM. Las respuestas nunca incluyen claves, KEK ni DEK envueltas. Vault/KMS, rotación automática y alta disponibilidad quedan fuera de este MVP; `DEMO_MASTER_KEY` no se debe versionar.

## Contrato para ms-historial

`POST /internal/v1/crypto/encrypt` cifra la narrativa y registra `ENCRYPT` en `key_usage`; `POST /internal/v1/crypto/decrypt` recupera el texto y registra `DECRYPT`. Ambos requieren `purpose`, `requestingService` y `correlationId`.

```json
{
  "organizationId": null,
  "purpose": "MEDICAL_RECORD",
  "plaintext": "Narrativa clínica ficticia",
  "requestingService": "ms-historial",
  "correlationId": "UUID"
}
```

La respuesta de cifrado es `{ "ciphertext": "base64" }`. Dentro de Docker, historial debe usar `http://ms-criptografia:8084`, nunca `localhost`.

```powershell
mvn test
docker build -t medicalsys/ms-criptografia:local .
```
