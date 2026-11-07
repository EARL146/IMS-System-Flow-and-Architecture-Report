---
title: Inventory Ledger
tags: [ims, inventory, ledger]
status: planned
---

# Inventory Ledger

`inventory_movements` records signed on-hand and reserved quantity deltas, before/after on-hand values, type, reference, actor, timestamp, and idempotency key. `inventory_balances` is transactionally maintained for reads. Services lock a balance before checking and applying a change. Customer checkout adds a reservation without reducing on-hand quantity; pending-order cancellation releases it. Delivery converts the reservation into a sale deduction by reducing both on-hand and reserved quantities in one transaction, with one deterministic movement key per order line. A transfer must debit and credit within one transaction. Reconciliation compares balance projections with ledger sums.

No stock update is valid without a corresponding movement record.
