---
title: Authorization Matrix
tags: [ims, security, authorization]
status: implemented-partial
---

# Authorization Matrix

Return actions follow distinct staff roles: customer support reviews requests, warehouse staff or inventory managers record inspection dispositions, and cashiers or accountants record external refunds. Customers can only see their own order returns. The API enforces these roles and ownership checks.

| Capability | Owner | Admin | Inventory | Purchasing | Warehouse | Cashier | Accountant | Support | Customer |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Business settings | all business | scoped | no | no | no | no | read finance | no | no |
| Products | yes | scoped | stock fields | read | read | search | read | read | catalog |
| Stock adjustment | yes | scoped | assigned | no | assigned workflow | sale workflow | no | no | no |
| Purchase/receiving | yes | scoped | receive permission | create/approve by scope | receive assigned | no | read | no | no |
| Customer-order fulfillment | yes | scoped | no | no | transition assigned orders | read | read | read | own orders |
| Manual payment record | yes | scoped | no | no | no | assigned register | record verification | no | own payment state |
| POS create/search | yes | scoped | no | no | no | assigned register | no | no | no |
| POS sale history | yes | scoped | no | no | no | register access | read | no | no |
| Customer data | yes | scoped | no | no | assigned orders | transaction scope | report scope | support scope | own profile |
| Customer addresses/cart/checkout | no | no | no | no | no | no | no | no | own account only |

This is a proposed least-privilege matrix, not a complete permission system. Customer routes require the `CUSTOMER` role and scope addresses, carts, orders, and order histories to the authenticated user. Staff customer-order reads allow owner/admin, warehouse staff, customer support, accountant, and cashier; status transitions allow owner/admin/warehouse staff; payment recording allows owner/admin/accountant/cashier. POS sale creation/search allows owner/admin/cashier; sale history also allows accountants. Payment verification and external-terminal approval are staff-recorded manual assertions, not external provider confirmations. Staff API guards are role-level only: supplier operations allow owner/admin/purchasing; purchase-order creation, submission, and send allow owner/admin/purchasing; approval, rejection, and cancellation allow owner/admin; receiving allows owner/admin/warehouse/inventory; reads are granted to purchasing and receiving staff as needed. Business, supplier, and warehouse-level object scopes are not implemented. Product and warehouse mutation remains owner/admin only.

Supplier-return reads allow owner/admin/purchasing/warehouse/inventory/accounting; creation allows owner/admin/purchasing; approval and rejection allow owner/admin; dispatch allows owner/admin/warehouse/inventory; supplier-credit-note recording allows owner/admin/accounting. Purchase-order cancellation requires owner/admin.
