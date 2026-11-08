---
title: Testing Strategy
tags: [ims, testing]
status: active
---

# Testing Strategy

Returns coverage includes customer and supplier return requests, approval, stock disposition/dispatch, credit/refund idempotency, and purchase-order cancellation. The full frontend and backend suites pass.

- Unit tests for domain services and calculations.
- API tests for validation, authentication, authorization, and ownership.
- MySQL integration tests for migrations, constraints, rollback, and persistence.
- Concurrency/idempotency tests for deductions, receiving, and transfers.
- Frontend checks for forms, loading/error/empty states, role navigation, and responsive flows.
- Security review for CORS/CSRF, rate limits, uploads, secrets, logs, and audit access.

## Current results
- Frontend production build: passed.
- Frontend UI tests: all 18 passed, including supplier-return creation, purchase cancellation, customer returns, staff inspection dispositions, POS receipts, and existing catalog/customer/purchasing flows.
- Backend: 35 passed — 16 H2 application/security smoke tests, 2 origin-guard unit tests, 6 customer commerce/returns integration tests, 4 inventory service unit tests, 2 POS workflow integration tests, 3 purchase-order receiving/cancellation integration tests, and 2 supplier-return workflow integration tests.
- H2 smoke coverage includes public catalog paging, page-size limit validation, unauthenticated management rejection, customer-role allow/deny checks, customer-order role gates, and accepted/rejected refresh origins. Commerce integration coverage exercises server-side totals, reservation/release, fulfillment stock deduction, same-key replay, changed-payload rejection, history ownership, insufficient-stock rollback, and payment workflow rules. Flyway is disabled in this test profile.
- MySQL integration/migration and true multi-connection concurrency tests: not run; no application DB account is available. Purchasing reference reads and warehouse supplier-return read/create role separation have smoke coverage; a full authorization matrix remains.
- Oxlint exits successfully with five existing React warnings about effect state updates and Fast Refresh exports.
