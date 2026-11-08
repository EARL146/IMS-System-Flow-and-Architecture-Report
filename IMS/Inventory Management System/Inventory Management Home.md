---
title: Inventory Management Home
tags: [ims, home]
status: active
---

# Inventory Management System

> [!info] Project state
> The catalog, inventory foundation, supplier purchasing/receiving, supplier returns, audited purchase cancellation, customer checkout/orders, staff fulfillment/payment recording, cashier POS, and customer/POS returns are implemented. All 35 backend tests, all 18 frontend UI tests, and the production build pass. MySQL migration verification and later modules remain.

## Project
- Vision: [[00 - Project Overview/Project Vision]]
- Requirements: [[00 - Project Overview/Project Requirements]]
- Business rules: [[00 - Project Overview/Business Rules]]
- Roadmap: [[00 - Project Overview/Development Roadmap]]
- Implementation plan: [[Implementation Plan]]

## Engineering
- Architecture: [[01 - Architecture/System Architecture]]
- Backend: [[01 - Architecture/Backend Architecture]]
- Frontend: [[01 - Architecture/Frontend Architecture]]
- Threat model: [[04 - Security/Threat Model]]
- Database schema: [[02 - Database/Database Schema]]
- Inventory ledger: [[02 - Database/Inventory Ledger]]
- API status: [[03 - API Documentation/API Overview]]
- POS workflow: [[03 - API Documentation/Point of Sale]]
- Returns and refunds: [[03 - API Documentation/Returns and Refunds]]
- Supplier returns: [[03 - API Documentation/Supplier Returns]]
- Testing: [[06 - Testing/Testing Strategy]]
- Deployment: [[07 - Deployment/Deployment Guide]]

## Verified environment
- Node.js 24.13.0 / npm 11.12.1; production build and all 18 UI tests pass
- Java 21.0.11 / Maven Wrapper 3.9.16; all 35 backend tests pass
- AMPPS MySQL 8.0.45; port 3306 listens locally; app credentials are not provisioned
- AMPPS phpMyAdmin uses cookie authentication
- Maven is not installed globally; use Maven Wrapper
- Local Obsidian vault: `IMS/`; project notes are under `IMS/Inventory Management System/` and mirrored from `docs/obsidian/Inventory Management System/`

## Progress
- [x] Inspect the initially empty workspace and local stack
- [x] Product/category/warehouse and inventory API/UI foundation
- [x] Customer catalog reads live API data
- [x] Customer registration/login, addresses, cart, server-priced checkout, stock reservation, order history, and pending-order cancellation
- [x] Staff order fulfillment, customer order tracking, and auditable manual payment recording
- [x] Cashier POS with stock-checked receipts, payment capture, and browser printing
- [x] Customer and POS returns with review, inspection dispositions, inventory movements, and recorded refunds
- [ ] Online payment-provider integration, reporting, support, and notifications
- [ ] Complete permission administration and business/warehouse boundaries
- [x] Supplier records, purchase-order approvals, and partial receiving
- [x] Supplier returns and audited purchase cancellation
- [ ] Supplier reports and support
- [ ] Run MySQL integration verification and security review

See [[05 - Development/Development Progress]].
