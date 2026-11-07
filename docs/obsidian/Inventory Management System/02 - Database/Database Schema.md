---
title: Database Schema
tags: [ims, database, schema]
status: designed-unverified
---

# Database Schema

## Authored domains

- V1: users/roles, refresh sessions, categories, products, warehouses, inventory balances, inventory movements, and inventory idempotency keys.
- V2: suppliers, purchase orders and lines, goods receipts and lines, and receipt idempotency keys.
- V3: customer delivery addresses, persistent cart rows, customer orders and item snapshots, checkout idempotency keys, and `inventory_movements.reserved_quantity_delta`.
- V4: customer order status history and manual payment verification records, with a status-history backfill for orders that predate V4.
- V5: independent POS sale headers, item snapshots (including internal cost), and request idempotency records. POS receipts do not reuse customer-order rows.

## Planned domains

External payment-provider transactions, returns/refunds, notifications, support, general security audit, configurable tax tables, and business settings.

All five Flyway migrations are present, but none has been applied to the AMPPS MySQL instance. H2 tests use Hibernate `create-drop` with Flyway disabled, so they do not verify MySQL migration compatibility. A table is not production-verified until its migration is applied and checked against MySQL.
