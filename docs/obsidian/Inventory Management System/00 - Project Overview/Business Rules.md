---
title: Business Rules
tags: [ims, business-rules]
status: in-progress
---

# Business Rules

- Customers cannot assign themselves administrative roles.
- The browser is never authoritative for prices, taxes, discounts, stock, or order status.
- Inventory changes must be authorized, transactional, traceable, and idempotent.
- Negative stock is disallowed unless business policy explicitly enables it.
- Transfers debit and credit warehouses in one database transaction.
- A purchase increases stock only through authorized goods receiving.
- Returned stock is inspected; refunding does not automatically make it sellable.
- Online payments are marked paid only after verified provider confirmation.
- Monetary values use fixed-precision decimals.

Rules remain targets until implementation and tests verify them.
