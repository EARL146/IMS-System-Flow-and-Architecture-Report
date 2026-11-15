-- =============================================================================
-- IMS Production Database Security Setup (MySQL 8.4+)
-- Separates Flyway DDL migration privileges from runtime CRUD privileges.
-- Never run the Spring Boot API under root in production.
-- =============================================================================

CREATE DATABASE IF NOT EXISTS inventory_management_system
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- 1. Schema Migration User (used only during deployment / Flyway migrate)
CREATE USER IF NOT EXISTS 'ims_migrator'@'%' IDENTIFIED BY 'CHANGE_ME_STRONG_MIGRATOR_SECRET';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, DROP
  ON inventory_management_system.* TO 'ims_migrator'@'%';

-- 2. Least-Privilege Runtime Application User (used by Spring Boot API at runtime)
-- Strictly limited to DML (SELECT, INSERT, UPDATE, DELETE) — no DROP, ALTER, or TRUNCATE.
CREATE USER IF NOT EXISTS 'ims_app'@'%' IDENTIFIED BY 'CHANGE_ME_STRONG_RUNTIME_SECRET';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'ims_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE
  ON inventory_management_system.* TO 'ims_app'@'%';

FLUSH PRIVILEGES;
