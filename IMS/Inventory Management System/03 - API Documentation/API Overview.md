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
- Customer checkout/orders: `POST /customer/checkout`, `GET /customer/orders`, `GET /customer/orders/{id}`, and `POST /customer/orders/{id}/cancel`. Checkout requires a UUID idempotency key, delivery address, active fulfillment location, and payment method. It recalculates current product prices, reserves available stock in the inventory transaction, snapshots the address and line values, then clears the cart. Customers can cancel pending orders; cancellation releases the reservation.
- Admin catalog: `GET/POST /admin/categories`; product reads at `GET /admin/products` and `GET /admin/products/{id}`; product writes at `POST /admin/products`, `PUT /admin/products/{id}`, and `DELETE /admin/products/{id}` (owner/admin only).
- Warehouses: `GET/POST /admin/warehouses`; purchasing staff can read the warehouse list for order entry.
- Inventory: `GET /admin/inventory/summary`, warehouse stock/movement reads, and `POST /admin/inventory/adjustments`.
- Suppliers: `GET/POST /admin/suppliers`, `GET/PUT/DELETE /admin/suppliers/{id}` (owner, admin, purchasing officer).
- Purchase orders: `GET/POST /admin/purchase-orders`, `GET /admin/purchase-orders/{id}`, and `POST /admin/purchase-orders/{id}/{submit|approve|reject|send|cancel|receive}`. Mutation roles vary by action; see [[../04 - Security/Authorization Matrix]].

Purchase order states: `DRAFT`, `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `ORDERED`, `PARTIALLY_RECEIVED`, `RECEIVED`, and `CANCELLED`. Receipt requests require an idempotency key; accepted quantities update inventory and the movement ledger in the same transaction.

Customer order statuses currently start at `PENDING` and can be cancelled by the customer while pending. Customer payment methods are `CASH_ON_DELIVERY` (starts `UNPAID`) and `MANUAL_TRANSFER` (starts `AWAITING_VERIFICATION`). No route marks a payment paid. Tax rate, flat shipping amount, and currency come from `IMS_CHECKOUT_TAX_RATE`, `IMS_CHECKOUT_SHIPPING_AMOUNT`, and `IMS_CHECKOUT_CURRENCY_CODE`; defaults are `0.00`, `0.00`, and `PHP`.

Management routes have role-level guards. Customer routes require the `CUSTOMER` role; address, cart, and order lookups are scoped to the current customer. Fine-grained permissions, business/tenant boundaries, and warehouse-level scopes are not implemented. Staff fulfillment, supplier returns, POS, payment verification/provider adapters, customer returns, reports, support, notifications, and audit routes are not implemented.
