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
- Admin catalog: `GET/POST /admin/categories`; product reads at `GET /admin/products` and `GET /admin/products/{id}`; product writes at `POST /admin/products`, `PUT /admin/products/{id}`, and `DELETE /admin/products/{id}` (owner/admin only).
- Warehouses: `GET/POST /admin/warehouses`; purchasing staff can read the warehouse list for order entry.
- Inventory: `GET /admin/inventory/summary`, warehouse stock/movement reads, and `POST /admin/inventory/adjustments`.
- Suppliers: `GET/POST /admin/suppliers`, `GET/PUT/DELETE /admin/suppliers/{id}` (owner, admin, purchasing officer).
- Purchase orders: `GET/POST /admin/purchase-orders`, `GET /admin/purchase-orders/{id}`, and `POST /admin/purchase-orders/{id}/{submit|approve|reject|send|cancel|receive}`. Mutation roles vary by action; see [[../04 - Security/Authorization Matrix]].

Purchase order states: `DRAFT`, `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `ORDERED`, `PARTIALLY_RECEIVED`, `RECEIVED`, and `CANCELLED`. Receipt requests require an idempotency key; accepted quantities update inventory and the movement ledger in the same transaction.

Management routes have role-level guards. Fine-grained permissions, business/tenant boundaries, and warehouse-level scopes are not implemented. Supplier returns, checkout, customer orders, POS, payments, sales returns, reports, support, notifications, and audit routes are not implemented.
