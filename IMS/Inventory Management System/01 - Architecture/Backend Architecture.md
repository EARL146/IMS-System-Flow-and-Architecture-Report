---
title: Backend Architecture
tags: [ims, backend]
status: planned
---

# Backend Architecture

Spring Boot 3 / Java 21 layered API:
- Controllers: HTTP and DTO boundary only.
- Services: transaction boundaries and business rules.
- Repositories: persistence access.
- Entities: internal persistence model, never directly serialized.
- Security: authentication, RBAC, object/warehouse scope, and audit.
- Flyway: reviewed schema evolution.

API routes are versioned under `/api/v1`. See [[../03 - API Documentation/API Overview]].
