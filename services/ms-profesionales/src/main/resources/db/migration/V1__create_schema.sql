CREATE TABLE health_professional (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    user_account_public_id BINARY(16) NOT NULL,
    document_type ENUM('CI','FOREIGN_ID_CARD','PASSPORT') NOT NULL DEFAULT 'CI',
    document_number VARCHAR(20) NOT NULL,
    document_complement VARCHAR(5) NOT NULL DEFAULT '',
    given_names VARCHAR(120) NOT NULL,
    paternal_surname VARCHAR(80) NOT NULL,
    maternal_surname VARCHAR(80) NULL,
    full_name VARCHAR(290) GENERATED ALWAYS AS
        (CONCAT_WS(' ', paternal_surname, maternal_surname, given_names)) STORED,
    professional_type ENUM('PHYSICIAN','DENTIST','PSYCHOLOGIST','NUTRITIONIST','PHYSIOTHERAPIST','NURSING','BIOCHEMIST','OTHER') NOT NULL DEFAULT 'PHYSICIAN',
    professional_license_number VARCHAR(40) NOT NULL,
    title VARCHAR(160) NULL,
    biography VARCHAR(1000) NULL,
    photo_document_public_id BINARY(16) NULL,
    signature_image_document_public_id BINARY(16) NULL,
    status ENUM('ACTIVE','SUSPENDED','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_health_professional PRIMARY KEY (id),
    CONSTRAINT uq_health_professional_public_id UNIQUE (public_id),
    CONSTRAINT uq_health_professional_user_account_public_id UNIQUE (user_account_public_id),
    CONSTRAINT uq_health_professional_license UNIQUE (professional_license_number),
    CONSTRAINT uq_health_professional_document UNIQUE (document_type, document_number, document_complement),
    INDEX ix_health_professional_full_name (full_name),
    INDEX ix_health_professional_photo_document_public_id (photo_document_public_id),
    INDEX ix_health_professional_signature_image_document_public_id (signature_image_document_public_id)
) ENGINE=InnoDB;

CREATE TABLE professional_specialty (
    health_professional_id BIGINT UNSIGNED NOT NULL,
    specialty_code VARCHAR(20) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    graduation_year SMALLINT UNSIGNED NULL,
    CONSTRAINT pk_professional_specialty PRIMARY KEY (health_professional_id, specialty_code),
    CONSTRAINT fk_professional_specialty_professional
        FOREIGN KEY (health_professional_id) REFERENCES health_professional(id) ON DELETE CASCADE,
    INDEX ix_professional_specialty_specialty_code (specialty_code)
) ENGINE=InnoDB;

CREATE TABLE professional_organization (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    health_professional_id BIGINT UNSIGNED NOT NULL,
    organization_public_id BINARY(16) NOT NULL,
    branch_public_id BINARY(16) NULL,
    modality ENUM('STAFF','EXTERNAL_CONSULTANT','FEE_BASED','PARTNER') NOT NULL DEFAULT 'FEE_BASED',
    commission_percentage DECIMAL(5,2) NULL,
    start_date DATE NOT NULL,
    end_date DATE NULL,
    status ENUM('ACTIVE','SUSPENDED','ENDED') NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_professional_organization PRIMARY KEY (id),
    CONSTRAINT uq_professional_organization_public_id UNIQUE (public_id),
    CONSTRAINT uq_professional_organization_link UNIQUE (health_professional_id, organization_public_id, branch_public_id),
    CONSTRAINT fk_professional_organization_professional
        FOREIGN KEY (health_professional_id) REFERENCES health_professional(id),
    CONSTRAINT chk_professional_organization_commission CHECK (commission_percentage IS NULL OR commission_percentage BETWEEN 0 AND 100),
    CONSTRAINT chk_professional_organization_dates CHECK (end_date IS NULL OR end_date >= start_date),
    INDEX ix_professional_organization_organization_status (organization_public_id, status),
    INDEX ix_professional_organization_branch_public_id (branch_public_id)
) ENGINE=InnoDB;
