---
title: Testing Strategy
tags: [ims, testing]
status: active
---

# Testing Strategy

- Unit tests for domain services and calculations.
- API tests for validation, authentication, authorization, and ownership.
- MySQL integration tests for migrations, constraints, rollback, and persistence.
- Concurrency/idempotency tests for deductions, receiving, and transfers.
- Frontend checks for forms, loading/error/empty states, role navigation, and responsive flows.
- Security review for CORS/CSRF, rate limits, uploads, secrets, logs, and audit access.

## Current results
- Frontend production build: passed.
- Frontend UI tests: 11 passed, including catalog rendering/filtering, customer cart/checkout/order history, product/supplier/purchase forms, partial receiving, staff order fulfillment, and manual payment recording.
- Backend: 25 passed — 12 H2 application/security smoke tests, 2 origin-guard unit tests, 5 customer commerce integration tests, 4 inventory service unit tests, and 2 purchase-order receiving integration tests.
- H2 smoke coverage includes public catalog paging, page-size limit validation, unauthenticated management rejection, customer-role allow/deny checks, customer-order role gates, and accepted/rejected refresh origins. Commerce integration coverage exercises server-side totals, reservation/release, fulfillment stock deduction, same-key replay, changed-payload rejection, history ownership, insufficient-stock rollback, and payment workflow rules. Flyway is disabled in this test profile.
- MySQL integration/migration and true multi-connection concurrency tests: not run; no application DB account is available. Purchasing reference reads are covered for a purchasing role; a full authorization matrix remains.
- Oxlint exits successfully with five existing React warnings about synchronous state updates in effects and Fast Refresh exports; these are cleanup items, not test failures.
