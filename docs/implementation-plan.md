---
title: Repository Assessment and Implementation Plan
date: 2026-11-15
tags: [ims, planning, assessment]
---

# Repository assessment

| Area | Verified state |
| --- | --- |
| Workspace | Full-stack IMS repository (`frontend/`, `backend/`, `database/`, `scripts/`, `docs/`). |
| Frontend | Node.js 24.13.0 and npm 11.12.1. React + TypeScript + Vite SPA covering storefront, customer portal, and staff operations workspace. |
| Backend | Temurin Java 21.0.11, Spring Boot 3.5, Spring Security, JPA/Hibernate, Flyway migrations `V1` through `V8`. |
| Database | MySQL 8 schema managed by Flyway (`V1`–`V8`) with least-privilege user separation (`ims_migrator` vs `ims_app`) in `database/setup-users.sql`. |
| Obsidian | Canonical Markdown maintained under `docs/obsidian/Inventory Management System/`. `.obsidian/` settings and generated Welcome note are excluded from Git. |

## Decisions

- Preserve the Java 21 / Spring Boot 3 architecture with the included Maven Wrapper (`mvnw.cmd`).
- Use Flyway for all schema migrations (`V1` through `V8`) and keep Hibernate `ddl-auto: validate`.
- Use fixed-precision decimals (`DECIMAL(19,4)` for money, `DECIMAL(18,3)` for quantities) across database, entities, and DTOs.
- Treat `inventory_movements` as the immutable audit ledger and `inventory_balances` as transactionally locked projections reconciled by `ReportService`.
- Separate database migration privileges (`ims_migrator`) from runtime application CRUD privileges (`ims_app`).

## Current implementation status (100% of scoped flows)

- **Catalog & Inventory**: Categories, products, warehouses, locked balances, idempotent stock adjustments, **between-warehouse stock transfers** (`TRANSFER_OUT` / `TRANSFER_IN` in a single deadlock-safe transaction), and movement history.
- **Procurement & Supplier Payables**: Supplier profiles, purchase order lifecycle (draft, approval, rejection, ordered, partial/full goods receipt, audited cancellation), supplier return workflow (request, approval, warehouse dispatch, supplier credit notes), and **supplier payables & credit reconciliation** (`OPEN`, `PARTIALLY_PAID`, `SETTLED`) with idempotent payment recording.
- **Customer Commerce & Store Pickup**: Customer registration/login, saved addresses, persistent cart, server-priced checkout supporting both **Home Delivery (`DELIVERY`)** and **Store Pickup (`STORE_PICKUP`)**, stock reservation, order history/timeline, **`READY_FOR_PICKUP` fulfillment transition**, and auditable manual payment verification.
- **Sales, POS & Returns**: Cashier POS with independent receipts, cash change, external terminal reference recording, receipt printing, and customer/POS return requests, physical inspection dispositions (`restock`, `damaged`, `scrap`), and idempotent refund records.
- **Identity, Scopes, Lockout & Audit**: JWT access tokens, rotating `HttpOnly` + `SameSite=Strict` refresh sessions with `Origin` enforcement, **staff user/role and warehouse scope administration**, **5-attempt / 15-minute brute-force login lockout**, **password change & token-based password reset**, HTTP security headers (`CSP`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy`), and **persistent security audit events** (`security_audit_events`).
- **Operations, Reports, Support & Notifications**: Operational overview report with **ledger-vs-balance reconciliation**, formula-safe CSV exports (`inventory`, `sales`, `suppliers`, `audit`), customer/staff **support tickets** (`OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`), and **user notifications**.
- **External boundaries**: External online payment gateways require merchant credentials before activation; no fake external payment success is simulated.
