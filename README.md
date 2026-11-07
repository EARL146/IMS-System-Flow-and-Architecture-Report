# Inventory Management System

A staged full-stack inventory and retail management platform. The implementation is being built around a React + TypeScript customer/admin frontend, a Java 21 Spring Boot REST API, MySQL 8, and Flyway migrations.

## Current status

Implemented vertical slices include product/category/warehouse administration, stock adjustments and ledger history, suppliers and purchase receiving, a customer storefront with registration/login, saved addresses, persistent cart, server-priced checkout, stock reservations, order history/status tracking, staff fulfillment and auditable manual payment verification, plus a cashier POS with independent receipts, cash change, external-terminal references, locked stock deduction and receipt printing. Returns/refunds, online payment-provider integration, business scoping, and several other requested modules remain in progress. See [the implementation plan](docs/implementation-plan.md) and Obsidian notes for the verified scope.

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

The API uses `/api/v1`. Flyway owns schema changes; Hibernate schema auto-update is disabled. Inventory changes are represented as ledger movements alongside current balance rows, including separate reserved-stock deltas. Customer checkout refreshes prices on the server, checks available inventory in a transaction, reserves stock, snapshots the delivery address, and records an idempotency key. Staff fulfillment consumes reserved stock once at delivery. POS uses server-side prices and a transactionally locked stock deduction; cash tender/change and cashier-entered external terminal references are recorded without handling card details. Staff can also record a full manual transfer verification or cash-on-delivery collection; no payment provider is integrated. `IMS_CHECKOUT_TAX_RATE`, `IMS_CHECKOUT_SHIPPING_AMOUNT`, and `IMS_CHECKOUT_CURRENCY_CODE` configure checkout/POS tax and currency; tax and shipping default to zero until configured. See the API and database notes for route and migration details.

## Security

The backend is authoritative for authentication, permissions, product prices, and stock availability. Monetary values use fixed-precision decimals. Inventory updates are transactional, prevent negative balances, and record a movement with a database-reserved idempotency key. Refresh/logout requests require an allowed `Origin`; the refresh cookie is `HttpOnly` and `SameSite=Strict`. See the security notes under `docs/obsidian/Inventory Management System/04 - Security/`.

## Verification

Verified: `npm run build` passes; Vitest passes 14 frontend UI tests; `backend/mvnw.cmd -B test` passes all 30 backend tests (15 application/security smoke, 2 origin-guard, 5 customer commerce, 4 inventory, 2 POS, and 2 purchase-receiving workflow tests). Oxlint exits successfully with five existing React warnings. Production MySQL migration and integration checks still require a provisioned database account.

## Obsidian

Obsidian-formatted source notes are maintained in `docs/obsidian/Inventory Management System/`. The local Obsidian vault at `IMS/` contains the project notes under `IMS/Inventory Management System/`; its `.obsidian/` settings and generated Welcome note stay local and are excluded from Git. See [the Obsidian quick start](docs/obsidian/Inventory%20Management%20System/00%20-%20Project%20Overview/Obsidian%20Quick%20Start.md).
