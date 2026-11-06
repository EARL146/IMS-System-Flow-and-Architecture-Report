---
title: Authorization Matrix
tags: [ims, security, authorization]
status: implemented-partial
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

This is a proposed least-privilege matrix. Current API guards are role-level only: supplier operations allow owner/admin/purchasing; purchase-order creation, submission, and send allow owner/admin/purchasing; approval, rejection, and cancellation allow owner/admin; receiving allows owner/admin/warehouse/inventory; reads are granted to purchasing and receiving staff as needed. Business, supplier, and warehouse-level object scopes are not implemented. Product and warehouse mutation remains owner/admin only.
