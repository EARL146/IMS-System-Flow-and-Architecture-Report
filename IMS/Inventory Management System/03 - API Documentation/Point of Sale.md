---
title: Point of Sale
tags: [ims, api, pos, sales]
status: implemented-partial
---

# Point of Sale

The POS is a staff-only counter-sale workflow that creates independent receipts. It does not create a customer order or collect card details.

## Routes

All routes use `/api/v1/admin/pos`:

- `GET /config` returns the configured tax rate and currency.
- `GET /products?warehouseId=…&query=…&page=…&size=…` searches active catalog items and current available stock.
- `GET /customers?query=…&page=…&size=…` searches enabled customer accounts by name or email.
- `GET /sales?page=…&size=…` and `GET /sales/{id}` return receipts.
- `POST /sales` creates a sale.

Owner, admin, and cashier roles can search for checkout and create receipts. Owner, admin, cashier, and accountant roles can read sales. Accountants receive a history-only screen with receipt details and no checkout controls. See [[../04 - Security/Authorization Matrix]].

## Sale request and pricing

The request contains a UUID idempotency key, active warehouse ID, optional customer email, unique product ID/quantity lines, fixed discount, payment method, and method-specific tender/reference fields. The server reloads active products and computes line prices, subtotal, tax, and total. Tax reuses `IMS_CHECKOUT_TAX_RATE`; currency reuses `IMS_CHECKOUT_CURRENCY_CODE`.

Cash requests include `amountTendered` greater than or equal to the sale total. The server records change. `CARD_TERMINAL` requests must omit tender amount and include a reference entered after approval in an external terminal. This is a manual confirmation only; the application has no payment-provider integration and must not receive card numbers or tokens.

The POS sale, item snapshots, idempotency link, stock balance changes, and inventory movements commit in one transaction. Matching retries return the original receipt; reusing a key with a changed request is rejected. Tracked inventory is deducted only after locking the warehouse/product balance and confirming available stock. Untracked products do not create inventory movements.

## Current boundaries

Receipts support browser printing and recent-sale lookup. Sale voids, returns, refunds, provider settlement, sales reports, cashier shift reconciliation, and MySQL migration verification remain future work. See [[API Overview]] and [[../02 - Database/Inventory Ledger]].
