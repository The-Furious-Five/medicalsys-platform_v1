-- Claves de tenant para el recorrido MVP (org demo A y org demo B).
-- La DEK no vive en wrapped_dek: se deriva por HKDF en runtime.

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
    UNHEX(REPLACE('00000000-0000-4000-8000-0000000000c4', '-', '')),
    UNHEX(REPLACE('00000000-0000-4000-8000-0000000000a1', '-', '')),
    'MEDICAL_RECORD',
    'AES-256-GCM',
    1,
    'env://DEMO_MASTER_KEY',
    0x0000000000000000000000000000000000000000000000000000000000000000,
    'ACTIVE'
), (
    UNHEX(REPLACE('00000000-0000-4000-8000-0000000000c5', '-', '')),
    UNHEX(REPLACE('00000000-0000-4000-8000-0000000000b1', '-', '')),
    'MEDICAL_RECORD',
    'AES-256-GCM',
    1,
    'env://DEMO_MASTER_KEY',
    0x0000000000000000000000000000000000000000000000000000000000000000,
    'ACTIVE'
);
