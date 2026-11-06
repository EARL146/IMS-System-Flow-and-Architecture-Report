---
title: Inventory Management Home
tags: [ims, home]
status: active
---

# Inventory Management System

> [!info] Project state
> The catalog, inventory foundation, supplier directory, and purchase-receiving workflow are implemented and locally verified. MySQL migration verification and later sales/customer modules remain.

## Project
- Vision: [[00 - Project Overview/Project Vision]]
- Requirements: [[00 - Project Overview/Project Requirements]]
- Business rules: [[00 - Project Overview/Business Rules]]
- Roadmap: [[00 - Project Overview/Development Roadmap]]
- Implementation plan: [[../../implementation-plan]]

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
- Node.js 24.13.0 / npm 11.12.1; frontend build and 6 UI tests pass
- Java 21.0.11 / Maven Wrapper 3.9.16; backend compiles and 14 tests pass (H2 smoke, origin guard, inventory, purchasing)
- AMPPS MySQL 8.0.45; port 3306 listens locally; app credentials are not provisioned
- AMPPS phpMyAdmin uses cookie authentication
- Maven is not installed globally; use Maven Wrapper
- Obsidian notes are ready under this project; open `docs/obsidian/Inventory Management System/` as a vault

## Progress
- [x] Inspect the initially empty workspace and local stack
- [x] Product/category/warehouse and inventory API/UI foundation
- [x] Customer catalog reads live API data
- [ ] Complete permission administration and business/warehouse boundaries
- [x] Supplier records, purchase-order approvals, and partial receiving
- [ ] Supplier returns, customer purchasing, POS, returns, reports, and support
- [ ] Run MySQL integration verification and security review

See [[05 - Development/Development Progress]].
