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
- Manual transfer is marked paid only after an authorized staff member records a full-amount verification; COD collection can be recorded only after delivery. The system does not receive provider confirmation.
- Customer orders follow the configured fulfillment transitions. Reserved stock is consumed once when delivery is recorded; a manual-transfer order cannot ship or be prepared for pickup until payment is verified.
- Monetary values use fixed-precision decimals.

Checkout supports cash on delivery (`UNPAID`) and manual transfer (`AWAITING_VERIFICATION`). Staff payment records are idempotent and auditable. There is no payment-provider integration. Tax and shipping are flat configurable checkout values and default to zero. Rules remain targets until implementation and tests verify them.
