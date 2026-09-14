-- MySQL dump 10.13  Distrib 8.4.11, for Linux (aarch64)
--
-- Host: localhost    Database: identity_db
-- ------------------------------------------------------
-- Server version	8.4.11

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `identity_db`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `identity_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `identity_db`;

--
-- Table structure for table `flyway_schema_history`
--

DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `permission`
--

DROP TABLE IF EXISTS `permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `permission` (
  `id` smallint unsigned NOT NULL AUTO_INCREMENT,
  `code` varchar(60) NOT NULL,
  `module` varchar(30) NOT NULL,
  `description` varchar(300) NOT NULL,
  `is_sensitive` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_permission_code` (`code`),
  KEY `ix_permission_module` (`module`),
  CONSTRAINT `ck_permission_module` CHECK ((`module` in (_utf8mb4'IDENTITY',_utf8mb4'TENANCY',_utf8mb4'CATALOGS',_utf8mb4'CRYPTOGRAPHY',_utf8mb4'PATIENTS',_utf8mb4'PROFESSIONALS',_utf8mb4'SCHEDULING',_utf8mb4'CLINICAL',_utf8mb4'DOCUMENT_MANAGEMENT',_utf8mb4'BILLING',_utf8mb4'ADMINISTRATION')))
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `role`
--

DROP TABLE IF EXISTS `role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role` (
  `id` smallint unsigned NOT NULL AUTO_INCREMENT,
  `code` varchar(30) NOT NULL,
  `name` varchar(80) NOT NULL,
  `description` varchar(300) DEFAULT NULL,
  `scope` varchar(20) NOT NULL DEFAULT 'ORGANIZATION',
  `is_system` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_role_code` (`code`),
  CONSTRAINT `ck_role_code` CHECK ((`code` in (_utf8mb4'PLATFORM_ADMIN',_utf8mb4'ADMINISTRATOR',_utf8mb4'PHYSICIAN',_utf8mb4'RECEPTIONIST',_utf8mb4'PATIENT',_utf8mb4'BILLING_CLERK'))),
  CONSTRAINT `ck_role_scope` CHECK ((`scope` in (_utf8mb4'PLATFORM',_utf8mb4'ORGANIZATION')))
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `role_permission`
--

DROP TABLE IF EXISTS `role_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role_permission` (
  `role_id` smallint unsigned NOT NULL,
  `permission_id` smallint unsigned NOT NULL,
  PRIMARY KEY (`role_id`,`permission_id`),
  KEY `ix_role_permission_permission_id` (`permission_id`),
  CONSTRAINT `fk_role_permission_permission` FOREIGN KEY (`permission_id`) REFERENCES `permission` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_role_permission_role` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_account`
--

DROP TABLE IF EXISTS `user_account`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_account` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `organization_public_id` binary(16) DEFAULT NULL,
  `email` varchar(160) NOT NULL,
  `phone_e164` varchar(20) DEFAULT NULL,
  `full_name` varchar(180) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `hash_algorithm` varchar(20) NOT NULL DEFAULT 'argon2id',
  `password_updated_at` timestamp NULL DEFAULT NULL,
  `mfa_enabled` tinyint(1) NOT NULL DEFAULT '0',
  `encrypted_mfa_secret` varbinary(512) DEFAULT NULL,
  `encryption_key_public_id` binary(16) DEFAULT NULL,
  `language` varchar(5) NOT NULL DEFAULT 'es-BO',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING_ACTIVATION',
  `failed_attempts` tinyint unsigned NOT NULL DEFAULT '0',
  `locked_until` timestamp NULL DEFAULT NULL,
  `last_access_at` timestamp NULL DEFAULT NULL,
  `terms_accepted_at` timestamp NULL DEFAULT NULL,
  `data_policy_accepted_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_account_public_id` (`public_id`),
  UNIQUE KEY `uq_user_account_email` (`email`),
  KEY `ix_user_account_organization_public_id_status` (`organization_public_id`,`status`),
  KEY `ix_user_account_phone_e164` (`phone_e164`),
  CONSTRAINT `ck_user_account_email` CHECK ((`email` like _utf8mb4'%_@_%._%')),
  CONSTRAINT `ck_user_account_status` CHECK ((`status` in (_utf8mb4'PENDING_ACTIVATION',_utf8mb4'ACTIVE',_utf8mb4'LOCKED',_utf8mb4'INACTIVE')))
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_role`
--

DROP TABLE IF EXISTS `user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_role` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_account_id` bigint unsigned NOT NULL,
  `role_id` smallint unsigned NOT NULL,
  `organization_public_id` binary(16) DEFAULT NULL,
  `branch_public_id` binary(16) DEFAULT NULL,
  `valid_from` date NOT NULL,
  `valid_until` date DEFAULT NULL,
  `assigned_by_user_id` bigint unsigned DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_role_user_account_id_role_id_organization_pu_key` (`user_account_id`,`role_id`,`organization_public_id`,`branch_public_id`),
  KEY `fk_user_role_role` (`role_id`),
  KEY `ix_user_role_organization_public_id_role_id` (`organization_public_id`,`role_id`),
  KEY `ix_user_role_branch_public_id` (`branch_public_id`),
  KEY `ix_user_role_assigned_by_user_id` (`assigned_by_user_id`),
  CONSTRAINT `fk_user_role_assigned_by` FOREIGN KEY (`assigned_by_user_id`) REFERENCES `user_account` (`id`),
  CONSTRAINT `fk_user_role_role` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`),
  CONSTRAINT `fk_user_role_user_account` FOREIGN KEY (`user_account_id`) REFERENCES `user_account` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_user_role_validity` CHECK (((`valid_until` is null) or (`valid_until` >= `valid_from`)))
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping routines for database 'identity_db'
--

--
-- Current Database: `tenancy_db`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `tenancy_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `tenancy_db`;

--
-- Table structure for table `branch`
--

DROP TABLE IF EXISTS `branch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `branch` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `organization_id` bigint unsigned NOT NULL,
  `code` varchar(20) NOT NULL,
  `name` varchar(120) NOT NULL,
  `is_headquarters` tinyint(1) NOT NULL DEFAULT '0',
  `address` varchar(250) NOT NULL,
  `city` varchar(80) NOT NULL,
  `department` varchar(20) NOT NULL,
  `phone_e164` varchar(20) DEFAULT NULL,
  `latitude` decimal(9,6) DEFAULT NULL,
  `longitude` decimal(9,6) DEFAULT NULL,
  `opening_hours` json DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_branch_public_id` (`public_id`),
  UNIQUE KEY `uq_branch_organization_id_code` (`organization_id`,`code`),
  KEY `ix_branch_organization_id_status` (`organization_id`,`status`),
  CONSTRAINT `fk_branch_organization` FOREIGN KEY (`organization_id`) REFERENCES `organization` (`id`),
  CONSTRAINT `ck_branch_department` CHECK ((`department` in (_utf8mb4'LA_PAZ',_utf8mb4'COCHABAMBA',_utf8mb4'SANTA_CRUZ',_utf8mb4'ORURO',_utf8mb4'POTOSI',_utf8mb4'CHUQUISACA',_utf8mb4'TARIJA',_utf8mb4'BENI',_utf8mb4'PANDO'))),
  CONSTRAINT `ck_branch_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'INACTIVE')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `flyway_schema_history`
--

DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `organization`
--

DROP TABLE IF EXISTS `organization`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `organization` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `code` varchar(30) NOT NULL,
  `type` varchar(30) NOT NULL,
  `legal_name` varchar(180) NOT NULL,
  `trade_name` varchar(180) NOT NULL,
  `nit` varchar(15) NOT NULL,
  `trade_registry_number` varchar(40) DEFAULT NULL,
  `contact_email` varchar(160) NOT NULL,
  `phone_e164` varchar(20) DEFAULT NULL,
  `city` varchar(80) DEFAULT NULL,
  `department` varchar(20) DEFAULT NULL,
  `time_zone` varchar(40) NOT NULL DEFAULT 'America/La_Paz',
  `currency` char(3) NOT NULL DEFAULT 'BOB',
  `logo_url` varchar(500) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ONBOARDING',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_organization_public_id` (`public_id`),
  UNIQUE KEY `uq_organization_code` (`code`),
  UNIQUE KEY `uq_organization_nit` (`nit`),
  KEY `ix_organization_status` (`status`),
  CONSTRAINT `ck_organization_department` CHECK (((`department` is null) or (`department` in (_utf8mb4'LA_PAZ',_utf8mb4'COCHABAMBA',_utf8mb4'SANTA_CRUZ',_utf8mb4'ORURO',_utf8mb4'POTOSI',_utf8mb4'CHUQUISACA',_utf8mb4'TARIJA',_utf8mb4'BENI',_utf8mb4'PANDO')))),
  CONSTRAINT `ck_organization_status` CHECK ((`status` in (_utf8mb4'ONBOARDING',_utf8mb4'ACTIVE',_utf8mb4'SUSPENDED',_utf8mb4'CANCELLED'))),
  CONSTRAINT `ck_organization_type` CHECK ((`type` in (_utf8mb4'PRIVATE_CLINIC',_utf8mb4'PUBLIC_CLINIC',_utf8mb4'OFFICE',_utf8mb4'INDEPENDENT_PHYSICIAN',_utf8mb4'DIAGNOSTIC_CENTER')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `plan_usage`
--

DROP TABLE IF EXISTS `plan_usage`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `plan_usage` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `organization_id` bigint unsigned NOT NULL,
  `metric` varchar(20) NOT NULL,
  `current_value` bigint unsigned NOT NULL DEFAULT '0',
  `period_start` date NOT NULL,
  `period_end` date DEFAULT NULL,
  `version` bigint unsigned NOT NULL DEFAULT '0',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_plan_usage_organization_id_metric_period_start` (`organization_id`,`metric`,`period_start`),
  KEY `ix_plan_usage_organization_id` (`organization_id`),
  CONSTRAINT `fk_plan_usage_organization` FOREIGN KEY (`organization_id`) REFERENCES `organization` (`id`),
  CONSTRAINT `ck_plan_usage_current_value` CHECK ((`current_value` >= 0)),
  CONSTRAINT `ck_plan_usage_metric` CHECK ((`metric` in (_utf8mb4'USERS',_utf8mb4'BRANCHES',_utf8mb4'STORAGE_BYTES'))),
  CONSTRAINT `ck_plan_usage_period` CHECK (((`period_end` is null) or (`period_end` >= `period_start`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `service_offering`
--

DROP TABLE IF EXISTS `service_offering`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `service_offering` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `organization_id` bigint unsigned NOT NULL,
  `code` varchar(30) NOT NULL,
  `name` varchar(160) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `type` varchar(30) NOT NULL,
  `specialty_code` varchar(20) DEFAULT NULL,
  `duration_minutes` smallint unsigned NOT NULL DEFAULT '30',
  `base_price_bob` decimal(12,2) NOT NULL DEFAULT '0.00',
  `requires_consent` tinyint(1) NOT NULL DEFAULT '0',
  `consent_template_public_id` binary(16) DEFAULT NULL,
  `required_resource_type` varchar(30) NOT NULL DEFAULT 'OFFICE',
  `tax_product_code` varchar(30) DEFAULT NULL,
  `tax_unit_code` varchar(10) DEFAULT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_service_offering_public_id` (`public_id`),
  UNIQUE KEY `uq_service_offering_organization_id_code` (`organization_id`,`code`),
  KEY `ix_service_offering_organization_id_active` (`organization_id`,`active`),
  KEY `ix_service_offering_specialty_code` (`specialty_code`),
  CONSTRAINT `fk_service_offering_organization` FOREIGN KEY (`organization_id`) REFERENCES `organization` (`id`),
  CONSTRAINT `ck_service_offering_base_price_bob` CHECK ((`base_price_bob` >= 0)),
  CONSTRAINT `ck_service_offering_duration_minutes` CHECK ((`duration_minutes` between 5 and 720)),
  CONSTRAINT `ck_service_offering_required_resource_type` CHECK ((`required_resource_type` in (_utf8mb4'NONE',_utf8mb4'OFFICE',_utf8mb4'OPERATING_ROOM',_utf8mb4'PROCEDURE_ROOM',_utf8mb4'EQUIPMENT'))),
  CONSTRAINT `ck_service_offering_type` CHECK ((`type` in (_utf8mb4'CONSULTATION',_utf8mb4'FOLLOW_UP',_utf8mb4'PROCEDURE',_utf8mb4'SURGERY',_utf8mb4'STUDY',_utf8mb4'TELECONSULTATION',_utf8mb4'VACCINATION',_utf8mb4'OTHER')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `subscription`
--

DROP TABLE IF EXISTS `subscription`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `subscription` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `organization_id` bigint unsigned NOT NULL,
  `subscription_plan_id` smallint unsigned NOT NULL,
  `billing_cycle` varchar(20) NOT NULL DEFAULT 'MONTHLY',
  `start_date` date NOT NULL,
  `end_date` date DEFAULT NULL,
  `agreed_price_bob` decimal(10,2) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'TRIAL',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_subscription_organization_id_status` (`organization_id`,`status`),
  KEY `ix_subscription_subscription_plan_id` (`subscription_plan_id`),
  CONSTRAINT `fk_subscription_organization` FOREIGN KEY (`organization_id`) REFERENCES `organization` (`id`),
  CONSTRAINT `fk_subscription_subscription_plan` FOREIGN KEY (`subscription_plan_id`) REFERENCES `subscription_plan` (`id`),
  CONSTRAINT `ck_subscription_billing_cycle` CHECK ((`billing_cycle` in (_utf8mb4'MONTHLY',_utf8mb4'YEARLY'))),
  CONSTRAINT `ck_subscription_status` CHECK ((`status` in (_utf8mb4'TRIAL',_utf8mb4'ACTIVE',_utf8mb4'PAST_DUE',_utf8mb4'CANCELLED'))),
  CONSTRAINT `ck_subscription_validity` CHECK (((`end_date` is null) or (`end_date` >= `start_date`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `subscription_plan`
--

DROP TABLE IF EXISTS `subscription_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `subscription_plan` (
  `id` smallint unsigned NOT NULL AUTO_INCREMENT,
  `code` varchar(30) NOT NULL,
  `name` varchar(80) NOT NULL,
  `monthly_price_bob` decimal(10,2) NOT NULL DEFAULT '0.00',
  `max_users` smallint unsigned DEFAULT NULL,
  `max_branches` smallint unsigned DEFAULT NULL,
  `max_storage_gb` smallint unsigned DEFAULT NULL,
  `includes_whatsapp` tinyint(1) NOT NULL DEFAULT '1',
  `includes_billing` tinyint(1) NOT NULL DEFAULT '1',
  `features` json DEFAULT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_subscription_plan_code` (`code`),
  CONSTRAINT `ck_subscription_plan_monthly_price_bob` CHECK ((`monthly_price_bob` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping routines for database 'tenancy_db'
--

--
-- Current Database: `catalogs_db`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `catalogs_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `catalogs_db`;

--
-- Table structure for table `catalog_sync`
--

DROP TABLE IF EXISTS `catalog_sync`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `catalog_sync` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `catalog_type` varchar(50) NOT NULL,
  `source` varchar(120) NOT NULL,
  `source_version` varchar(80) NOT NULL,
  `started_at` timestamp NOT NULL,
  `finished_at` timestamp NULL DEFAULT NULL,
  `status` enum('IN_PROGRESS','SUCCESSFUL','FAILED') NOT NULL DEFAULT 'IN_PROGRESS',
  `processed_records` int unsigned NOT NULL DEFAULT '0',
  `sha256_hash` char(64) DEFAULT NULL,
  `error_detail` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_catalog_sync_catalog_type_started_at` (`catalog_type`,`started_at`),
  CONSTRAINT `chk_catalog_sync_processed_records` CHECK ((`processed_records` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `flyway_schema_history`
--

DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `icd10_catalog`
--

DROP TABLE IF EXISTS `icd10_catalog`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `icd10_catalog` (
  `code` varchar(10) NOT NULL,
  `description` varchar(300) NOT NULL,
  `chapter` varchar(120) DEFAULT NULL,
  `category` varchar(120) DEFAULT NULL,
  `applicable_sex` enum('BOTH','M','F') NOT NULL DEFAULT 'BOTH',
  `active` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`code`),
  KEY `ix_icd10_catalog_description` (`description`(100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `medication`
--

DROP TABLE IF EXISTS `medication`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `medication` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `code` varchar(30) NOT NULL,
  `generic_name` varchar(160) NOT NULL,
  `trade_name` varchar(160) DEFAULT NULL,
  `concentration` varchar(60) DEFAULT NULL,
  `pharmaceutical_form` enum('TABLET','CAPSULE','SYRUP','SUSPENSION','INJECTABLE','CREAM','OINTMENT','DROPS','SUPPOSITORY','PATCH','AEROSOL','OTHER') NOT NULL DEFAULT 'TABLET',
  `administration_route` enum('ORAL','INTRAVENOUS','INTRAMUSCULAR','SUBCUTANEOUS','TOPICAL','OPHTHALMIC','OTIC','NASAL','RECTAL','INHALATION','OTHER') NOT NULL DEFAULT 'ORAL',
  `is_controlled` tinyint(1) NOT NULL DEFAULT '0',
  `requires_prescription` tinyint(1) NOT NULL DEFAULT '1',
  `active` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_medication_code` (`code`),
  KEY `ix_medication_generic_name` (`generic_name`(100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `medication_active_ingredient`
--

DROP TABLE IF EXISTS `medication_active_ingredient`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `medication_active_ingredient` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `medication_id` bigint unsigned NOT NULL,
  `active_ingredient_code` varchar(40) NOT NULL,
  `active_ingredient_name` varchar(160) NOT NULL,
  `quantity` decimal(12,4) DEFAULT NULL,
  `unit` varchar(30) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_medication_active_ingredient_medication_id_active_key` (`medication_id`,`active_ingredient_code`),
  KEY `ix_medication_active_ingredient_active_ingredient_name` (`active_ingredient_name`(100)),
  CONSTRAINT `fk_medication_active_ingredient_medication` FOREIGN KEY (`medication_id`) REFERENCES `medication` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_medication_active_ingredient_quantity` CHECK (((`quantity` is null) or (`quantity` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `specialty`
--

DROP TABLE IF EXISTS `specialty`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `specialty` (
  `id` smallint unsigned NOT NULL AUTO_INCREMENT,
  `code` varchar(20) NOT NULL,
  `name` varchar(120) NOT NULL,
  `description` varchar(300) DEFAULT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_specialty_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `study_type`
--

DROP TABLE IF EXISTS `study_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `study_type` (
  `id` smallint unsigned NOT NULL AUTO_INCREMENT,
  `code` varchar(30) NOT NULL,
  `name` varchar(160) NOT NULL,
  `category` enum('LABORATORY','IMAGING','PATHOLOGY','CARDIOLOGY','ENDOSCOPY','OTHER') NOT NULL,
  `requires_fasting` tinyint(1) NOT NULL DEFAULT '0',
  `generates_image` tinyint(1) NOT NULL DEFAULT '0',
  `result_unit` varchar(30) DEFAULT NULL,
  `reference_min` decimal(12,4) DEFAULT NULL,
  `reference_max` decimal(12,4) DEFAULT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_study_type_code` (`code`),
  KEY `ix_study_type_category` (`category`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tax_catalog`
--

DROP TABLE IF EXISTS `tax_catalog`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tax_catalog` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `catalog_type` enum('IDENTITY_DOCUMENT_TYPE','PAYMENT_METHOD_TYPE','UNIT_OF_MEASURE','ECONOMIC_ACTIVITY','PRODUCT_SERVICE','INVOICE_CAPTION','CANCELLATION_REASON','SIGNIFICANT_EVENT_TYPE','TAX_DOCUMENT_TYPE','EMISSION_TYPE','POINT_OF_SALE_TYPE') NOT NULL,
  `code` varchar(30) NOT NULL,
  `description` varchar(500) NOT NULL,
  `parent_code` varchar(30) DEFAULT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `synced_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_tax_catalog_catalog_type_code` (`catalog_type`,`code`),
  KEY `ix_tax_catalog_catalog_type_active` (`catalog_type`,`active`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping routines for database 'catalogs_db'
--

--
-- Current Database: `cryptography_db`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `cryptography_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `cryptography_db`;

--
-- Table structure for table `digital_certificate`
--

DROP TABLE IF EXISTS `digital_certificate`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `digital_certificate` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `organization_public_id` binary(16) NOT NULL,
  `holder_user_public_id` binary(16) DEFAULT NULL,
  `usage` enum('CONSENT_SIGNATURE','PRESCRIPTION_SIGNATURE','TAX_INVOICE_SIGNATURE','ORGANIZATION_SEAL') NOT NULL,
  `certificate_authority` varchar(160) NOT NULL,
  `serial_number` varchar(100) NOT NULL,
  `subject` varchar(300) NOT NULL,
  `sha256_fingerprint` char(64) NOT NULL,
  `valid_from` timestamp NOT NULL,
  `valid_until` timestamp NOT NULL,
  `status` enum('ACTIVE','EXPIRING_SOON','EXPIRED','REVOKED') NOT NULL DEFAULT 'ACTIVE',
  `encryption_key_id` bigint unsigned DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_digital_certificate_public_id` (`public_id`),
  UNIQUE KEY `uq_digital_certificate_certificate_authority_serial_number` (`certificate_authority`,`serial_number`),
  KEY `ix_digital_certificate_organization_public_id_status` (`organization_public_id`,`status`),
  KEY `ix_digital_certificate_holder_user_public_id` (`holder_user_public_id`),
  KEY `ix_digital_certificate_encryption_key_id` (`encryption_key_id`),
  CONSTRAINT `fk_digital_certificate_encryption_key` FOREIGN KEY (`encryption_key_id`) REFERENCES `encryption_key` (`id`),
  CONSTRAINT `chk_digital_certificate_valid_until` CHECK ((`valid_until` > `valid_from`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `encryption_key`
--

DROP TABLE IF EXISTS `encryption_key`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `encryption_key` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `organization_public_id` binary(16) DEFAULT NULL,
  `purpose` enum('MEDICAL_RECORD','DOCUMENTS','CONTACT_DATA','CREDENTIALS','BILLING','DIGITAL_SIGNATURE') NOT NULL,
  `algorithm` enum('AES-256-GCM','AES-256-CBC','CHACHA20-POLY1305') NOT NULL DEFAULT 'AES-256-GCM',
  `version` smallint unsigned NOT NULL DEFAULT '1',
  `kek_reference` varchar(300) NOT NULL,
  `wrapped_dek` varbinary(512) NOT NULL,
  `status` enum('ACTIVE','AT_ROTACION','RETIRED','COMPROMISED') NOT NULL DEFAULT 'ACTIVE',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `rotated_at` timestamp NULL DEFAULT NULL,
  `retired_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_encryption_key_public_id` (`public_id`),
  UNIQUE KEY `uq_encryption_key_organization_public_id_purpose_version` (`organization_public_id`,`purpose`,`version`),
  KEY `ix_encryption_key_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `flyway_schema_history`
--

DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `key_usage`
--

DROP TABLE IF EXISTS `key_usage`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `key_usage` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `encryption_key_id` bigint unsigned NOT NULL,
  `operation` enum('ENCRYPT','DECRYPT','WRAP','UNWRAP','ROTATE','SIGN','VERIFY') NOT NULL,
  `requesting_service` varchar(80) NOT NULL,
  `subject_type` varchar(60) NOT NULL,
  `subject_public_id` binary(16) DEFAULT NULL,
  `correlation_id` binary(16) NOT NULL,
  `result` enum('SUCCESS','FAILURE') NOT NULL,
  `error_code` varchar(80) DEFAULT NULL,
  `occurred_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_key_usage_encryption_key_id_occurred_at` (`encryption_key_id`,`occurred_at`),
  KEY `ix_key_usage_correlation_id` (`correlation_id`),
  CONSTRAINT `fk_key_usage_encryption_key` FOREIGN KEY (`encryption_key_id`) REFERENCES `encryption_key` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping routines for database 'cryptography_db'
--

--
-- Current Database: `patients_db`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `patients_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `patients_db`;

--
-- Table structure for table `flyway_schema_history`
--

DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notification_preference`
--

DROP TABLE IF EXISTS `notification_preference`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification_preference` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `patient_id` bigint unsigned NOT NULL,
  `channel` varchar(20) NOT NULL,
  `category` varchar(20) NOT NULL DEFAULT 'ADMINISTRATIVE',
  `opt_in` tinyint(1) NOT NULL DEFAULT '0',
  `consent_source` varchar(30) DEFAULT NULL,
  `opt_in_at` timestamp NULL DEFAULT NULL,
  `opt_out_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_notification_preference_patient_id_channel_category` (`patient_id`,`channel`,`category`),
  CONSTRAINT `fk_notification_preference_patient` FOREIGN KEY (`patient_id`) REFERENCES `patient` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_notification_preference_category` CHECK ((`category` in (_utf8mb4'CLINICAL',_utf8mb4'ADMINISTRATIVE'))),
  CONSTRAINT `ck_notification_preference_channel` CHECK ((`channel` in (_utf8mb4'WHATSAPP',_utf8mb4'SMS',_utf8mb4'EMAIL',_utf8mb4'PUSH'))),
  CONSTRAINT `ck_notification_preference_consent_source` CHECK (((`consent_source` is null) or (`consent_source` in (_utf8mb4'WEB_FORM',_utf8mb4'RECEPTION',_utf8mb4'WHATSAPP_RESPONSE',_utf8mb4'PHYSICAL_CONTRACT',_utf8mb4'IMPORT')))),
  CONSTRAINT `ck_notification_preference_opt_in_at` CHECK (((`opt_in` = false) or (`opt_in_at` is not null)))
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `patient`
--

DROP TABLE IF EXISTS `patient`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `patient` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `user_account_public_id` binary(16) DEFAULT NULL,
  `document_type` varchar(30) NOT NULL DEFAULT 'CI',
  `document_number` varchar(20) NOT NULL,
  `document_complement` varchar(5) NOT NULL DEFAULT '',
  `issued_department` varchar(20) DEFAULT NULL,
  `given_names` varchar(120) NOT NULL,
  `paternal_surname` varchar(80) NOT NULL,
  `maternal_surname` varchar(80) DEFAULT NULL,
  `full_name` varchar(290) GENERATED ALWAYS AS (concat_ws(_utf8mb4' ',`paternal_surname`,`maternal_surname`,`given_names`)) STORED,
  `birth_date` date NOT NULL,
  `sex` varchar(20) NOT NULL DEFAULT 'UNDECLARED',
  `marital_status` varchar(30) NOT NULL DEFAULT 'UNDECLARED',
  `blood_type` varchar(10) NOT NULL DEFAULT 'UNKNOWN',
  `occupation` varchar(120) DEFAULT NULL,
  `phone_e164` varchar(20) DEFAULT NULL,
  `alternate_phone` varchar(20) DEFAULT NULL,
  `email` varchar(160) DEFAULT NULL,
  `encrypted_address` varbinary(1024) DEFAULT NULL,
  `city` varchar(80) DEFAULT NULL,
  `department` varchar(20) DEFAULT NULL,
  `emergency_contact_name` varchar(160) DEFAULT NULL,
  `emergency_contact_phone` varchar(20) DEFAULT NULL,
  `emergency_contact_relationship` varchar(60) DEFAULT NULL,
  `guardian_patient_id` bigint unsigned DEFAULT NULL,
  `encryption_key_public_id` binary(16) DEFAULT NULL,
  `data_consent_at` timestamp NULL DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `death_date` date DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_patient_public_id` (`public_id`),
  UNIQUE KEY `uq_patient_document_type_document_number_document_complement` (`document_type`,`document_number`,`document_complement`),
  UNIQUE KEY `uq_patient_user_account_public_id` (`user_account_public_id`),
  KEY `ix_patient_full_name` (`full_name`),
  KEY `ix_patient_phone_e164` (`phone_e164`),
  KEY `ix_patient_guardian_patient_id` (`guardian_patient_id`),
  KEY `ix_patient_encryption_key_public_id` (`encryption_key_public_id`),
  CONSTRAINT `fk_patient_guardian_patient` FOREIGN KEY (`guardian_patient_id`) REFERENCES `patient` (`id`),
  CONSTRAINT `ck_patient_birth_date` CHECK ((`birth_date` >= _utf8mb4'1900-01-01')),
  CONSTRAINT `ck_patient_blood_type` CHECK ((`blood_type` in (_utf8mb4'A+',_utf8mb4'A-',_utf8mb4'B+',_utf8mb4'B-',_utf8mb4'AB+',_utf8mb4'AB-',_utf8mb4'O+',_utf8mb4'O-',_utf8mb4'UNKNOWN'))),
  CONSTRAINT `ck_patient_department` CHECK (((`department` is null) or (`department` in (_utf8mb4'LA_PAZ',_utf8mb4'COCHABAMBA',_utf8mb4'SANTA_CRUZ',_utf8mb4'ORURO',_utf8mb4'POTOSI',_utf8mb4'CHUQUISACA',_utf8mb4'TARIJA',_utf8mb4'BENI',_utf8mb4'PANDO',_utf8mb4'FOREIGN')))),
  CONSTRAINT `ck_patient_document_type` CHECK ((`document_type` in (_utf8mb4'CI',_utf8mb4'FOREIGN_ID_CARD',_utf8mb4'PASSPORT',_utf8mb4'BIRTH_CERTIFICATE',_utf8mb4'NO_DOCUMENT'))),
  CONSTRAINT `ck_patient_issued_department` CHECK (((`issued_department` is null) or (`issued_department` in (_utf8mb4'LA_PAZ',_utf8mb4'COCHABAMBA',_utf8mb4'SANTA_CRUZ',_utf8mb4'ORURO',_utf8mb4'POTOSI',_utf8mb4'CHUQUISACA',_utf8mb4'TARIJA',_utf8mb4'BENI',_utf8mb4'PANDO',_utf8mb4'FOREIGN')))),
  CONSTRAINT `ck_patient_marital_status` CHECK ((`marital_status` in (_utf8mb4'SINGLE',_utf8mb4'MARRIED',_utf8mb4'DIVORCED',_utf8mb4'WIDOWED',_utf8mb4'DOMESTIC_PARTNERSHIP',_utf8mb4'UNDECLARED'))),
  CONSTRAINT `ck_patient_sex` CHECK ((`sex` in (_utf8mb4'M',_utf8mb4'F',_utf8mb4'INTERSEX',_utf8mb4'UNDECLARED'))),
  CONSTRAINT `ck_patient_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'INACTIVE',_utf8mb4'DECEASED'))),
  CONSTRAINT `ck_patient_status_death_date` CHECK ((((`status` <> _utf8mb4'DECEASED') and (`death_date` is null)) or ((`status` = _utf8mb4'DECEASED') and (`death_date` is not null))))
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `patient_merge`
--

DROP TABLE IF EXISTS `patient_merge`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `patient_merge` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `surviving_patient_id` bigint unsigned NOT NULL,
  `merged_patient_id` bigint unsigned NOT NULL,
  `reason` varchar(500) NOT NULL,
  `merged_by_user_public_id` binary(16) NOT NULL,
  `merged_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `event_public_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_patient_merge_merged_patient_id` (`merged_patient_id`),
  UNIQUE KEY `uq_patient_merge_event_public_id` (`event_public_id`),
  KEY `ix_patient_merge_surviving_patient_id` (`surviving_patient_id`),
  CONSTRAINT `fk_patient_merge_merged_patient` FOREIGN KEY (`merged_patient_id`) REFERENCES `patient` (`id`),
  CONSTRAINT `fk_patient_merge_surviving_patient` FOREIGN KEY (`surviving_patient_id`) REFERENCES `patient` (`id`),
  CONSTRAINT `ck_patient_merge_distinct_patients` CHECK ((`surviving_patient_id` <> `merged_patient_id`))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping routines for database 'patients_db'
--

--
-- Current Database: `professionals_db`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `professionals_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `professionals_db`;

--
-- Table structure for table `flyway_schema_history`
--

DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `health_professional`
--

DROP TABLE IF EXISTS `health_professional`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `health_professional` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `user_account_public_id` binary(16) NOT NULL,
  `document_type` enum('CI','FOREIGN_ID_CARD','PASSPORT') NOT NULL DEFAULT 'CI',
  `document_number` varchar(20) NOT NULL,
  `document_complement` varchar(5) NOT NULL DEFAULT '',
  `given_names` varchar(120) NOT NULL,
  `paternal_surname` varchar(80) NOT NULL,
  `maternal_surname` varchar(80) DEFAULT NULL,
  `full_name` varchar(290) GENERATED ALWAYS AS (concat_ws(_utf8mb4' ',`paternal_surname`,`maternal_surname`,`given_names`)) STORED,
  `professional_type` enum('PHYSICIAN','DENTIST','PSYCHOLOGIST','NUTRITIONIST','PHYSIOTHERAPIST','NURSING','BIOCHEMIST','OTHER') NOT NULL DEFAULT 'PHYSICIAN',
  `professional_license_number` varchar(40) NOT NULL,
  `title` varchar(160) DEFAULT NULL,
  `biography` varchar(1000) DEFAULT NULL,
  `photo_document_public_id` binary(16) DEFAULT NULL,
  `signature_image_document_public_id` binary(16) DEFAULT NULL,
  `status` enum('ACTIVE','SUSPENDED','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_health_professional_public_id` (`public_id`),
  UNIQUE KEY `uq_health_professional_user_account_public_id` (`user_account_public_id`),
  UNIQUE KEY `uq_health_professional_license` (`professional_license_number`),
  UNIQUE KEY `uq_health_professional_document` (`document_type`,`document_number`,`document_complement`),
  KEY `ix_health_professional_full_name` (`full_name`),
  KEY `ix_health_professional_photo_document_public_id` (`photo_document_public_id`),
  KEY `ix_health_professional_signature_image_document_public_id` (`signature_image_document_public_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `professional_organization`
--

DROP TABLE IF EXISTS `professional_organization`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `professional_organization` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `health_professional_id` bigint unsigned NOT NULL,
  `organization_public_id` binary(16) NOT NULL,
  `branch_public_id` binary(16) DEFAULT NULL,
  `modality` enum('STAFF','EXTERNAL_CONSULTANT','FEE_BASED','PARTNER') NOT NULL DEFAULT 'FEE_BASED',
  `commission_percentage` decimal(5,2) DEFAULT NULL,
  `start_date` date NOT NULL,
  `end_date` date DEFAULT NULL,
  `status` enum('ACTIVE','SUSPENDED','ENDED') NOT NULL DEFAULT 'ACTIVE',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_professional_organization_public_id` (`public_id`),
  UNIQUE KEY `uq_professional_organization_link` (`health_professional_id`,`organization_public_id`,`branch_public_id`),
  KEY `ix_professional_organization_organization_status` (`organization_public_id`,`status`),
  KEY `ix_professional_organization_branch_public_id` (`branch_public_id`),
  CONSTRAINT `fk_professional_organization_professional` FOREIGN KEY (`health_professional_id`) REFERENCES `health_professional` (`id`),
  CONSTRAINT `chk_professional_organization_commission` CHECK (((`commission_percentage` is null) or (`commission_percentage` between 0 and 100))),
  CONSTRAINT `chk_professional_organization_dates` CHECK (((`end_date` is null) or (`end_date` >= `start_date`)))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `professional_specialty`
--

DROP TABLE IF EXISTS `professional_specialty`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `professional_specialty` (
  `health_professional_id` bigint unsigned NOT NULL,
  `specialty_code` varchar(20) NOT NULL,
  `is_primary` tinyint(1) NOT NULL DEFAULT '0',
  `graduation_year` smallint unsigned DEFAULT NULL,
  PRIMARY KEY (`health_professional_id`,`specialty_code`),
  KEY `ix_professional_specialty_specialty_code` (`specialty_code`),
  CONSTRAINT `fk_professional_specialty_professional` FOREIGN KEY (`health_professional_id`) REFERENCES `health_professional` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping routines for database 'professionals_db'
--

--
-- Current Database: `medical_records_db`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `medical_records_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `medical_records_db`;

--
-- Table structure for table `allergy`
--

DROP TABLE IF EXISTS `allergy`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `allergy` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `medical_record_id` bigint unsigned NOT NULL,
  `type` enum('MEDICATION','FOOD','ENVIRONMENTAL','LATEX','STING','OTHER') NOT NULL,
  `substance` varchar(160) NOT NULL,
  `medication_code` varchar(30) DEFAULT NULL,
  `reaction` varchar(300) DEFAULT NULL,
  `severity` enum('MILD','MODERATE','SEVERE','ANAPHYLAXIS') NOT NULL DEFAULT 'MILD',
  `status` enum('ACTIVE','RESOLVED','DISMISSED') NOT NULL DEFAULT 'ACTIVE',
  `recorded_by_professional_public_id` binary(16) DEFAULT NULL,
  `recorded_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_allergy_medical_record_id_type_substance` (`medical_record_id`,`type`,`substance`),
  KEY `ix_allergy_medication_code` (`medication_code`),
  KEY `ix_allergy_recorded_by_professional_public_id` (`recorded_by_professional_public_id`),
  CONSTRAINT `fk_allergy_medical_record` FOREIGN KEY (`medical_record_id`) REFERENCES `medical_record` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `clinical_encounter`
--

DROP TABLE IF EXISTS `clinical_encounter`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `clinical_encounter` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `organization_public_id` binary(16) NOT NULL,
  `medical_record_id` bigint unsigned NOT NULL,
  `appointment_public_id` binary(16) DEFAULT NULL,
  `health_professional_public_id` binary(16) NOT NULL,
  `branch_public_id` binary(16) NOT NULL,
  `type` enum('INITIAL_CONSULTATION','FOLLOW_UP_CONSULTATION','EMERGENCY','TELECONSULTATION','PROCEDURE','SURGERY','CONSULTATION_REFERRAL') NOT NULL DEFAULT 'INITIAL_CONSULTATION',
  `starts_at` timestamp NOT NULL,
  `ends_at` timestamp NULL DEFAULT NULL,
  `encrypted` varbinary(8192) DEFAULT NULL,
  `encrypted_subjective_notes` varbinary(8192) DEFAULT NULL,
  `encrypted_objective_notes` varbinary(8192) DEFAULT NULL,
  `encrypted_assessment` varbinary(8192) DEFAULT NULL,
  `encrypted_plan` varbinary(8192) DEFAULT NULL,
  `encryption_key_public_id` binary(16) NOT NULL,
  `suggested_next_appointment_at` date DEFAULT NULL,
  `status` enum('DRAFT','SIGNED','VOIDED') NOT NULL DEFAULT 'DRAFT',
  `content_hash` char(64) DEFAULT NULL,
  `signed_at` timestamp NULL DEFAULT NULL,
  `digital_signature` varbinary(2048) DEFAULT NULL,
  `digital_certificate_public_id` binary(16) DEFAULT NULL,
  `voided_at` timestamp NULL DEFAULT NULL,
  `voided_by_user_public_id` binary(16) DEFAULT NULL,
  `cancellation_reason` varchar(300) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_clinical_encounter_public_id` (`public_id`),
  UNIQUE KEY `uq_clinical_encounter_appointment_public_id` (`appointment_public_id`),
  KEY `ix_clinical_encounter_medical_record_id_starts_at` (`medical_record_id`,`starts_at`),
  KEY `ix_clinical_encounter_health_professional_public_id_key` (`health_professional_public_id`,`starts_at`),
  KEY `ix_clinical_encounter_organization_public_id_status_key` (`organization_public_id`,`status`,`starts_at`),
  KEY `ix_clinical_encounter_branch_public_id` (`branch_public_id`),
  KEY `ix_clinical_encounter_encryption_key_public_id` (`encryption_key_public_id`),
  KEY `ix_clinical_encounter_digital_certificate_public_id` (`digital_certificate_public_id`),
  KEY `ix_clinical_encounter_voided_by_user_public_id` (`voided_by_user_public_id`),
  CONSTRAINT `fk_clinical_encounter_medical_record` FOREIGN KEY (`medical_record_id`) REFERENCES `medical_record` (`id`),
  CONSTRAINT `ck_clinical_encounter_period` CHECK (((`ends_at` is null) or (`ends_at` >= `starts_at`))),
  CONSTRAINT `ck_clinical_encounter_signature` CHECK (((`status` <> _utf8mb4'SIGNED') or ((`signed_at` is not null) and (`content_hash` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`medical_records_user`@`%`*/ /*!50003 TRIGGER `trg_clinical_encounter_immutable_on_update` BEFORE UPDATE ON `clinical_encounter` FOR EACH ROW BEGIN
    IF OLD.status = 'SIGNED' THEN
        -- Anular es la unica transicion admitida despues de firmar.
        IF NEW.status = 'VOIDED' THEN
            IF NOT (NEW.medical_record_id            <=> OLD.medical_record_id
                AND NEW.public_id                    <=> OLD.public_id
                AND NEW.organization_public_id       <=> OLD.organization_public_id
                AND NEW.appointment_public_id        <=> OLD.appointment_public_id
                AND NEW.health_professional_public_id <=> OLD.health_professional_public_id
                AND NEW.branch_public_id             <=> OLD.branch_public_id
                AND NEW.type                         <=> OLD.type
                AND NEW.starts_at                    <=> OLD.starts_at
                AND NEW.ends_at                      <=> OLD.ends_at
                AND NEW.encrypted                    <=> OLD.encrypted
                AND NEW.encrypted_subjective_notes   <=> OLD.encrypted_subjective_notes
                AND NEW.encrypted_objective_notes    <=> OLD.encrypted_objective_notes
                AND NEW.encrypted_assessment         <=> OLD.encrypted_assessment
                AND NEW.encrypted_plan               <=> OLD.encrypted_plan
                AND NEW.encryption_key_public_id     <=> OLD.encryption_key_public_id
                AND NEW.content_hash                 <=> OLD.content_hash
                AND NEW.signed_at                    <=> OLD.signed_at
                AND NEW.digital_signature            <=> OLD.digital_signature
                AND NEW.digital_certificate_public_id <=> OLD.digital_certificate_public_id) THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'CLINICAL_ENCOUNTER_IMMUTABLE: al anular solo pueden cambiar status, voided_at, voided_by_user_public_id y cancellation_reason';
            END IF;
        ELSE
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'CLINICAL_ENCOUNTER_IMMUTABLE: un encuentro firmado no se sobrescribe; use encounter_addendum';
        END IF;
    END IF;

    -- Un encuentro anulado queda congelado por completo.
    IF OLD.status = 'VOIDED' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'CLINICAL_ENCOUNTER_IMMUTABLE: un encuentro anulado no admite modificaciones';
    END IF;
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`medical_records_user`@`%`*/ /*!50003 TRIGGER `trg_clinical_encounter_no_delete` BEFORE DELETE ON `clinical_encounter` FOR EACH ROW BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'CLINICAL_ENCOUNTER_IMMUTABLE: los registros clinicos se anulan, no se borran';
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;

--
-- Table structure for table `diagnosis`
--

DROP TABLE IF EXISTS `diagnosis`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `diagnosis` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `clinical_encounter_id` bigint unsigned NOT NULL,
  `icd10_code` varchar(10) NOT NULL,
  `type` enum('PROVISIONAL','FINAL','DISMISSED') NOT NULL DEFAULT 'PROVISIONAL',
  `is_primary` tinyint(1) NOT NULL DEFAULT '0',
  `is_chronic` tinyint(1) NOT NULL DEFAULT '0',
  `laterality` enum('NOT_APPLICABLE','LEFT','RIGHT','BILATERAL') NOT NULL DEFAULT 'NOT_APPLICABLE',
  `encrypted_observation` varbinary(2048) DEFAULT NULL,
  `status` enum('ACTIVE','AT_REMISSION','RESOLVED') NOT NULL DEFAULT 'ACTIVE',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_diagnosis_clinical_encounter_id_icd10_code` (`clinical_encounter_id`,`icd10_code`),
  KEY `ix_diagnosis_icd10_code_is_chronic` (`icd10_code`,`is_chronic`),
  CONSTRAINT `fk_diagnosis_clinical_encounter` FOREIGN KEY (`clinical_encounter_id`) REFERENCES `clinical_encounter` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `encounter_addendum`
--

DROP TABLE IF EXISTS `encounter_addendum`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `encounter_addendum` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `clinical_encounter_id` bigint unsigned NOT NULL,
  `health_professional_public_id` binary(16) NOT NULL,
  `reason` enum('CORRECTION','ADDITION','LATE_RESULT','OTHER') NOT NULL,
  `encrypted_text` varbinary(8192) NOT NULL,
  `encryption_key_public_id` binary(16) NOT NULL,
  `content_hash` char(64) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_encounter_addendum_clinical_encounter_id_created_at` (`clinical_encounter_id`,`created_at`),
  KEY `ix_encounter_addendum_health_professional_public_id` (`health_professional_public_id`),
  KEY `ix_encounter_addendum_encryption_key_public_id` (`encryption_key_public_id`),
  CONSTRAINT `fk_encounter_addendum_clinical_encounter` FOREIGN KEY (`clinical_encounter_id`) REFERENCES `clinical_encounter` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`medical_records_user`@`%`*/ /*!50003 TRIGGER `trg_encounter_addendum_no_update` BEFORE UPDATE ON `encounter_addendum` FOR EACH ROW BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ENCOUNTER_ADDENDUM_IMMUTABLE: un addendum es una adicion inmutable';
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`medical_records_user`@`%`*/ /*!50003 TRIGGER `trg_encounter_addendum_no_delete` BEFORE DELETE ON `encounter_addendum` FOR EACH ROW BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ENCOUNTER_ADDENDUM_IMMUTABLE: un addendum es una adicion inmutable';
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;

--
-- Table structure for table `flyway_schema_history`
--

DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `medical_background`
--

DROP TABLE IF EXISTS `medical_background`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `medical_background` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `medical_record_id` bigint unsigned NOT NULL,
  `type` enum('PATHOLOGICAL','FAMILY','SURGICAL','TRAUMATIC','HABIT','OBSTETRIC_GYNECOLOGICAL','IMMUNIZATION','TRANSFUSIONAL','OTHER') NOT NULL,
  `title` varchar(160) NOT NULL,
  `encrypted_detail` varbinary(4096) DEFAULT NULL,
  `icd10_code` varchar(10) DEFAULT NULL,
  `event_date` date DEFAULT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `recorded_by_professional_public_id` binary(16) DEFAULT NULL,
  `recorded_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_medical_background_medical_record_id_type` (`medical_record_id`,`type`),
  KEY `ix_medical_background_icd10_code` (`icd10_code`),
  KEY `ix_medical_background_recorded_by_professional_public_id` (`recorded_by_professional_public_id`),
  CONSTRAINT `fk_medical_background_medical_record` FOREIGN KEY (`medical_record_id`) REFERENCES `medical_record` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `medical_record`
--

DROP TABLE IF EXISTS `medical_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `medical_record` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `organization_public_id` binary(16) NOT NULL,
  `patient_public_id` binary(16) NOT NULL,
  `record_number` varchar(30) NOT NULL,
  `opened_on` date NOT NULL DEFAULT (curdate()),
  `opening_branch_public_id` binary(16) DEFAULT NULL,
  `encryption_key_public_id` binary(16) NOT NULL,
  `shares_with_network` tinyint(1) NOT NULL DEFAULT '0',
  `sharing_authorized_at` timestamp NULL DEFAULT NULL,
  `status` enum('ACTIVE','ARCHIVED','CLOSED') NOT NULL DEFAULT 'ACTIVE',
  `retention_until` date DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_medical_record_public_id` (`public_id`),
  UNIQUE KEY `uq_medical_record_organization_public_id_record_number` (`organization_public_id`,`record_number`),
  UNIQUE KEY `uq_medical_record_organization_public_id_patient_public_id` (`organization_public_id`,`patient_public_id`),
  KEY `ix_medical_record_patient_public_id` (`patient_public_id`),
  KEY `ix_medical_record_opening_branch_public_id` (`opening_branch_public_id`),
  KEY `ix_medical_record_encryption_key_public_id` (`encryption_key_public_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `performed_procedure`
--

DROP TABLE IF EXISTS `performed_procedure`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `performed_procedure` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `clinical_encounter_id` bigint unsigned NOT NULL,
  `service_offering_public_id` binary(16) NOT NULL,
  `physical_resource_public_id` binary(16) DEFAULT NULL,
  `responsible_professional_public_id` binary(16) NOT NULL,
  `starts_at` timestamp NOT NULL,
  `ends_at` timestamp NULL DEFAULT NULL,
  `anesthesia_type` enum('NONE','LOCAL','REGIONAL','SEDATION','GENERAL') NOT NULL DEFAULT 'NONE',
  `encrypted_description` varbinary(8192) DEFAULT NULL,
  `encrypted_complications` varbinary(4096) DEFAULT NULL,
  `informed_consent_public_id` binary(16) DEFAULT NULL,
  `status` enum('SCHEDULED','IN_PROGRESS','COMPLETED','SUSPENDED') NOT NULL DEFAULT 'SCHEDULED',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_performed_procedure_clinical_encounter_id` (`clinical_encounter_id`),
  KEY `ix_performed_procedure_physical_resource_public_id_starts_at` (`physical_resource_public_id`,`starts_at`),
  KEY `ix_performed_procedure_service_offering_public_id` (`service_offering_public_id`),
  KEY `ix_performed_procedure_responsible_professional_public_id` (`responsible_professional_public_id`),
  KEY `ix_performed_procedure_informed_consent_public_id` (`informed_consent_public_id`),
  CONSTRAINT `fk_performed_procedure_clinical_encounter` FOREIGN KEY (`clinical_encounter_id`) REFERENCES `clinical_encounter` (`id`),
  CONSTRAINT `ck_performed_procedure_period` CHECK (((`ends_at` is null) or (`ends_at` >= `starts_at`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `prescription`
--

DROP TABLE IF EXISTS `prescription`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `prescription` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `organization_public_id` binary(16) NOT NULL,
  `clinical_encounter_id` bigint unsigned NOT NULL,
  `folio` varchar(30) NOT NULL,
  `issued_at` timestamp NOT NULL,
  `validity_days` smallint unsigned NOT NULL DEFAULT '30',
  `encrypted_general_instructions` varbinary(4096) DEFAULT NULL,
  `status` enum('ISSUED','PARTIALLY_DISPENSED','DISPENSED','EXPIRED','VOIDED') NOT NULL DEFAULT 'ISSUED',
  `pdf_document_public_id` binary(16) DEFAULT NULL,
  `sha256_hash` char(64) DEFAULT NULL,
  `signed_at` timestamp NULL DEFAULT NULL,
  `digital_certificate_public_id` binary(16) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_prescription_public_id` (`public_id`),
  UNIQUE KEY `uq_prescription_organization_public_id_folio` (`organization_public_id`,`folio`),
  KEY `ix_prescription_clinical_encounter_id` (`clinical_encounter_id`),
  KEY `ix_prescription_pdf_document_public_id` (`pdf_document_public_id`),
  KEY `ix_prescription_digital_certificate_public_id` (`digital_certificate_public_id`),
  CONSTRAINT `fk_prescription_clinical_encounter` FOREIGN KEY (`clinical_encounter_id`) REFERENCES `clinical_encounter` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `prescription_item`
--

DROP TABLE IF EXISTS `prescription_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `prescription_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `prescription_id` bigint unsigned NOT NULL,
  `item_number` tinyint unsigned NOT NULL,
  `medication_code` varchar(30) DEFAULT NULL,
  `free_text_description` varchar(200) DEFAULT NULL,
  `dose` varchar(80) NOT NULL,
  `administration_route` enum('ORAL','INTRAVENOUS','INTRAMUSCULAR','SUBCUTANEOUS','TOPICAL','OPHTHALMIC','OTIC','NASAL','RECTAL','INHALATION','OTHER') NOT NULL DEFAULT 'ORAL',
  `frequency_hours` smallint unsigned DEFAULT NULL,
  `duration_days` smallint unsigned DEFAULT NULL,
  `quantity` decimal(8,2) NOT NULL DEFAULT '1.00',
  `unit` varchar(20) DEFAULT NULL,
  `encrypted_instructions` varbinary(2048) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_prescription_item_prescription_id_item_number` (`prescription_id`,`item_number`),
  KEY `ix_prescription_item_medication_code` (`medication_code`),
  CONSTRAINT `fk_prescription_item_prescription` FOREIGN KEY (`prescription_id`) REFERENCES `prescription` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_prescription_item_medication` CHECK (((`medication_code` is not null) or (`free_text_description` is not null))),
  CONSTRAINT `ck_prescription_item_quantity` CHECK ((`quantity` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `study_order`
--

DROP TABLE IF EXISTS `study_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `study_order` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `organization_public_id` binary(16) NOT NULL,
  `clinical_encounter_id` bigint unsigned NOT NULL,
  `study_type_code` varchar(30) NOT NULL,
  `priority` enum('ROUTINE','URGENT','STAT') NOT NULL DEFAULT 'ROUTINE',
  `instructions` varchar(500) DEFAULT NULL,
  `icd10_code` varchar(10) DEFAULT NULL,
  `status` enum('REQUESTED','SCHEDULED','SAMPLE_COLLECTED','IN_PROGRESS','REPORTED','VOIDED') NOT NULL DEFAULT 'REQUESTED',
  `requested_by_professional_public_id` binary(16) NOT NULL,
  `requested_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `collection_appointment_public_id` binary(16) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_study_order_public_id` (`public_id`),
  KEY `ix_study_order_clinical_encounter_id` (`clinical_encounter_id`),
  KEY `ix_study_order_organization_public_id_status_requested_at` (`organization_public_id`,`status`,`requested_at`),
  KEY `ix_study_order_study_type_code` (`study_type_code`),
  KEY `ix_study_order_requested_by_professional_public_id` (`requested_by_professional_public_id`),
  KEY `ix_study_order_icd10_code` (`icd10_code`),
  KEY `ix_study_order_collection_appointment_public_id` (`collection_appointment_public_id`),
  CONSTRAINT `fk_study_order_clinical_encounter` FOREIGN KEY (`clinical_encounter_id`) REFERENCES `clinical_encounter` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `study_result`
--

DROP TABLE IF EXISTS `study_result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `study_result` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `study_order_id` bigint unsigned NOT NULL,
  `parameter` varchar(120) NOT NULL DEFAULT 'RESULT',
  `numeric_value` decimal(14,4) DEFAULT NULL,
  `encrypted_text_value` varbinary(4096) DEFAULT NULL,
  `unit` varchar(30) DEFAULT NULL,
  `reference_min` decimal(14,4) DEFAULT NULL,
  `reference_max` decimal(14,4) DEFAULT NULL,
  `out_of_range` tinyint(1) NOT NULL DEFAULT '0',
  `status` enum('PRELIMINARY','FINAL','CORRECTED') NOT NULL DEFAULT 'PRELIMINARY',
  `document_public_id` binary(16) DEFAULT NULL,
  `reported_by_professional_public_id` binary(16) DEFAULT NULL,
  `reported_at` timestamp NULL DEFAULT NULL,
  `delivered_to_patient_at` timestamp NULL DEFAULT NULL,
  `encryption_key_public_id` binary(16) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_study_result_study_order_id_parameter` (`study_order_id`,`parameter`),
  KEY `ix_study_result_status_reported_at` (`status`,`reported_at`),
  KEY `ix_study_result_document_public_id` (`document_public_id`),
  KEY `ix_study_result_reported_by_professional_public_id` (`reported_by_professional_public_id`),
  KEY `ix_study_result_encryption_key_public_id` (`encryption_key_public_id`),
  CONSTRAINT `fk_study_result_study_order` FOREIGN KEY (`study_order_id`) REFERENCES `study_order` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `vital_sign`
--

DROP TABLE IF EXISTS `vital_sign`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `vital_sign` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `clinical_encounter_id` bigint unsigned NOT NULL,
  `measured_at` timestamp NOT NULL,
  `systolic_pressure` smallint unsigned DEFAULT NULL,
  `diastolic_pressure` smallint unsigned DEFAULT NULL,
  `heart_rate` smallint unsigned DEFAULT NULL,
  `respiratory_rate` smallint unsigned DEFAULT NULL,
  `temperature_c` decimal(4,1) DEFAULT NULL,
  `oxygen_saturation` tinyint unsigned DEFAULT NULL,
  `blood_glucose_mg_dl` smallint unsigned DEFAULT NULL,
  `weight_kg` decimal(6,2) DEFAULT NULL,
  `height_cm` decimal(5,1) DEFAULT NULL,
  `bmi` decimal(5,2) GENERATED ALWAYS AS (round((`weight_kg` / pow((nullif(`height_cm`,0) / 100),2)),2)) STORED,
  `abdominal_circumference_cm` decimal(5,1) DEFAULT NULL,
  `pain_scale` tinyint unsigned DEFAULT NULL,
  `recorded_by_user_public_id` binary(16) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_vital_sign_clinical_encounter_id_measured_at` (`clinical_encounter_id`,`measured_at`),
  KEY `ix_vital_sign_recorded_by_user_public_id` (`recorded_by_user_public_id`),
  CONSTRAINT `fk_vital_sign_clinical_encounter` FOREIGN KEY (`clinical_encounter_id`) REFERENCES `clinical_encounter` (`id`),
  CONSTRAINT `ck_vital_sign_oxygen_saturation` CHECK (((`oxygen_saturation` is null) or (`oxygen_saturation` between 30 and 100))),
  CONSTRAINT `ck_vital_sign_pain_scale` CHECK (((`pain_scale` is null) or (`pain_scale` between 0 and 10))),
  CONSTRAINT `ck_vital_sign_pressure` CHECK (((`systolic_pressure` is null) or (`diastolic_pressure` is null) or (`systolic_pressure` >= `diastolic_pressure`))),
  CONSTRAINT `ck_vital_sign_temperature` CHECK (((`temperature_c` is null) or (`temperature_c` between 25.0 and 45.0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping routines for database 'medical_records_db'
--

--
-- Current Database: `documents_db`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `documents_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `documents_db`;

--
-- Table structure for table `consent_event`
--

DROP TABLE IF EXISTS `consent_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `consent_event` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `informed_consent_id` bigint unsigned NOT NULL,
  `type` varchar(30) NOT NULL,
  `occurred_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `user_account_public_id` binary(16) DEFAULT NULL,
  `ip_origin` varbinary(16) DEFAULT NULL,
  `user_agent` varchar(300) DEFAULT NULL,
  `detail` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_consent_event_informed_consent_id_occurred_at` (`informed_consent_id`,`occurred_at`),
  KEY `ix_consent_event_user_account_public_id` (`user_account_public_id`),
  CONSTRAINT `fk_consent_event_informed_consent` FOREIGN KEY (`informed_consent_id`) REFERENCES `informed_consent` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_consent_event_type` CHECK ((`type` in (_utf8mb4'GENERATED',_utf8mb4'SENT_WHATSAPP',_utf8mb4'SENT_EMAIL',_utf8mb4'OPEN',_utf8mb4'FULLY_READ',_utf8mb4'OTP_REQUESTED',_utf8mb4'OTP_VALIDATED',_utf8mb4'SIGNED',_utf8mb4'REJECTED',_utf8mb4'REVOKED',_utf8mb4'DOWNLOADED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `consent_template`
--

DROP TABLE IF EXISTS `consent_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `consent_template` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `organization_public_id` binary(16) DEFAULT NULL,
  `code` varchar(40) NOT NULL,
  `version` smallint unsigned NOT NULL DEFAULT '1',
  `title` varchar(200) NOT NULL,
  `specialty_code` varchar(20) DEFAULT NULL,
  `markdown_body` mediumtext NOT NULL,
  `variables` json DEFAULT NULL,
  `requires_witness` tinyint(1) NOT NULL DEFAULT '0',
  `requires_guardian_for_minor` tinyint(1) NOT NULL DEFAULT '1',
  `valid_from` date NOT NULL,
  `valid_until` date DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `body_hash` char(64) NOT NULL,
  `created_by_user_public_id` binary(16) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_consent_template_public_id` (`public_id`),
  UNIQUE KEY `uq_consent_template_organization_public_id_code_version` (`organization_public_id`,`code`,`version`),
  KEY `ix_consent_template_organization_public_id_status` (`organization_public_id`,`status`),
  KEY `ix_consent_template_specialty_code` (`specialty_code`),
  KEY `ix_consent_template_created_by_user_public_id` (`created_by_user_public_id`),
  CONSTRAINT `ck_consent_template_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'PUBLISHED',_utf8mb4'DEPRECATED'))),
  CONSTRAINT `ck_consent_template_validity` CHECK (((`valid_until` is null) or (`valid_until` >= `valid_from`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `document`
--

DROP TABLE IF EXISTS `document`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `document` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `organization_public_id` binary(16) NOT NULL,
  `patient_public_id` binary(16) DEFAULT NULL,
  `type` varchar(30) NOT NULL,
  `original_filename` varchar(255) NOT NULL,
  `mime_type` varchar(120) NOT NULL,
  `size_bytes` bigint unsigned NOT NULL,
  `storage_provider` varchar(20) NOT NULL DEFAULT 'S3',
  `region` varchar(40) DEFAULT NULL,
  `bucket` varchar(120) NOT NULL,
  `key_object` varchar(500) NOT NULL,
  `object_version` varchar(80) DEFAULT NULL,
  `sha256_hash` char(64) NOT NULL,
  `server_side_encryption` varchar(20) NOT NULL DEFAULT 'SSE_KMS',
  `encryption_key_public_id` binary(16) DEFAULT NULL,
  `is_dicom` tinyint(1) NOT NULL DEFAULT '0',
  `metadata` json DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'UPLOAD_PENDING',
  `antivirus_status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `uploaded_by_user_public_id` binary(16) DEFAULT NULL,
  `uploaded_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `retention_until` date DEFAULT NULL,
  `voided_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_document_public_id` (`public_id`),
  UNIQUE KEY `uq_document_bucket_key_object` (`bucket`,`key_object`),
  KEY `ix_document_organization_public_id_type_uploaded_at` (`organization_public_id`,`type`,`uploaded_at`),
  KEY `ix_document_patient_public_id` (`patient_public_id`),
  KEY `ix_document_status` (`status`),
  KEY `ix_document_sha256_hash` (`sha256_hash`),
  KEY `ix_document_encryption_key_public_id` (`encryption_key_public_id`),
  KEY `ix_document_uploaded_by_user_public_id` (`uploaded_by_user_public_id`),
  CONSTRAINT `ck_document_antivirus_status` CHECK ((`antivirus_status` in (_utf8mb4'NOT_APPLICABLE',_utf8mb4'PENDING',_utf8mb4'CLEAN',_utf8mb4'INFECTED'))),
  CONSTRAINT `ck_document_server_side_encryption` CHECK ((`server_side_encryption` in (_utf8mb4'SSE_S3',_utf8mb4'SSE_KMS',_utf8mb4'NONE'))),
  CONSTRAINT `ck_document_size_bytes` CHECK ((`size_bytes` > 0)),
  CONSTRAINT `ck_document_status` CHECK ((`status` in (_utf8mb4'UPLOAD_PENDING',_utf8mb4'AVAILABLE',_utf8mb4'QUARANTINED',_utf8mb4'VOIDED',_utf8mb4'ARCHIVED',_utf8mb4'PURGED'))),
  CONSTRAINT `ck_document_storage_provider` CHECK ((`storage_provider` in (_utf8mb4'S3',_utf8mb4'MINIO',_utf8mb4'GCS',_utf8mb4'AZURE_BLOB'))),
  CONSTRAINT `ck_document_type` CHECK ((`type` in (_utf8mb4'X_RAY',_utf8mb4'CT_SCAN',_utf8mb4'MRI',_utf8mb4'ULTRASOUND',_utf8mb4'DICOM_STUDY',_utf8mb4'LAB_RESULT',_utf8mb4'MEDICAL_REPORT',_utf8mb4'INFORMED_CONSENT',_utf8mb4'PRESCRIPTION_PDF',_utf8mb4'INVOICE_PDF',_utf8mb4'INVOICE_XML',_utf8mb4'CLINICAL_PHOTO',_utf8mb4'IDENTITY_DOCUMENT',_utf8mb4'SIGNATURE_IMAGE',_utf8mb4'OTHER')))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `document_link`
--

DROP TABLE IF EXISTS `document_link`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `document_link` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `document_id` bigint unsigned NOT NULL,
  `entity_type` varchar(30) NOT NULL,
  `linked_entity_public_id` binary(16) NOT NULL,
  `description` varchar(250) DEFAULT NULL,
  `linked_by_user_public_id` binary(16) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_document_link_document_id_entity_type_linked_enti_key` (`document_id`,`entity_type`,`linked_entity_public_id`),
  KEY `ix_document_link_entity_type_linked_entity_public_id` (`entity_type`,`linked_entity_public_id`),
  KEY `ix_document_link_linked_by_user_public_id` (`linked_by_user_public_id`),
  CONSTRAINT `fk_document_link_document` FOREIGN KEY (`document_id`) REFERENCES `document` (`id`),
  CONSTRAINT `ck_document_link_entity_type` CHECK ((`entity_type` in (_utf8mb4'MEDICAL_RECORD',_utf8mb4'ENCOUNTER',_utf8mb4'STUDY_ORDER',_utf8mb4'STUDY_RESULT',_utf8mb4'CONSENT',_utf8mb4'PRESCRIPTION',_utf8mb4'INVOICE',_utf8mb4'APPOINTMENT',_utf8mb4'PROFESSIONAL')))
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `flyway_schema_history`
--

DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `informed_consent`
--

DROP TABLE IF EXISTS `informed_consent`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `informed_consent` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `organization_public_id` binary(16) NOT NULL,
  `template_id` bigint unsigned NOT NULL,
  `template_version` smallint unsigned NOT NULL,
  `patient_public_id` binary(16) NOT NULL,
  `medical_record_public_id` binary(16) NOT NULL,
  `appointment_public_id` binary(16) DEFAULT NULL,
  `clinical_encounter_public_id` binary(16) DEFAULT NULL,
  `health_professional_public_id` binary(16) NOT NULL,
  `service_offering_public_id` binary(16) DEFAULT NULL,
  `rendered_body_hash` char(64) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'GENERATED',
  `signature_method` varchar(30) DEFAULT NULL,
  `signer_type` varchar(20) NOT NULL DEFAULT 'PATIENT',
  `signing_patient_public_id` binary(16) DEFAULT NULL,
  `signer_document_number` varchar(25) DEFAULT NULL,
  `witness_name` varchar(160) DEFAULT NULL,
  `witness_document_number` varchar(25) DEFAULT NULL,
  `pdf_document_id` bigint unsigned DEFAULT NULL,
  `document_sha256_hash` char(64) DEFAULT NULL,
  `digital_signature` varbinary(4096) DEFAULT NULL,
  `digital_certificate_public_id` binary(16) DEFAULT NULL,
  `tsa_timestamp` varbinary(2048) DEFAULT NULL,
  `generated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at` timestamp NULL DEFAULT NULL,
  `signed_at` timestamp NULL DEFAULT NULL,
  `signature_ip` varbinary(16) DEFAULT NULL,
  `signature_user_agent` varchar(300) DEFAULT NULL,
  `signature_geolocation` varchar(60) DEFAULT NULL,
  `revoked_at` timestamp NULL DEFAULT NULL,
  `revocation_reason` varchar(300) DEFAULT NULL,
  `encryption_key_public_id` binary(16) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_informed_consent_public_id` (`public_id`),
  KEY `ix_informed_consent_patient_public_id_status` (`patient_public_id`,`status`),
  KEY `ix_informed_consent_medical_record_public_id` (`medical_record_public_id`),
  KEY `ix_informed_consent_organization_public_id_status_ge_key` (`organization_public_id`,`status`,`generated_at`),
  KEY `ix_informed_consent_appointment_public_id` (`appointment_public_id`),
  KEY `ix_informed_consent_clinical_encounter_public_id` (`clinical_encounter_public_id`),
  KEY `ix_informed_consent_template_id` (`template_id`),
  KEY `ix_informed_consent_health_professional_public_id` (`health_professional_public_id`),
  KEY `ix_informed_consent_service_offering_public_id` (`service_offering_public_id`),
  KEY `ix_informed_consent_pdf_document_id` (`pdf_document_id`),
  KEY `ix_informed_consent_digital_certificate_public_id` (`digital_certificate_public_id`),
  KEY `ix_informed_consent_signing_patient_public_id` (`signing_patient_public_id`),
  KEY `ix_informed_consent_encryption_key_public_id` (`encryption_key_public_id`),
  CONSTRAINT `fk_informed_consent_pdf_document` FOREIGN KEY (`pdf_document_id`) REFERENCES `document` (`id`),
  CONSTRAINT `fk_informed_consent_template` FOREIGN KEY (`template_id`) REFERENCES `consent_template` (`id`),
  CONSTRAINT `ck_informed_consent_signature_method` CHECK (((`signature_method` is null) or (`signature_method` in (_utf8mb4'DIGITAL_CERTIFICATE',_utf8mb4'OTP_WHATSAPP',_utf8mb4'OTP_SMS',_utf8mb4'BIOMETRIC_TRACE',_utf8mb4'SCANNED_HANDWRITTEN')))),
  CONSTRAINT `ck_informed_consent_signed_data` CHECK (((`status` <> _utf8mb4'SIGNED') or ((`signed_at` is not null) and (`signature_method` is not null) and (`document_sha256_hash` is not null)))),
  CONSTRAINT `ck_informed_consent_signer_type` CHECK ((`signer_type` in (_utf8mb4'PATIENT',_utf8mb4'LEGAL_GUARDIAN',_utf8mb4'REPRESENTATIVE'))),
  CONSTRAINT `ck_informed_consent_status` CHECK ((`status` in (_utf8mb4'GENERATED',_utf8mb4'SENT',_utf8mb4'VIEWED',_utf8mb4'SIGNED',_utf8mb4'REJECTED',_utf8mb4'REVOKED',_utf8mb4'EXPIRED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping routines for database 'documents_db'
--

--
-- Current Database: `billing_db`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `billing_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `billing_db`;

--
-- Table structure for table `billing_configuration`
--

DROP TABLE IF EXISTS `billing_configuration`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `billing_configuration` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `organization_public_id` binary(16) NOT NULL,
  `nit` varchar(15) NOT NULL,
  `legal_name` varchar(180) NOT NULL,
  `modality` enum('ONLINE_ELECTRONIC','ONLINE_COMPUTERIZED','MANUAL') NOT NULL DEFAULT 'ONLINE_COMPUTERIZED',
  `environment` enum('PILOT','PRODUCTION') NOT NULL DEFAULT 'PILOT',
  `encrypted_delegated_token` varbinary(2048) DEFAULT NULL,
  `token_valid_until` timestamp NULL DEFAULT NULL,
  `encryption_key_public_id` binary(16) DEFAULT NULL,
  `digital_certificate_public_id` binary(16) DEFAULT NULL,
  `vat_caption` varchar(300) DEFAULT NULL,
  `notification_email` varchar(160) DEFAULT NULL,
  `status` enum('CONFIGURING','ACTIVE','SUSPENDED') NOT NULL DEFAULT 'CONFIGURING',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_billing_configuration_organization_public_id` (`organization_public_id`),
  KEY `ix_billing_configuration_encryption_key_public_id` (`encryption_key_public_id`),
  KEY `ix_billing_configuration_digital_certificate_public_id` (`digital_certificate_public_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `cufd`
--

DROP TABLE IF EXISTS `cufd`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cufd` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `point_of_sale_id` bigint unsigned NOT NULL,
  `cuis_id` bigint unsigned NOT NULL,
  `code` varchar(200) NOT NULL,
  `control_code` varchar(30) NOT NULL,
  `address` varchar(250) NOT NULL,
  `valid_from` timestamp NOT NULL,
  `valid_until` timestamp NOT NULL,
  `requested_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_cufd_code` (`code`),
  KEY `ix_cufd_point_of_sale_id_valid_until` (`point_of_sale_id`,`valid_until`),
  KEY `ix_cufd_cuis_id` (`cuis_id`),
  CONSTRAINT `fk_cufd_cuis` FOREIGN KEY (`cuis_id`) REFERENCES `cuis` (`id`),
  CONSTRAINT `fk_cufd_point_of_sale` FOREIGN KEY (`point_of_sale_id`) REFERENCES `point_of_sale` (`id`),
  CONSTRAINT `ck_cufd_validity_range` CHECK ((`valid_until` > `valid_from`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `cuis`
--

DROP TABLE IF EXISTS `cuis`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cuis` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `point_of_sale_id` bigint unsigned NOT NULL,
  `code` varchar(30) NOT NULL,
  `valid_until` date NOT NULL,
  `requested_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `status` enum('ACTIVE','EXPIRED') NOT NULL DEFAULT 'ACTIVE',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_cuis_point_of_sale_id_code` (`point_of_sale_id`,`code`),
  KEY `ix_cuis_point_of_sale_id_status_valid_until` (`point_of_sale_id`,`status`,`valid_until`),
  CONSTRAINT `fk_cuis_point_of_sale` FOREIGN KEY (`point_of_sale_id`) REFERENCES `point_of_sale` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `document_sequence`
--

DROP TABLE IF EXISTS `document_sequence`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `document_sequence` (
  `point_of_sale_id` bigint unsigned NOT NULL,
  `tax_document_type` varchar(30) NOT NULL,
  `last_number` bigint unsigned NOT NULL DEFAULT '0',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`point_of_sale_id`,`tax_document_type`),
  CONSTRAINT `fk_document_sequence_point_of_sale` FOREIGN KEY (`point_of_sale_id`) REFERENCES `point_of_sale` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `flyway_schema_history`
--

DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `invoice`
--

DROP TABLE IF EXISTS `invoice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `invoice` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `organization_public_id` binary(16) NOT NULL,
  `branch_public_id` binary(16) NOT NULL,
  `patient_public_id` binary(16) NOT NULL,
  `point_of_sale_id` bigint unsigned NOT NULL,
  `cufd_id` bigint unsigned NOT NULL,
  `tax_document_type` enum('PURCHASE_SALE_INVOICE','HEALTH_SECTOR_INVOICE','PROFESSIONAL_FEES_RECEIPT') NOT NULL DEFAULT 'PURCHASE_SALE_INVOICE',
  `emission_type` enum('ONLINE','OFFLINE','BULK') NOT NULL DEFAULT 'ONLINE',
  `invoice_number` bigint unsigned NOT NULL,
  `cuf` varchar(80) NOT NULL,
  `cafc` varchar(30) DEFAULT NULL,
  `identity_document_type_code` tinyint unsigned NOT NULL DEFAULT '1',
  `customer_document_number` varchar(20) NOT NULL,
  `customer_document_complement` varchar(5) DEFAULT NULL,
  `customer_legal_name` varchar(200) NOT NULL,
  `customer_email` varchar(160) DEFAULT NULL,
  `exchange_rate` decimal(12,5) NOT NULL DEFAULT '1.00000',
  `total_amount` decimal(14,2) NOT NULL,
  `vat_taxable_amount` decimal(14,2) NOT NULL,
  `total_discount_amount` decimal(14,2) NOT NULL DEFAULT '0.00',
  `gift_card_amount` decimal(14,2) NOT NULL DEFAULT '0.00',
  `currency_total_amount` decimal(14,2) NOT NULL,
  `payment_method_code` tinyint unsigned NOT NULL DEFAULT '1',
  `card_number_suffix` varchar(4) DEFAULT NULL,
  `issued_at` timestamp NOT NULL,
  `tax_receipt_code` varchar(50) DEFAULT NULL,
  `validated_at` timestamp NULL DEFAULT NULL,
  `caption` varchar(500) DEFAULT NULL,
  `qr_url` varchar(500) DEFAULT NULL,
  `xml_hash` char(64) DEFAULT NULL,
  `xml_document_public_id` binary(16) DEFAULT NULL,
  `pdf_document_public_id` binary(16) DEFAULT NULL,
  `status` enum('ISSUED','SEND_PENDING','DEMO_ACCEPTED','VALIDATED','REJECTED','VOIDED') NOT NULL DEFAULT 'ISSUED',
  `cancellation_reason_code` tinyint unsigned DEFAULT NULL,
  `voided_at` timestamp NULL DEFAULT NULL,
  `issued_by_user_public_id` binary(16) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_invoice_public_id` (`public_id`),
  UNIQUE KEY `uq_invoice_cuf` (`cuf`),
  UNIQUE KEY `uq_invoice_point_of_sale_id_tax_document_type_invoice_number` (`point_of_sale_id`,`tax_document_type`,`invoice_number`),
  KEY `ix_invoice_organization_public_id_issued_at` (`organization_public_id`,`issued_at`),
  KEY `ix_invoice_patient_public_id_issued_at` (`patient_public_id`,`issued_at`),
  KEY `ix_invoice_status_issued_at` (`status`,`issued_at`),
  KEY `ix_invoice_cufd_id` (`cufd_id`),
  KEY `ix_invoice_branch_public_id` (`branch_public_id`),
  KEY `ix_invoice_xml_document_public_id` (`xml_document_public_id`),
  KEY `ix_invoice_pdf_document_public_id` (`pdf_document_public_id`),
  KEY `ix_invoice_issued_by_user_public_id` (`issued_by_user_public_id`),
  CONSTRAINT `fk_invoice_cufd` FOREIGN KEY (`cufd_id`) REFERENCES `cufd` (`id`),
  CONSTRAINT `fk_invoice_point_of_sale` FOREIGN KEY (`point_of_sale_id`) REFERENCES `point_of_sale` (`id`),
  CONSTRAINT `ck_invoice_amounts` CHECK (((`total_amount` >= 0) and (`vat_taxable_amount` >= 0) and (`total_discount_amount` >= 0))),
  CONSTRAINT `ck_invoice_offline_requires_cafc` CHECK (((`emission_type` <> _utf8mb4'OFFLINE') or (`cafc` is not null))),
  CONSTRAINT `ck_invoice_voided_requires_reason` CHECK (((`status` <> _utf8mb4'VOIDED') or ((`voided_at` is not null) and (`cancellation_reason_code` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `invoice_item`
--

DROP TABLE IF EXISTS `invoice_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `invoice_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `invoice_id` bigint unsigned NOT NULL,
  `item_number` smallint unsigned NOT NULL,
  `service_offering_public_id` binary(16) DEFAULT NULL,
  `appointment_public_id` binary(16) DEFAULT NULL,
  `clinical_encounter_public_id` binary(16) DEFAULT NULL,
  `economic_activity_code` varchar(20) NOT NULL,
  `tax_product_code` varchar(30) NOT NULL,
  `internal_product_code` varchar(30) DEFAULT NULL,
  `description` varchar(500) NOT NULL,
  `quantity` decimal(12,4) NOT NULL DEFAULT '1.0000',
  `unit_of_measure` smallint unsigned NOT NULL DEFAULT '58',
  `unit_price` decimal(14,2) NOT NULL,
  `discount_amount` decimal(14,2) NOT NULL DEFAULT '0.00',
  `subtotal` decimal(14,2) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_invoice_item_invoice_id_item_number` (`invoice_id`,`item_number`),
  KEY `ix_invoice_item_service_offering_public_id` (`service_offering_public_id`),
  KEY `ix_invoice_item_appointment_public_id` (`appointment_public_id`),
  KEY `ix_invoice_item_clinical_encounter_public_id` (`clinical_encounter_public_id`),
  CONSTRAINT `fk_invoice_item_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `invoice` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_invoice_item_amounts` CHECK (((`unit_price` >= 0) and (`discount_amount` >= 0) and (`subtotal` >= 0))),
  CONSTRAINT `ck_invoice_item_quantity` CHECK ((`quantity` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `point_of_sale`
--

DROP TABLE IF EXISTS `point_of_sale`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `point_of_sale` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `organization_public_id` binary(16) NOT NULL,
  `branch_public_id` binary(16) NOT NULL,
  `tax_branch_code` smallint unsigned NOT NULL,
  `point_of_sale_code` smallint unsigned NOT NULL,
  `name` varchar(120) NOT NULL,
  `type` enum('COMMISSION_AGENT','COLLECTION_DESK','MOBILE','YPFB','ATM','OTHER') NOT NULL DEFAULT 'COLLECTION_DESK',
  `status` enum('ACTIVE','CLOSED') NOT NULL DEFAULT 'ACTIVE',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_point_of_sale_organization_public_id_tax_branch_c_key` (`organization_public_id`,`tax_branch_code`,`point_of_sale_code`),
  KEY `ix_point_of_sale_branch_public_id_status` (`branch_public_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping routines for database 'billing_db'
--

--
-- Current Database: `notifications_db`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `notifications_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `notifications_db`;

--
-- Table structure for table `conversation_window`
--

DROP TABLE IF EXISTS `conversation_window`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `conversation_window` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `organization_id` char(36) NOT NULL,
  `patient_id` char(36) NOT NULL,
  `channel` varchar(20) NOT NULL DEFAULT 'WHATSAPP',
  `destination` varchar(20) NOT NULL,
  `opened_at` datetime(6) NOT NULL,
  `last_patient_message_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) GENERATED ALWAYS AS ((`last_patient_message_at` + interval 24 hour)) STORED,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_conversation_window` (`organization_id`,`patient_id`,`channel`),
  KEY `ix_conversation_expiration` (`expires_at`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `flyway_schema_history`
--

DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `incoming_message`
--

DROP TABLE IF EXISTS `incoming_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `incoming_message` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `public_id` char(36) NOT NULL,
  `organization_id` char(36) DEFAULT NULL,
  `channel` varchar(20) NOT NULL DEFAULT 'WHATSAPP',
  `source_phone` varchar(20) NOT NULL,
  `patient_id` char(36) DEFAULT NULL,
  `provider_message_id` varchar(160) DEFAULT NULL,
  `notification_id` bigint unsigned DEFAULT NULL,
  `message_text` text,
  `intent` varchar(24) NOT NULL DEFAULT 'UNRECOGNIZED',
  `processed` tinyint(1) NOT NULL DEFAULT '0',
  `performed_action` varchar(120) DEFAULT NULL,
  `received_at` datetime(6) NOT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_incoming_public_id` (`public_id`),
  UNIQUE KEY `uq_incoming_provider_id` (`provider_message_id`),
  KEY `ix_incoming_pending` (`processed`,`received_at`),
  KEY `fk_incoming_notification` (`notification_id`),
  CONSTRAINT `fk_incoming_notification` FOREIGN KEY (`notification_id`) REFERENCES `notification` (`id`),
  CONSTRAINT `ck_incoming_intent` CHECK ((`intent` in (_utf8mb4'CONFIRM',_utf8mb4'CANCEL',_utf8mb4'RESCHEDULE',_utf8mb4'QUESTION',_utf8mb4'MARKETING_OPT_OUT',_utf8mb4'OTHER',_utf8mb4'UNRECOGNIZED')))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `message_template`
--

DROP TABLE IF EXISTS `message_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `message_template` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `public_id` char(36) NOT NULL,
  `organization_id` char(36) DEFAULT NULL,
  `code` varchar(60) NOT NULL,
  `channel` varchar(20) NOT NULL DEFAULT 'WHATSAPP',
  `category` varchar(24) NOT NULL DEFAULT 'UTILITY',
  `provider_template_name` varchar(120) DEFAULT NULL,
  `language_code` varchar(10) NOT NULL DEFAULT 'es',
  `subject` varchar(200) DEFAULT NULL,
  `body` text NOT NULL,
  `variables` json DEFAULT NULL,
  `approval_status` varchar(24) NOT NULL DEFAULT 'DRAFT',
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_message_template_public_id` (`public_id`),
  UNIQUE KEY `uq_message_template_scope` (`organization_id`,`code`,`channel`,`language_code`),
  CONSTRAINT `ck_template_approval` CHECK ((`approval_status` in (_utf8mb4'DRAFT',_utf8mb4'IN_REVIEW',_utf8mb4'APPROVED',_utf8mb4'REJECTED',_utf8mb4'PAUSED'))),
  CONSTRAINT `ck_template_category` CHECK ((`category` in (_utf8mb4'UTILITY',_utf8mb4'MARKETING',_utf8mb4'AUTHENTICATION',_utf8mb4'SERVICE'))),
  CONSTRAINT `ck_template_channel` CHECK ((`channel` in (_utf8mb4'WHATSAPP',_utf8mb4'SMS',_utf8mb4'EMAIL',_utf8mb4'PUSH')))
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notification`
--

DROP TABLE IF EXISTS `notification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `public_id` char(36) NOT NULL,
  `origin_event_id` char(36) DEFAULT NULL,
  `idempotency_key` varchar(160) NOT NULL,
  `organization_id` char(36) NOT NULL,
  `patient_id` char(36) DEFAULT NULL,
  `destination_user_id` char(36) DEFAULT NULL,
  `channel` varchar(20) NOT NULL DEFAULT 'WHATSAPP',
  `category` varchar(24) NOT NULL DEFAULT 'UTILITY',
  `template_id` bigint unsigned DEFAULT NULL,
  `rule_id` bigint unsigned DEFAULT NULL,
  `event_type` varchar(40) NOT NULL,
  `entity_type` varchar(32) NOT NULL DEFAULT 'NONE',
  `entity_id` char(36) DEFAULT NULL,
  `destination` varchar(160) NOT NULL,
  `variables` json DEFAULT NULL,
  `rendered_body` text,
  `status` varchar(24) NOT NULL DEFAULT 'QUEUED',
  `scheduled_for` datetime(6) NOT NULL,
  `attempts` tinyint unsigned NOT NULL DEFAULT '0',
  `next_attempt_at` datetime(6) DEFAULT NULL,
  `sent_at` datetime(6) DEFAULT NULL,
  `delivered_at` datetime(6) DEFAULT NULL,
  `read_at` datetime(6) DEFAULT NULL,
  `provider_message_id` varchar(160) DEFAULT NULL,
  `error_code` varchar(40) DEFAULT NULL,
  `error_detail` varchar(500) DEFAULT NULL,
  `estimated_cost_usd` decimal(10,4) DEFAULT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_notification_public_id` (`public_id`),
  UNIQUE KEY `uq_notification_idempotency` (`idempotency_key`),
  UNIQUE KEY `uq_notification_provider_id` (`provider_message_id`),
  KEY `ix_notification_queue` (`status`,`scheduled_for`),
  KEY `ix_notification_patient` (`patient_id`,`created_at`),
  KEY `ix_notification_org` (`organization_id`,`event_type`,`created_at`),
  KEY `fk_notification_template` (`template_id`),
  KEY `fk_notification_rule` (`rule_id`),
  KEY `ix_notification_origin_event` (`origin_event_id`),
  CONSTRAINT `fk_notification_rule` FOREIGN KEY (`rule_id`) REFERENCES `notification_rule` (`id`),
  CONSTRAINT `fk_notification_template` FOREIGN KEY (`template_id`) REFERENCES `message_template` (`id`),
  CONSTRAINT `ck_notification_category` CHECK ((`category` in (_utf8mb4'UTILITY',_utf8mb4'MARKETING',_utf8mb4'AUTHENTICATION',_utf8mb4'SERVICE'))),
  CONSTRAINT `ck_notification_channel` CHECK ((`channel` in (_utf8mb4'WHATSAPP',_utf8mb4'SMS',_utf8mb4'EMAIL',_utf8mb4'PUSH'))),
  CONSTRAINT `ck_notification_recipient` CHECK (((`patient_id` is not null) or (`destination_user_id` is not null))),
  CONSTRAINT `ck_notification_status` CHECK ((`status` in (_utf8mb4'SCHEDULED',_utf8mb4'QUEUED',_utf8mb4'SENT',_utf8mb4'DELIVERED',_utf8mb4'READ',_utf8mb4'FAILED',_utf8mb4'CANCELLED',_utf8mb4'REJECTED_OPT_OUT')))
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notification_event`
--

DROP TABLE IF EXISTS `notification_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification_event` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `notification_id` bigint unsigned NOT NULL,
  `status` varchar(24) NOT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `error_code` varchar(40) DEFAULT NULL,
  `webhook_payload` json DEFAULT NULL,
  `received_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_notification_event` (`notification_id`,`status`,`occurred_at`),
  CONSTRAINT `fk_event_notification` FOREIGN KEY (`notification_id`) REFERENCES `notification` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_event_status` CHECK ((`status` in (_utf8mb4'ACCEPTED',_utf8mb4'SENT',_utf8mb4'DELIVERED',_utf8mb4'READ',_utf8mb4'FAILED',_utf8mb4'DELETED')))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notification_preference_reference`
--

DROP TABLE IF EXISTS `notification_preference_reference`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification_preference_reference` (
  `patient_id` char(36) NOT NULL,
  `channel` varchar(20) NOT NULL,
  `category` varchar(24) NOT NULL,
  `opted_in` tinyint(1) NOT NULL DEFAULT '0',
  `opted_in_at` datetime(6) DEFAULT NULL,
  `opted_out_at` datetime(6) DEFAULT NULL,
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`patient_id`,`channel`,`category`),
  CONSTRAINT `ck_preference_category` CHECK ((`category` in (_utf8mb4'UTILITY',_utf8mb4'MARKETING'))),
  CONSTRAINT `ck_preference_channel` CHECK ((`channel` in (_utf8mb4'WHATSAPP',_utf8mb4'SMS',_utf8mb4'EMAIL',_utf8mb4'PUSH'))),
  CONSTRAINT `ck_preference_opt_in` CHECK (((`opted_in` = false) or (`opted_in_at` is not null)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notification_rule`
--

DROP TABLE IF EXISTS `notification_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification_rule` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `public_id` char(36) NOT NULL,
  `organization_id` char(36) NOT NULL,
  `event_type` varchar(40) NOT NULL,
  `channel` varchar(20) NOT NULL DEFAULT 'WHATSAPP',
  `template_id` bigint unsigned NOT NULL,
  `offset_minutes` int NOT NULL DEFAULT '0',
  `sending_window_from` time DEFAULT NULL,
  `sending_window_to` time DEFAULT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_notification_rule_public_id` (`public_id`),
  UNIQUE KEY `uq_notification_rule` (`organization_id`,`event_type`,`channel`,`offset_minutes`),
  KEY `fk_rule_template` (`template_id`),
  CONSTRAINT `fk_rule_template` FOREIGN KEY (`template_id`) REFERENCES `message_template` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `organization_reference`
--

DROP TABLE IF EXISTS `organization_reference`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `organization_reference` (
  `organization_id` char(36) NOT NULL,
  `legal_name` varchar(180) NOT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`organization_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `patient_reference`
--

DROP TABLE IF EXISTS `patient_reference`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `patient_reference` (
  `patient_id` char(36) NOT NULL,
  `organization_id` char(36) NOT NULL,
  `display_name` varchar(240) NOT NULL,
  `whatsapp_number` varchar(20) DEFAULT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`patient_id`),
  KEY `ix_patient_reference_org` (`organization_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping routines for database 'notifications_db'
--
