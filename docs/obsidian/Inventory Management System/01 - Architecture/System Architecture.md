---
title: System Architecture
tags: [ims, architecture]
status: planned
---

# System Architecture

```mermaid
flowchart LR
  Customer[Customer portal] -->|HTTPS JSON API| API[Spring Boot API]
  Admin[Admin and POS application] -->|HTTPS JSON API| API
  API --> Security[Spring Security / RBAC]
  API --> Services[Domain services]
  Services --> DB[(MySQL 8)]
  Flyway[Flyway migrations] --> DB
  Services --> Ledger[Inventory movement ledger]
  POS[Cashier sales] -->|locked stock deduction| Ledger
```

Frontend clients use `/api/v1`. Controllers accept and return DTOs; services enforce business rules and authorization; repositories access MySQL. Flyway is the schema authority. See [[Backend Architecture]] and [[Frontend Architecture]].
