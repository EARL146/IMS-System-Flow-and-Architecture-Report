---
title: Project Requirements
tags: [ims, requirements]
status: in-progress
---

# Project Requirements

## Customer experience
- Browse a real catalog and current availability.
- Maintain an account, addresses, cart, orders, tracking, returns, notifications, and support requests.
- Checkout totals and stock validation are calculated by the backend.
- Payment providers use an adapter; raw card data is never stored.

## Admin experience
- Manage products, categories, warehouses, inventory, suppliers, purchasing, customers, employees, sales, returns, reports, settings, and audit history.
- Every operation is authorized by the backend and scoped to business/warehouse permissions.
- Every visible action calls a real API or is labeled unavailable.

## Quality and security
- Java 21, Spring Boot 3, React, TypeScript, MySQL 8, and Flyway.
- DTOs and validation at API boundaries; business rules in backend services.
- Fixed-precision money; transactional, idempotent inventory movements.
- Unit, integration, authorization, concurrency, and migration tests.
- Keep secrets and private customer data out of source control and notes.

This is target scope. See [[Development Roadmap]] for delivery order.
