---
title: Inventory Ledger
tags: [ims, inventory, ledger]
status: planned
---

# Inventory Ledger

`inventory_movements` records signed deltas, before/after quantity, type, reference, actor, timestamp, and idempotency key. `inventory_balances` is transactionally maintained for reads. Services lock a balance before checking and applying a change. A transfer must debit and credit within one transaction. Reconciliation compares balances with ledger sums.

No stock update is valid without a corresponding movement record.
