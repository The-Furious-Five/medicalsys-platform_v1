#!/bin/bash
set -e

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
    CREATE DATABASE IF NOT EXISTS identity_db
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
    CREATE USER IF NOT EXISTS 'identity_user'@'%'
        IDENTIFIED BY '${IDENTIDAD_DB_PASSWORD}';
    GRANT ALL PRIVILEGES ON identity_db.* TO 'identity_user'@'%';

    CREATE DATABASE IF NOT EXISTS tenancy_db
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
    CREATE USER IF NOT EXISTS 'tenancy_user'@'%'
        IDENTIFIED BY '${TENENCIA_DB_PASSWORD}';
    GRANT ALL PRIVILEGES ON tenancy_db.* TO 'tenancy_user'@'%';

    CREATE DATABASE IF NOT EXISTS catalogs_db
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
    CREATE USER IF NOT EXISTS 'catalogs_user'@'%'
        IDENTIFIED BY '${CATALOGOS_DB_PASSWORD}';
    GRANT ALL PRIVILEGES ON catalogs_db.* TO 'catalogs_user'@'%';

    CREATE DATABASE IF NOT EXISTS cryptography_db
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
    CREATE USER IF NOT EXISTS 'cryptography_user'@'%'
        IDENTIFIED BY '${CRIPTOGRAFIA_DB_PASSWORD}';
    GRANT ALL PRIVILEGES ON cryptography_db.* TO 'cryptography_user'@'%';

    CREATE DATABASE IF NOT EXISTS patients_db
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
    CREATE USER IF NOT EXISTS 'patients_user'@'%'
        IDENTIFIED BY '${PACIENTES_DB_PASSWORD}';
    GRANT ALL PRIVILEGES ON patients_db.* TO 'patients_user'@'%';

    CREATE DATABASE IF NOT EXISTS professionals_db
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
    CREATE USER IF NOT EXISTS 'professionals_user'@'%'
        IDENTIFIED BY '${PROFESIONALES_DB_PASSWORD}';
    GRANT ALL PRIVILEGES ON professionals_db.* TO 'professionals_user'@'%';

    CREATE DATABASE IF NOT EXISTS medical_records_db
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
    CREATE USER IF NOT EXISTS 'medical_records_user'@'%'
        IDENTIFIED BY '${HISTORIAL_DB_PASSWORD}';
    GRANT ALL PRIVILEGES ON medical_records_db.* TO 'medical_records_user'@'%';

    CREATE DATABASE IF NOT EXISTS documents_db
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
    CREATE USER IF NOT EXISTS 'documents_user'@'%'
        IDENTIFIED BY '${DOCUMENTAL_DB_PASSWORD}';
    GRANT ALL PRIVILEGES ON documents_db.* TO 'documents_user'@'%';

    CREATE DATABASE IF NOT EXISTS billing_db
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
    CREATE USER IF NOT EXISTS 'billing_user'@'%'
        IDENTIFIED BY '${FACTURACION_DB_PASSWORD}';
    GRANT ALL PRIVILEGES ON billing_db.* TO 'billing_user'@'%';

    FLUSH PRIVILEGES;
EOSQL
