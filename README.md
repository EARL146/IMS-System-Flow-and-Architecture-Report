# Inventory Management System

A full-stack inventory, procurement, POS, and customer commerce platform built with a React + TypeScript customer/staff frontend, a Java 21 Spring Boot 3 REST API, MySQL 8, and Flyway migrations (`V1` through `V8`).

## Current status

All core and cross-cutting workflows mapped in the Figma architecture (`Website Flow`, `Gaps & Remaining Flows`, and `System Architecture`) are implemented:

- **Catalog & Warehouses**: Product/category/warehouse administration, public catalog browsing, and multi-warehouse availability.
- **Inventory & Stock Ledger**: Locked balance rows, idempotent stock adjustments, **between-warehouse stock transfers** (`POST /api/v1/admin/inventory/transfers` with paired `TRANSFER_OUT` / `TRANSFER_IN` movements and deadlock-safe ascending lock ordering), and full movement history.
- **Procurement, Supplier Returns & Accounts Payable**: Supplier management, purchase order drafts/approvals/partial goods receipts, audited PO cancellation, supplier return approvals and stock dispatch, manual supplier credit notes, and **supplier payables & credit reconciliation** (`/api/v1/admin/supplier-payables`) with idempotent supplier settlement payments.
- **Customer Commerce & Store Pickup**: Customer registration/login, saved addresses, persistent cart, server-priced checkout supporting both **Home Delivery (`DELIVERY`)** and **Store Pickup (`STORE_PICKUP`)**, stock reservation, order history/status tracking, **`READY_FOR_PICKUP` fulfillment transition**, and auditable manual payment verification.
- **Sales, POS & Returns**: Cashier POS with server-side pricing, cash tender/change, external terminal reference recording, receipt printing, and customer/POS return requests, physical inspection dispositions (`restock`, `damaged`, `scrap`), and idempotent refund records.
- **Identity, Warehouse Scopes, Lockout & Security Audit**: Role-based access control (`OWNER`, `ADMIN`, `INVENTORY_MANAGER`, `PURCHASING_OFFICER`, `WAREHOUSE_STAFF`, `ACCOUNTANT`, `CASHIER`, `CUSTOMER_SUPPORT`, `CUSTOMER`), **staff user & warehouse scope administration** (`/api/v1/admin/users`), **brute-force login lockout** (5 failed attempts -> 15-minute lock with `423 Locked`), **password change & token-based password reset** (`/api/v1/auth/change-password`, `/api/v1/auth/password-reset/**`), and **persistent security audit events** (`/api/v1/admin/audit-events`).
- **Operations, Reports, CSV Exports, Support & Notifications**: Operational overview and **ledger-vs-balance reconciliation** (`/api/v1/admin/reports/overview`), formula-safe CSV exports (`/api/v1/admin/reports/export/{inventory|sales|suppliers|audit}`), customer & staff **support tickets** (`/api/v1/customer/support-tickets`, `/api/v1/admin/support-tickets`), and in-app **user notifications** (`/api/v1/notifications`).

## Architecture

- [frontend/](file:///c:/Users/shizu/OneDrive/Desktop/IMS/frontend): React, TypeScript, Vite, React Router, centralized Axios API client, and responsive customer/admin workspaces.
- [backend/](file:///c:/Users/shizu/OneDrive/Desktop/IMS/backend): Java 21, Spring Boot 3, Spring Security, JPA/Hibernate (`ddl-auto: validate`), Bean Validation, Flyway migrations (`V1`–`V8`), and OpenAPI (`/docs`).
- [database/](file:///c:/Users/shizu/OneDrive/Desktop/IMS/database): Safe database creation script ([create-database.sql](file:///c:/Users/shizu/OneDrive/Desktop/IMS/database/create-database.sql)) and least-privilege user separation script ([setup-users.sql](file:///c:/Users/shizu/OneDrive/Desktop/IMS/database/setup-users.sql)).
- [scripts/](file:///c:/Users/shizu/OneDrive/Desktop/IMS/scripts): Environment loader ([load-env.ps1](file:///c:/Users/shizu/OneDrive/Desktop/IMS/scripts/load-env.ps1)) and SHA-256 verified MySQL backup/restore scripts ([backup-db.ps1](file:///c:/Users/shizu/OneDrive/Desktop/IMS/scripts/backup-db.ps1), [restore-db.ps1](file:///c:/Users/shizu/OneDrive/Desktop/IMS/scripts/restore-db.ps1)).
- [docs/](file:///c:/Users/shizu/OneDrive/Desktop/IMS/docs): Architecture, security, API, implementation plan, and Obsidian notes.

## Setup

1. Copy `.env.example` to `.env`, set local database credentials, and generate a random 32-byte Base64 JWT secret:
   ```powershell
   [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
   ```
2. Provision the MySQL database and least-privilege users using [database/create-database.sql](file:///c:/Users/shizu/OneDrive/Desktop/IMS/database/create-database.sql) and [database/setup-users.sql](file:///c:/Users/shizu/OneDrive/Desktop/IMS/database/setup-users.sql) (see [database/README.md](file:///c:/Users/shizu/OneDrive/Desktop/IMS/database/README.md)).
3. In a backend PowerShell terminal at the project root, run `. .\scripts\load-env.ps1`, then `Set-Location backend` and `./mvnw.cmd spring-boot:run`.
4. In a second PowerShell terminal, enter `frontend/`, then run `npm install` and `npm run dev`.

Keep `.env` out of Git and never commit production credentials.

## Security Hardening (API & Database)

- **Server-Side Authority**: Prices, taxes, discounts, stock availability, role permissions, and warehouse object scopes are enforced on the server.
- **Database Least Privilege**: [database/setup-users.sql](file:///c:/Users/shizu/OneDrive/Desktop/IMS/database/setup-users.sql) separates `ims_migrator` (DDL privileges for Flyway) from `ims_app` (`SELECT, INSERT, UPDATE, DELETE` only at runtime).
- **Concurrency & Idempotency**: Pessimistic row locks (`SELECT ... FOR UPDATE`), `@Version` optimistic locking, deterministic lock ordering on multi-warehouse transfers, and SHA-256 request-hash idempotency keys protect all stock-mutating and financial endpoints.
- **Session & Header Hardening**: Short-lived JWT access tokens, rotating `HttpOnly` + `SameSite=Strict` refresh cookies guarded by strict `Origin` checks, account lockout after 5 failed login attempts, and HTTP security headers (`Content-Security-Policy`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy`).
