---
title: Inventory Management Home
tags: [ims, home]
status: active
---

# Inventory Management System

> [!info] Project state
> The catalog, inventory foundation, supplier and purchase-receiving workflow, and customer cart/checkout/order slice are implemented and locally verified against H2. MySQL migration verification, staff fulfillment, POS, payments, and other later modules remain.

## Project
- Vision: [[00 - Project Overview/Project Vision]]
- Requirements: [[00 - Project Overview/Project Requirements]]
- Business rules: [[00 - Project Overview/Business Rules]]
- Roadmap: [[00 - Project Overview/Development Roadmap]]
- Implementation plan: [[../Implementation Plan]]

## Engineering
- Architecture: [[01 - Architecture/System Architecture]]
- Backend: [[01 - Architecture/Backend Architecture]]
- Frontend: [[01 - Architecture/Frontend Architecture]]
- Threat model: [[04 - Security/Threat Model]]
- Database schema: [[02 - Database/Database Schema]]
- Inventory ledger: [[02 - Database/Inventory Ledger]]
- API status: [[03 - API Documentation/API Overview]]
- Testing: [[06 - Testing/Testing Strategy]]
- Deployment: [[07 - Deployment/Deployment Guide]]

## Verified environment
- Node.js 24.13.0 / npm 11.12.1; frontend build and 8 UI tests pass
- Java 21.0.11 / Maven Wrapper 3.9.16; backend compiles and 18 tests pass (H2 security smoke, origin guard, customer checkout, inventory, purchasing)
- AMPPS MySQL 8.0.45; port 3306 listens locally; app credentials are not provisioned
- AMPPS phpMyAdmin uses cookie authentication
- Maven is not installed globally; use Maven Wrapper
- Local Obsidian vault: `IMS/`; project notes are under `IMS/Inventory Management System/` and mirrored from `docs/obsidian/Inventory Management System/`

## Progress
- [x] Inspect the initially empty workspace and local stack
- [x] Product/category/warehouse and inventory API/UI foundation
- [x] Customer catalog reads live API data
- [x] Customer registration/login, addresses, cart, server-priced checkout, stock reservation, order history, and pending-order cancellation
- [ ] Staff order fulfillment, payment verification, POS, returns/refunds, reporting, support, and notifications
- [ ] Complete permission administration and business/warehouse boundaries
- [x] Supplier records, purchase-order approvals, and partial receiving
- [ ] Supplier returns, customer purchasing, POS, returns, reports, and support
- [ ] Run MySQL integration verification and security review

See [[05 - Development/Development Progress]].
