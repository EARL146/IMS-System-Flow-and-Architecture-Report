---
title: Authorization Matrix
tags: [ims, security, authorization]
status: planned
---

# Authorization Matrix

| Capability | Owner | Admin | Inventory | Purchasing | Warehouse | Cashier | Accountant | Support | Customer |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Business settings | all business | scoped | no | no | no | no | read finance | no | no |
| Products | yes | scoped | stock fields | read | read | search | read | read | catalog |
| Stock adjustment | yes | scoped | assigned | no | assigned workflow | sale workflow | no | no | no |
| Purchase/receiving | yes | scoped | receive permission | create/approve by scope | receive assigned | no | read | no | no |
| POS/sales | yes | scoped | read | no | pick/pack | assigned register | read | no | own orders |
| Customer data | yes | scoped | no | no | assigned orders | transaction scope | report scope | support scope | own profile |

This is a proposed least-privilege matrix. Backend method and object authorization are still required.
