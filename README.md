# Inventory Management System

A staged full-stack inventory and retail management platform. The implementation is being built around a React + TypeScript customer/admin frontend, a Java 21 Spring Boot REST API, MySQL 8, and Flyway migrations.

## Current status

The initial workspace was empty and had no Git history. The first delivery is an implementation foundation, not a claim that every requested business module is complete. Current scope and verified status are tracked in [the implementation plan](docs/implementation-plan.md) and Obsidian-ready notes under `docs/obsidian/Inventory Management System/`.

## Planned architecture

- `frontend/`: React, TypeScript, Vite, React Router, a centralized API client, and reusable UI.
- `backend/`: Java 21, Spring Boot 3, Spring Security, JPA, Bean Validation, and REST DTOs.
- `database/`: safe database creation script and MySQL setup notes. Versioned schema changes live in `backend/src/main/resources/db/migration/` and are applied by Flyway.
- `docs/`: architecture, security, API, implementation, and Obsidian-compatible notes.

## Local prerequisites

- Node.js 20.19+ (inspected machine: Node 24.13.0) and npm 11.12.1.
- Java 21 (inspected machine: Temurin 21.0.11).
- MySQL 8 (AMPPS has MySQL 8.0.45 installed on this machine).
- Maven Wrapper is included so a global Maven installation is not required.

AMPPS phpMyAdmin is configured for cookie authentication. The MySQL listener responds on `127.0.0.1:3306`, but a read-only version query using an unauthenticated local `root` account was rejected. No database, account, or existing data has been changed. Configure a dedicated least-privilege application account and database before migrations.

## Setup

1. Copy `.env.example` to `.env`, set local database credentials, and generate a random JWT secret. In PowerShell, `[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))` generates the required 32 random bytes.
2. In phpMyAdmin, run `database/create-database.sql` and create a dedicated `ims_app` user scoped only to `inventory_management_system`. Follow [the database setup notes](database/README.md). Spring connects directly to MySQL; phpMyAdmin is only an administration UI.
3. In a backend PowerShell terminal at the project root, run `. .\scripts\load-env.ps1`, then `Set-Location backend` and `./mvnw.cmd spring-boot:run`.
4. In a second PowerShell terminal, load the same environment file, enter `frontend/`, then run `npm install` and `npm run dev`.

Keep `.env` out of Git and do not use production credentials for local development.

## API and database

The API uses `/api/v1`. Flyway owns schema changes; Hibernate schema auto-update is disabled. Inventory changes are represented as ledger movements alongside current balance rows. Implemented routes and limitations are tracked in the project notes.

## Security

The backend is authoritative for authentication, permissions, product prices, and stock availability. Monetary values use fixed-precision decimals. Inventory updates are transactional, prevent negative balances, and record a movement with a database-reserved idempotency key. Refresh/logout requests require an allowed `Origin`; the refresh cookie is `HttpOnly` and `SameSite=Strict`. See the security notes under `docs/obsidian/Inventory Management System/04 - Security/`.

## Verification

Verified so far: `npm run build` passes; `npm test` passes 2 frontend UI tests; `backend/mvnw.cmd test` passes 6 backend unit tests (4 inventory and 2 origin-guard tests). Database integration checks and a MySQL migration run still require a provisioned database account.

## Obsidian

Obsidian-formatted Markdown notes are maintained in `docs/obsidian/Inventory Management System/`. To use them, choose **Open folder as vault** in Obsidian and select that folder. They have not been copied into an existing vault.
