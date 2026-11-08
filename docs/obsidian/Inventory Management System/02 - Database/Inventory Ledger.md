---
title: Inventory Ledger
tags: [ims, inventory, ledger]
status: implemented-partial
---

# Inventory Ledger

Return inspection uses ledger entries for sellable restock and damaged returns. A damaged return increases both on-hand and damaged quantity, so it does not become available stock. Scrap remains outside inventory.

Supplier-return dispatch writes a signed `SUPPLIER_RETURN` movement for each tracked purchase line. It removes the dispatched quantity and the marked damaged quantity from the balance; it requires enough unreserved stock in each condition. Requesting, approving, rejecting, and cancelling a supplier return do not change inventory. The purchase-order cancellation flow also leaves received stock untouched and only releases unreceived commitment.

`inventory_movements` records signed on-hand and reserved quantity deltas, before/after on-hand values, type, reference, actor, timestamp, and idempotency key. `inventory_balances` is transactionally maintained for reads. Services lock a balance before checking and applying a change. Customer checkout adds a reservation without reducing on-hand quantity; pending-order cancellation releases it. Delivery converts the reservation into a sale deduction by reducing both on-hand and reserved quantities in one transaction, with one deterministic movement key per order line. POS checks unreserved, undamaged available quantity, reduces on-hand only, and records one `POS_SALE` movement per tracked receipt line in the same transaction. A transfer must debit and credit within one transaction. Reconciliation compares balance projections with ledger sums.

No stock update is valid without a corresponding movement record.
