---
title: Repository Assessment and Implementation Plan
date: 2026-11-06
tags: [ims, planning, assessment]
---

# Repository assessment

| Area | Verified state |
| --- | --- |
| Workspace | `C:/Users/shizu/OneDrive/Desktop/IMS` was empty; no `.git` or project files existed. |
| Frontend | Node.js 24.13.0 and npm 11.12.1 are installed. |
| Backend | Temurin Java 21.0.11 is installed. Maven was not on PATH. |
| Database | AMPPS includes MySQL Community Server 8.0.45; its configured loopback listener is port 3306 and its data directory is `D:/Ampps/mysql/data/`. |
| Database authentication | A read-only version query using local `root` without a password was rejected. No credentials were read or changed. |
| phpMyAdmin | Present under `D:/Ampps/phpmyadmin`; cookie authentication and host `127.0.0.1` are configured. AMPPS uses Apache 2.4.66 and PHP 8.2.30. |
| Obsidian | IMS notes are created as Obsidian-compatible Markdown under `docs/obsidian/Inventory Management System/`; open that folder as a vault when needed. No registered-vault path is required. |
| Existing app/security/database | None found in the initially empty IMS workspace. |

## Decisions

- Preserve the requested Java 21 / Spring Boot 3 architecture; use a Maven Wrapper because Maven is not globally installed.
- Use Flyway for schema changes and avoid editing existing AMPPS databases without a dedicated name and credentials.
- Use fixed-precision decimals for all financial values.
- Treat inventory movements as the audit source of truth and balance rows as transactionally maintained projections.
- Build vertical slices in dependency order and label unimplemented features as pending.
- Keep the Obsidian notes in the project so they can be opened directly as their own vault.

## Current implementation status

- Implemented code: responsive storefront/catalog, staff app shell, product create/edit/archive and category management, warehouse management, supplier profiles, purchase order drafts/approval/send/receive with partial receipts, dashboard, stock adjustments and ledger history, customer registration/login, JWT access tokens, rotating refresh sessions, owner bootstrap, DTO validation, common errors, and OpenAPI.
- Inventory integrity includes locked balance rows, non-negative checks, one database transaction for balance/movement writes, and a unique-key reservation that makes concurrent retries replay the original adjustment or reject a changed payload.
- Cookie refresh/logout requests require an exact allowed Origin; refresh cookies are HttpOnly and SameSite=Strict.
- Procurement includes supplier CRUD/archive, role-scoped reference reads, purchase-order transitions, over-receipt checks, transaction-locked inventory posting, receipt records, and idempotent retries.
- Verified: Vite production build succeeds with 6 UI tests passing; Maven backend build succeeds with 14 tests passing (6 H2 app/security smoke, 2 origin guard, 4 inventory, 2 purchase workflow).
- Not verified: MySQL database creation, Flyway migration, Hibernate schema validation against MySQL, and phpMyAdmin table inspection. Existing local credentials were unavailable. The H2 profile disables Flyway and does not replace those checks.
- Not implemented: business/tenant scoping, permission-level administration, user administration, login abuse controls, security audit persistence, supplier returns, customer checkout/orders, POS, payments, returns/refunds, reports, support, and notifications. Origin-guard behavior still needs integration testing and security review.
- Obsidian-ready notes are in `docs/obsidian/Inventory Management System/` and can be opened directly as a vault. No GitHub remote is configured.

## Prioritized roadmap

1. Foundation: repository metadata, design tokens, build wrapper, environment template, API error model, migration baseline, and architecture/security notes.
2. Identity and access: registration/login, short-lived access tokens, refresh rotation, server-side RBAC, bootstrap owner, and audit.
3. Catalog and inventory: categories, products, warehouses, locked balances, idempotent adjustments, movement history, and safe public catalog reads.
4. Procurement: supplier profiles, purchase orders, approvals, and partial goods receiving are implemented; supplier returns remain.
5. Customer experience: accounts/addresses, catalog search, cart, server-priced checkout, orders, payment adapter, status history, and returns.
6. Sales operations: POS, concurrent stock controls, receipts, returns, inspection, and refunds.
7. Reporting and operations: support, notifications, exports, audit search, deployment, backup, and recovery.
8. Verification: unit/integration/security/concurrency tests, MySQL migrations, and security review.

## Inputs still needed

- A dedicated MySQL database and application user must be provisioned; unauthenticated local root access is unavailable.
- Payment provider/merchant details are needed before online payments can be activated. No payment success will be simulated.
