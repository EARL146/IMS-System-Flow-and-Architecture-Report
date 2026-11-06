---
title: API Overview
tags: [ims, api]
status: implemented-partial
---

# API Overview

Base path: `/api/v1`. Controllers use DTOs, Jakarta validation, consistent errors, pagination limits, role guards, and springdoc OpenAPI. MySQL-backed startup and endpoint integration are not yet verified.

Implemented routes:
- Auth: `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`.
- Public catalog: `GET /catalog/products` and `/catalog/categories`.
- Admin catalog: `GET/POST /admin/categories`; `GET/POST /admin/products`; `GET/PUT/DELETE /admin/products/{id}`.
- Warehouses: `GET/POST /admin/warehouses`.
- Inventory: `GET /admin/inventory/summary`, warehouse stock/movement reads, and `POST /admin/inventory/adjustments`.

Management routes have role-level guards. Fine-grained permissions, business/tenant boundaries, and warehouse-level scopes are not implemented. Procurement, checkout, orders, POS, payments, returns, reports, support, notifications, and audit routes are not implemented.
