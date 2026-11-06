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
- [x] Built the React/TypeScript storefront and staff UI shell with product/category, warehouse, dashboard, and stock screens.
- [x] Built the Spring Boot API foundation with validation, shared errors, OpenAPI, customer registration/login, access tokens, rotating refresh sessions, logout, and owner bootstrap.
- [x] Added Flyway schema for identities, catalog, warehouses, stock balances, movement ledger, and idempotency reservations.
- [x] Implemented transaction-locked stock adjustments, negative-stock protection, movement recording, and database-reserved idempotent retry/reuse handling.
- [x] Added exact-Origin checks for refresh/logout with an HttpOnly, SameSite=Strict refresh cookie.
- [x] Frontend production build passes; 2 frontend UI tests pass; backend compiles and 6 unit tests pass (4 inventory, 2 origin guard).
- [x] Added database setup notes, environment loader, README, and Obsidian-ready project notes.

## Pending
- [ ] Provision MySQL credentials and verify the Flyway migration, Hibernate validation, and API startup.
- [ ] Add permission-level RBAC, business/warehouse scopes, staff account management, persistent security audit events, login abuse limits, password recovery, and integration/security review of CSRF controls.
- [ ] Implement suppliers and purchasing, customer accounts/cart/checkout/orders, POS/sales, payments, returns/refunds, reporting, notifications, and support.
- [ ] Expand frontend coverage and add backend integration, authorization, security, and database concurrency tests.
- [ ] Sync notes to a registered Obsidian vault if its folder is specified.
- [ ] Configure GitHub destination/privacy before publishing.

See [[../../implementation-plan]] for the roadmap.
