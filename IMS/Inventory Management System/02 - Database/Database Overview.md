---
title: Database Overview
tags: [ims, database]
status: designed-unverified
---

# Database Overview

Target: `inventory_management_system` on AMPPS MySQL 8 with UTF8MB4, Flyway migrations, foreign keys, indexes, transactions, and DECIMAL financial columns. V1 and V2 are authored but have not been run because application credentials have not been provisioned. V2 adds suppliers, purchase orders, goods receipts, and receipt idempotency. The local DB creation helper is at `database/create-database.sql`. See [[Database Schema]] and [[Inventory Ledger]].
