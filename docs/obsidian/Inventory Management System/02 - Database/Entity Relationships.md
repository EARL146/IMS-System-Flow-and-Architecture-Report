---
title: Entity Relationships
tags: [ims, database, erd]
status: implemented-partial
---

# Entity Relationships

```mermaid
erDiagram
  APP_USER ||--o{ USER_ROLE : has
  ROLE ||--o{ USER_ROLE : assigned
  CATEGORY ||--o{ PRODUCT : groups
  PRODUCT ||--o{ INVENTORY_BALANCE : stocked
  WAREHOUSE ||--o{ INVENTORY_BALANCE : stores
  PRODUCT ||--o{ INVENTORY_MOVEMENT : records
  WAREHOUSE ||--o{ INVENTORY_MOVEMENT : locates
  APP_USER ||--o{ INVENTORY_MOVEMENT : performs
  APP_USER ||--o{ CUSTOMER_ADDRESS : saves
  APP_USER ||--o{ SHOPPING_CART_ITEM : owns
  PRODUCT ||--o{ SHOPPING_CART_ITEM : selected
  APP_USER ||--o{ CUSTOMER_ORDER : places
  WAREHOUSE ||--o{ CUSTOMER_ORDER : fulfills
  CUSTOMER_ORDER ||--|{ CUSTOMER_ORDER_ITEM : contains
  PRODUCT ||--o{ CUSTOMER_ORDER_ITEM : snapshots
  APP_USER ||--o{ CHECKOUT_IDEMPOTENCY_KEY : reserves
  CUSTOMER_ORDER o|--o| CHECKOUT_IDEMPOTENCY_KEY : created_by
```

These relationships match the authored V1-V3 migrations and JPA mappings. MySQL migration execution and schema validation remain unverified because no database account has been provisioned.
