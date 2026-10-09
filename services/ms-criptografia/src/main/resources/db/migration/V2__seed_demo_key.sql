-- =============================================================
-- ms-criptografia · cryptography_db
-- V2__seed_demo_key.sql
-- Clave de demostración local.
-- kek_reference apunta a la variable DEMO_MASTER_KEY (no en repo).
-- wrapped_dek: valor ficticio para demo; no usar en producción.
-- UUID demo: 0x444d4f4f4d4d44454d4f4b45594b4559 = "DEMOMASTERKEY"
-- =============================================================

INSERT IGNORE INTO encryption_key (
    public_id,
    organization_public_id,
    purpose,
    algorithm,
    version,
    kek_reference,
    wrapped_dek,
    status
) VALUES (
    UNHEX(REPLACE('11111111-0000-0000-0000-000000000001', '-', '')),
    NULL,
    'MEDICAL_RECORD',
    'AES-256-GCM',
    1,
    'env://DEMO_MASTER_KEY',
    0x0000000000000000000000000000000000000000000000000000000000000000,
    'ACTIVE'
);
