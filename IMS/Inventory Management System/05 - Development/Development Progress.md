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
- [x] Implemented customer saved addresses, persistent cart, customer checkout, current-price calculation, inventory reservation/release, order history/detail, pending-order cancellation, and honest COD/manual-transfer payment states.
- [x] Implemented role-scoped staff order queues, audited order-status history, state-checked shipping/delivery, once-only stock consumption at delivery, and full-amount manual payment verification/collection records.
- [x] Added customer order-progress timeline and staff order/payment management screen.
- [x] Added cashier POS with independent sales receipts, server-priced checkout, cash change, external-terminal approval references, customer lookup, receipt printing, retry-safe requests, and locked stock deductions in the inventory ledger.
- [x] Implemented customer-order and POS return requests, staff approval/rejection, item inspection with restock/damaged/scrap disposition, idempotent manual refund records, and inventory/payment/order ledger updates.
- [x] Added supplier returns against received purchase lines, active quantity reservations, approval/rejection, stock-safe dispatch movements, damaged-stock handling, and manual supplier credit-note records.
- [x] Purchase-order cancellation now requires an audited reason, can release unreceived quantities after supplier send, and records the released commitment while retaining received stock and receipt history.
- [x] Added customer return history/request UI, staff return queue/actions, POS return entry from sale history, V6 schema, and the [[../03 - API Documentation/Returns and Refunds]] operational note.
- [x] Customer routes require the CUSTOMER role, are scoped to the authenticated user, and return 403 for denied staff roles.
- [x] Backend compiles and all 35 backend tests pass, including customer/supplier return approval, inventory disposition/dispatch, manual credit/refund records, idempotency, and purchase cancellation coverage.
- [x] Frontend production build passes and all 18 UI tests pass, including supplier return creation and purchase cancellation screens.
- [x] Added database setup notes, environment loader, README, and Obsidian-ready project notes.

## Pending
- [ ] Provision MySQL credentials and verify the Flyway migration, Hibernate validation, and API startup. Current app smoke tests use H2 and disable Flyway.
- [ ] Add permission-level RBAC, business/warehouse scopes, staff account management, persistent security audit events, login abuse limits, password recovery, and integration/security review of CSRF controls.
- [ ] Implement email verification/password recovery, online payment-provider integration, supplier performance reports, business reports, notifications, and support.
- [ ] Expand authorization, security, MySQL migration, and multi-connection concurrency coverage.
- [ ] Configure GitHub destination/privacy before publishing.

See [[Implementation Plan]] for the roadmap.
