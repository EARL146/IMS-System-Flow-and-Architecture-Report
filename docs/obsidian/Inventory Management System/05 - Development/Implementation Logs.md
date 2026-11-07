---
title: Implementation Logs
tags: [ims, development, log]
status: active
---

# Implementation Logs

## 2026-11-06 — Initial inspection
- Workspace was empty and not a Git repository.
- Node 24.13.0, npm 11.12.1, and Java 21.0.11 are installed.
- AMPPS includes MySQL 8.0.45, Apache 2.4.66, PHP 8.2.30, and phpMyAdmin.
- Local MySQL accepts connections; application credentials are unavailable.
- No application, schema, or security configuration was found in the IMS workspace.

## 2026-11-06 — Supplier and purchasing workflow
- Added Flyway V2 tables for suppliers, purchase orders/lines, goods receipts/lines, and receipt idempotency.
- Added supplier profile APIs and purchase-order draft, submit, approve/reject, send, cancel, and partial receipt actions.
- Receipts update inventory balances and the movement ledger in the same transaction; over-receipts are rejected and matching retries replay safely.
- Added staff screens for supplier records and purchase-order work, with role-based navigation and controls.
- Purchasing staff can read active products and warehouse options but cannot edit those records.
- Added two receiving workflow integration tests, a purchasing role reference-access test, and supplier/receipt UI tests.
- Current verification: 6 frontend tests and 14 backend tests pass; the production frontend build passes.
- MySQL migrations remain unverified because the application database account has not been provisioned.
- Obsidian notes are in this project; open `docs/obsidian/Inventory Management System/` as a vault when desired.

## 2026-11-06 — Customer commerce foundation
- Added Flyway V3 customer-address, persistent-cart, customer-order, order-line snapshot, checkout-idempotency, and reserved-movement-delta schema.
- Added customer-scoped address/cart/checkout/order APIs and public active fulfillment-location reads.
- Checkout uses current server prices, flat configured tax/shipping, per-customer cart/user locking, row-locked available stock reservation, address/line snapshots, and idempotent request replay. Pending-order cancellation releases reservations.
- Added customer registration/login, storefront add-to-cart, cart/checkout, address management, order list/detail, and pending-order cancellation pages.
- COD remains unpaid until collection; manual transfer remains awaiting verification. The application has no payment-provider integration, payment-verification workflow, email verification, or password-recovery flow.
- Customer role and ownership checks, checkout totals/idempotency, reservation/release, insufficient-stock rollback, and payment-state tests pass. A security test found method-security denial was being returned as 500; centralized handling now returns 403.
- Current verification: frontend build passes with 8 UI tests; backend passes 18 tests. Oxlint passes with five existing warnings.
- MySQL migrations remain unverified because the application database account has not been provisioned.
- Confirmed the local Obsidian vault at `IMS/`; copied the project notes into `IMS/Inventory Management System/`, verified internal links, and excluded `.obsidian/` settings and the generated welcome note from Git.

## 2026-11-07 — Staff fulfillment and payment verification
- Added Flyway V4 status-history and payment-verification tables, including a history snapshot for existing orders.
- Added row-locked customer-order transitions, staff status-history APIs, and owner-scoped customer history reads. Checkout does not offer pickup, so current staff transitions use the shipped/delivered path.
- Delivery now consumes reserved and on-hand stock once per tracked order line and writes a `SALE_FULFILLMENT` ledger movement with a deterministic idempotency key.
- Added a staff queue for order progress and an audit record for full-amount manual-transfer verification or COD cash collection. Manual-transfer orders cannot ship before staff verification; COD collection can be recorded only after delivery.
- Added customer order timeline display and tests for delivery ledger integrity, idempotent payment replay, manual-transfer sequencing, and history ownership.
- Current verification: 11 frontend tests pass, the production build passes, and all 25 backend tests pass. `git diff --check` passes. MySQL migration validation remains outstanding.
