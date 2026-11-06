---
title: Database Schema
tags: [ims, database, schema]
status: designed-unverified
---

# Database Schema

## Authored domains

- V1: users/roles, refresh sessions, categories, products, warehouses, inventory balances, inventory movements, and inventory idempotency keys.
- V2: suppliers, purchase orders and lines, goods receipts and lines, and receipt idempotency keys.

## Planned domains

Customers, carts, orders, sales, payments, returns, notifications, support, audit, tax, and settings.

Both Flyway migrations are present, but neither has been applied to the AMPPS MySQL instance. H2 tests use Hibernate `create-drop` with Flyway disabled, so they do not verify MySQL migration compatibility. A table is not production-verified until its migration is applied and checked against MySQL.
