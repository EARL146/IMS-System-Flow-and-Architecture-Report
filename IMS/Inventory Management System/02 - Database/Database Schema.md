---
title: Database Schema
tags: [ims, database, schema]
status: designed-unverified
---

# Database Schema

V6 adds damaged quantity deltas to inventory movements, POS sale refund payment states, return cases and item snapshots, manual refund records, and request/refund idempotency keys. V7 adds supplier return cases and lines, supplier credit-note records, idempotency keys, and audited purchase-order cancellation values. The schema is authored but still requires AMPPS MySQL migration verification.

## Authored domains

- V1: users/roles, refresh sessions, categories, products, warehouses, inventory balances, inventory movements, and inventory idempotency keys.
- V2: suppliers, purchase orders and lines, goods receipts and lines, and receipt idempotency keys.
- V3: customer delivery addresses, persistent cart rows, customer orders and item snapshots, checkout idempotency keys, and `inventory_movements.reserved_quantity_delta`.
- V4: customer order status history and manual payment verification records, with a status-history backfill for orders that predate V4.
- V5: independent POS sale headers, item snapshots (including internal cost), and request idempotency records. POS receipts do not reuse customer-order rows.
- V6: damaged movement deltas, POS refund payment states, customer return cases/items, manual refunds, and return/refund idempotency records.
- V7: supplier return cases/items linked to received purchase-order lines, manual supplier credit notes, return idempotency, and cancellation reason/actor/time plus unreceived commitment value on purchase orders.

## Planned domains

External payment-provider transactions, supplier accounts-payable integration, notifications, support, general security audit, configurable tax tables, and business settings.

All seven Flyway migrations are present, but none has been applied to the AMPPS MySQL instance. H2 tests use Hibernate `create-drop` with Flyway disabled, so they do not verify MySQL migration compatibility. A table is not production-verified until its migration is applied and checked against MySQL.
