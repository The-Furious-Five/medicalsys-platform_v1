# ms-criptografia

Servicio propietario de los metadatos y auditoría de claves: `encryption_key`, `digital_certificate` y `key_usage`.

- Puerto interno: `8084`
- Base: `cryptography_db`
- Usuario: `cryptography_user`
- Variable de contraseña: `CRIPTOGRAFIA_DB_PASSWORD`
- Variable local obligatoria: `DEMO_MASTER_KEY`
- Ping: `GET /api/v1/cryptography/ping`

Las operaciones internas usan AES-256-GCM. Las respuestas nunca incluyen claves, KEK ni DEK envueltas. Vault/KMS, rotación automática y alta disponibilidad quedan fuera de este MVP; `DEMO_MASTER_KEY` no se debe versionar.

```powershell
mvn test
docker build -t medicalsys/ms-criptografia:local .
```
