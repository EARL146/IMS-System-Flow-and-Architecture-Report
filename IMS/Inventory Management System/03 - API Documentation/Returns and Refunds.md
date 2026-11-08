# Returns and Refunds

## Workflow

The system supports customer order returns and point of sale returns through a shared return case.

1. A customer submits a return request against delivered order lines. A cashier can submit one against a POS sale.
2. Customer support reviews each requested quantity and approves partial quantities or rejects the request with a reason.
3. Warehouse or inventory staff inspects approved items and records each quantity as **restock**, **damaged**, or **scrap**. These quantities must total the approved quantity.
4. The system records restocked quantities as sellable on hand. Damaged quantities increase on hand and damaged stock together, so they do not increase available stock. Scrap is recorded on the return case and is not added to inventory.
5. An authorized cashier or accountant records a refund after it has been processed outside IMS. IMS stores the method, amount, reference, operator, and time; it does not contact a payment provider.

Every request and refund requires an idempotency key. Refund amounts are calculated from the original sale lines, discount, and tax, and exclude customer order shipping. Staff cannot override the calculated amount.

## API

- POST /api/v1/customer/orders/{orderId}/returns — customer return request.
- GET /api/v1/customer/returns and GET /api/v1/customer/returns/{id} — customer's return history.
- POST /api/v1/admin/pos/sales/{saleId}/returns — cashier POS return request.
- GET /api/v1/admin/returns?status=REQUESTED — staff queue and status filter.
- GET /api/v1/admin/returns/{id} — staff case detail.
- POST /api/v1/admin/returns/{id}/approve — customer support review.
- POST /api/v1/admin/returns/{id}/reject — rejection with reason.
- POST /api/v1/admin/returns/{id}/inspect — warehouse disposition and inventory posting.
- POST /api/v1/admin/returns/{id}/refund — manual refund record.

## Data and audit

Migration V6__returns_and_refunds.sql creates return cases, return lines, refund records, and request/refund idempotency records. Inventory movement rows now include a damaged quantity delta. POS sales track PAID, PARTIALLY_REFUNDED, or REFUNDED; customer orders use the same payment states and retain return status history.

Return line quantities are bounded by the original sold quantity across all non-rejected cases. Inventory changes are posted in the same transaction as inspection, with stable movement keys so a retry cannot post stock twice. Refund records are unique per return case.

## Role access

- Customers can request and view returns for their own orders.
- Cashiers can create POS return cases and record refunds.
- Customer support can approve or reject cases.
- Warehouse and inventory managers can record inspection dispositions.
- Accountants can view cases and record refunds.
- Owners and admins can perform each staff action.

## Operational note

The external refund must be completed before its record is submitted to IMS. For bank transfers and external terminals, the operator enters the processor or bank reference. Cash refunds have no external reference.
