---
title: API Overview
tags: [ims, api]
status: implemented-partial
---

# API Overview

Base path: `/api/v1`. Controllers use DTOs, Jakarta validation, consistent errors, bounded pagination with a stable page response DTO, role guards, and springdoc OpenAPI. H2 route smoke tests pass; MySQL-backed startup and endpoint integration are not yet verified.

Implemented routes:
- Auth: `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`.
- Public catalog: `GET /catalog/products` and `/catalog/categories`.
- Public fulfillment options: `GET /catalog/warehouses` (active locations only).
- Customer addresses: `GET/POST /customer/addresses`, `PUT/DELETE /customer/addresses/{id}`. Addresses are limited to ten per account and object-scoped to the authenticated customer.
- Customer cart: `GET /customer/cart`, `POST /customer/cart/items`, `PUT/DELETE /customer/cart/items/{id}`, `DELETE /customer/cart`.
- Customer checkout/orders: `POST /customer/checkout`, `GET /customer/orders`, `GET /customer/orders/{id}`, `GET /customer/orders/{id}/history`, and `POST /customer/orders/{id}/cancel`. Checkout requires a UUID idempotency key, delivery address, active fulfillment location, and payment method. It recalculates current product prices, reserves available stock in the inventory transaction, snapshots the address and line values, then clears the cart. Customers can cancel pending orders; cancellation releases the reservation. Order and history reads are owner-scoped.
- Admin catalog: `GET/POST /admin/categories`; product reads at `GET /admin/products` and `GET /admin/products/{id}`; product writes at `POST /admin/products`, `PUT /admin/products/{id}`, and `DELETE /admin/products/{id}` (owner/admin only).
- Warehouses: `GET/POST /admin/warehouses`; purchasing staff can read the warehouse list for order entry.
- Inventory: `GET /admin/inventory/summary`, warehouse stock/movement reads, and `POST /admin/inventory/adjustments`.
- Suppliers: `GET/POST /admin/suppliers`, `GET/PUT/DELETE /admin/suppliers/{id}` (owner, admin, purchasing officer).
- Purchase orders: `GET/POST /admin/purchase-orders`, `GET /admin/purchase-orders/{id}`, and `POST /admin/purchase-orders/{id}/{submit|approve|reject|send|cancel|receive}`. Mutation roles vary by action; see [[../04 - Security/Authorization Matrix]].
- Customer order management: `GET /admin/customer-orders` (optional status, page, size filters), `GET /admin/customer-orders/{id}`, and `GET /admin/customer-orders/{id}/history`. Staff transitions use `POST /admin/customer-orders/{id}/status`; manual payment records use `POST /admin/customer-orders/{id}/payments/verify` with an idempotency UUID, full amount, and optional reference/notes.
- POS: `GET /admin/pos/config`, `GET /admin/pos/products?warehouseId=…`, `GET /admin/pos/customers`, `GET /admin/pos/sales`, `GET /admin/pos/sales/{id}`, and `POST /admin/pos/sales`. Owner/admin/cashier can search and create sales; accountants can read sale history in a read-only UI. Sale creation accepts product IDs/quantities, a warehouse, optional customer email, fixed discount, payment method, and UUID idempotency key. Prices are recalculated from the catalog. Cash requires tender at least equal to the total and returns change; `CARD_TERMINAL` records an external approval reference only.

Purchase order states: `DRAFT`, `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `ORDERED`, `PARTIALLY_RECEIVED`, `RECEIVED`, and `CANCELLED`. Receipt requests require an idempotency key; accepted quantities update inventory and the movement ledger in the same transaction.

Customer order statuses progress through `PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED`; invalid transitions are rejected. Customers can cancel while pending. Delivery consumes the reserved quantity and on-hand quantity once and records the sale movement. Status history is available to the order owner and authorized staff. The `READY_FOR_PICKUP` enum is reserved for a future checkout flow; current checkout does not offer pickup. Customer payment methods are `CASH_ON_DELIVERY` (starts `UNPAID`) and `MANUAL_TRANSFER` (starts `AWAITING_VERIFICATION`). Staff can record a full-amount manual-transfer verification before shipping or record COD collection after delivery. The append-only verification record identifies the staff member and retains amount, method, currency, reference, note, timestamp, and idempotency key. This is a manual staff action; no bank, wallet, or payment-provider confirmation is integrated. Tax rate, flat shipping amount, and currency come from `IMS_CHECKOUT_TAX_RATE`, `IMS_CHECKOUT_SHIPPING_AMOUNT`, and `IMS_CHECKOUT_CURRENCY_CODE`; defaults are `0.00`, `0.00`, and `PHP`.

POS reuses the checkout tax-rate and currency settings and applies tax after a fixed discount. Each completed POS sale snapshots catalog price and product details, binds the sale to its cashier and warehouse, and stores payment method, tender/change or external terminal reference. A locked stock balance must have enough available quantity before tracked inventory is reduced; the sale and movement commit together. `TrackingType.NONE` products do not create stock movements. The UUID idempotency key replays the original sale for matching requests and rejects changed payloads. POS terminal approval is a manual cashier assertion; card data and provider transactions are not handled.

Management routes have role-level guards. Customer routes require the `CUSTOMER` role; address, cart, order, and history lookups are scoped to the current customer. Customer-order reads are available to owner/admin, warehouse, customer-support, accounting, and cashier roles; fulfillment transitions are limited to owner/admin/warehouse staff; payment recording is limited to owner/admin/accounting/cashier. POS sale creation is limited to owner/admin/cashier and sale-history reads also allow accountants. Fine-grained permissions, business/tenant boundaries, and warehouse-level scopes are not implemented. Supplier returns, online payment-provider adapters, customer returns/refunds, reports, support, notifications, and general security-audit search are not implemented.
