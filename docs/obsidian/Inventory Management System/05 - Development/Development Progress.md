---
title: Development Progress
tags: [ims, progress]
status: active
---

# Development Progress

## Completed / verified
- [x] Inspected the empty project workspace and local runtime versions.
- [x] Confirmed AMPPS MySQL 8.0.45 is installed and port 3306 accepts connections; phpMyAdmin uses cookie authentication.
- [x] Confirmed unauthenticated local root access is unavailable and left existing databases unchanged.
- [x] Built the React/TypeScript storefront and staff UI shell with product create/edit/archive, category, warehouse, supplier, purchase-order, dashboard, and stock screens.
- [x] Built the Spring Boot API foundation with validation, shared errors, OpenAPI, customer registration/login, access tokens, rotating refresh sessions, logout, and owner bootstrap.
- [x] Added Flyway schema for identities, catalog, warehouses, stock balances, movement ledger, and idempotency reservations; V2 adds supplier and purchase-receiving tables.
- [x] Implemented transaction-locked stock adjustments, negative-stock protection, movement recording, and database-reserved idempotent retry/reuse handling.
- [x] Added exact-Origin checks for refresh/logout with an HttpOnly, SameSite=Strict refresh cookie.
- [x] Implemented supplier profiles and purchase-order workflow: submit, approve/reject, mark sent, partial receiving, stock ledger writes, and safe request replay.
- [x] Frontend production build passes; 6 UI tests pass; backend compiles and 14 tests pass (6 H2 app/security smoke, 2 origin guard, 4 inventory, 2 procurement integration).
- [x] Added database setup notes, environment loader, README, and Obsidian-ready project notes.

## Pending
- [ ] Provision MySQL credentials and verify the Flyway migration, Hibernate validation, and API startup. Current app smoke tests use H2 and disable Flyway.
- [ ] Add permission-level RBAC, business/warehouse scopes, staff account management, persistent security audit events, login abuse limits, password recovery, and integration/security review of CSRF controls.
- [ ] Implement supplier returns, customer accounts/cart/checkout/orders, POS/sales, payments, returns/refunds, reporting, notifications, and support.
- [ ] Expand authorization, security, MySQL migration, and multi-connection concurrency coverage.
- [ ] Configure GitHub destination/privacy before publishing.

See [[../../implementation-plan]] for the roadmap.
