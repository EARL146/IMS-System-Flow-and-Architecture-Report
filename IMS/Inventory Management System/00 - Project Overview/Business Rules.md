---
title: Business Rules
tags: [ims, business-rules]
status: in-progress
---

# Business Rules

- Customers cannot assign themselves administrative roles.
- The browser is never authoritative for prices, taxes, discounts, stock, or order status. Checkout refreshes product prices and reserves tracked stock in the API transaction.
- Inventory changes must be authorized, transactional, traceable, and idempotent.
- Negative stock is disallowed unless business policy explicitly enables it.
- Transfers debit and credit warehouses in one database transaction.
- A purchase increases stock only through authorized goods receiving.
- Returned stock is inspected; refunding does not automatically make it sellable.
- Online payments are marked paid only after verified provider confirmation.
- Monetary values use fixed-precision decimals.

Checkout currently supports cash on delivery (`UNPAID`) and manual transfer (`AWAITING_VERIFICATION`). There is no payment-provider integration or endpoint that marks a customer order paid. Tax and shipping are flat configurable checkout values and default to zero. Rules remain targets until implementation and tests verify them.
