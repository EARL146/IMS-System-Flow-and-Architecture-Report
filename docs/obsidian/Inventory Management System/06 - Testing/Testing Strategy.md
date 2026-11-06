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
- Frontend UI tests: 2 passed (catalog rendering/filtering and required-field validation).
- Backend unit tests: 6 passed (stock adjustment, negative-stock rejection, retry replay, idempotency-key reuse conflict, and allowed/rejected cookie-auth origins).
- MySQL integration, migration, security, authorization, and true multi-connection concurrency tests: not run; no application DB account is available.
- Oxlint exits successfully with five React warnings about synchronous state updates in effects and Fast Refresh exports; these are cleanup items, not test failures.
