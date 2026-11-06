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
- Frontend UI tests: 6 passed (catalog rendering/filtering, product validation/editing, supplier validation/creation, purchase-order draft creation, and warehouse-staff partial receipt submission).
- Backend: 14 passed — 6 H2 application/security smoke tests, 2 origin-guard unit tests, 4 inventory service unit tests, and 2 purchase-order receiving integration tests.
- H2 smoke coverage includes public catalog paging, page-size limit validation, unauthenticated management rejection, and accepted/rejected refresh origins. Flyway is disabled in this test profile.
- MySQL integration/migration and true multi-connection concurrency tests: not run; no application DB account is available. Purchasing reference reads are covered for a purchasing role; a full authorization matrix remains.
- Oxlint exits successfully with five React warnings about synchronous state updates in effects and Fast Refresh exports; these are cleanup items, not test failures.
