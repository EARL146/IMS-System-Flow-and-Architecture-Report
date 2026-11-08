---
title: Supplier Returns
tags: [ims, purchasing, returns, inventory, finance]
status: implemented-unverified-mysql
---

# Supplier Returns

Supplier returns are recorded against quantities received on a purchase order. Requested and approved return quantities reserve those received units so active requests cannot overbook a purchase line. Rejected and cancelled requests release the reservation.

## Workflow

`PENDING_APPROVAL` can be approved, rejected, or cancelled. An `APPROVED` return can be dispatched or cancelled. Dispatch reduces stock and records one idempotent `SUPPLIER_RETURN` movement per line. A dispatched return cannot be cancelled or dispatched again.

Each line stores the quantity being returned and how much is damaged. Dispatch checks locked warehouse balances for enough unreserved sellable and damaged stock, reduces on-hand quantity, and reduces damaged quantity by the damaged amount. It does not allow stock to go negative or consume reserved stock. No inventory movement is made for products configured with `TrackingType.NONE`.

The expected supplier credit is estimated from the purchase-order unit costs. After dispatch, accounting staff can record one or more supplier credit notes with the supplier's reference, amount, currency, notes, recorder, and timestamp. Credit-note submissions have idempotency keys. These records are for reconciliation; IMS does not post to an accounts-payable ledger or contact an accounting system.

## Purchase-order cancellation

Staff with owner or admin access can cancel a purchase order before or after it is sent, provided it still has unreceived quantities. Cancellation requires a reason and records the actor and time. For a partially received order, already received inventory stays in stock and remains visible in receipt history. The system snapshots the value of the unreceived quantities as the released purchase commitment; that value does not represent an invoice payment or an accounts-payable adjustment. A fully received order cannot be cancelled. Goods already received must use the supplier-return workflow to leave inventory.

## API

Base path: `/api/v1/admin/supplier-returns`.

- `GET /admin/supplier-returns?page=0&size=50` and `GET /admin/supplier-returns/{id}` read the queue and case details.
- `GET /admin/supplier-returns/returnable-items?purchaseOrderId={id}` lists received quantities not reserved by another active return.
- `POST /admin/supplier-returns` creates an idempotent request. Each line has `purchaseOrderItemId`, `quantity`, and `damagedQuantity`.
- `POST /admin/supplier-returns/{id}/approve` and `/reject` review the request. Rejection body: `{ "reason": "..." }`.
- `POST /admin/supplier-returns/{id}/dispatch` authorizes the stock deduction.
- `POST /admin/supplier-returns/{id}/cancel` cancels a pending or approved return.
- `POST /admin/supplier-returns/{id}/credit-notes` records an issued supplier credit. The body includes `idempotencyKey`, `supplierReference`, positive `amount`, and optional `notes`.
- `POST /admin/purchase-orders/{id}/cancel` requires `{ "reason": "..." }` and is available to owners and admins while unreceived quantities remain.

Request and credit-note UUID keys replay a matching request and reject a changed payload. Return quantities are checked while holding the purchase-order lock; dispatch locks the return and inventory balances.

## Access

- Read: owner, admin, purchasing officer, warehouse staff, inventory manager, accountant.
- Create: owner, admin, purchasing officer.
- Approve or reject: owner, admin.
- Dispatch: owner, admin, warehouse staff, inventory manager.
- Cancel a supplier return: owner, admin, purchasing officer.
- Record supplier credit notes: owner, admin, accountant.
- Purchase-order cancellation: owner or admin; receives a required reason and an actor from the authenticated session.

## Verification limits

Backend integration tests cover request reservation, rejection release, dispatch stock guards, damaged stock removal, credit-note idempotency, and purchase-order cancellation after partial receiving. Flyway V7 still needs to be applied and validated against the AMPPS MySQL instance. Supplier credit records do not update a general ledger or vendor balance.
