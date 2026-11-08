---
title: Development Roadmap
tags: [ims, roadmap]
status: active
---

# Development Roadmap

1. [x] Repository/runtime assessment and implementation plan.
2. [x] Design system and frontend application shell.
3. [ ] MySQL schema exists; verify migration on AMPPS.
4. [x] Spring API foundation, validation, errors, and OpenAPI.
5. [ ] Finish authentication hardening, permission RBAC, object scopes, audit logging, and abuse protection.
6. [~] Product/category/warehouse and inventory ledger slices exist; extend product tracking/editor and verify against MySQL.
7. [~] Supplier records, purchase approvals, and partial receiving implemented; supplier returns remain.
8. [~] Live catalog, customer registration/login, saved addresses, persistent cart, server-priced checkout, stock reservation, order history/status tracking, pending cancellation, auditable manual payment verification, and customer returns/refunds are implemented.
9. [~] Staff order fulfillment, once-only stock deduction at delivery, cashier POS receipts/stock deduction, POS returns, inspection disposition, and manual refund records are implemented; provider integrations and sales reporting remain.
10. [ ] Reports, support, notifications, exports, security tests, deployment, backup, and recovery.

`[~]` means partially implemented. A phase is complete only after its implementation is verified. See [[Implementation Plan]] and [[../05 - Development/Development Progress]].
