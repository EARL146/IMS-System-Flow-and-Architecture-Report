---
title: System Architecture
tags: [ims, architecture]
status: implemented-partial
---

# System Architecture

![[System Architecture.png]]

Editable vector: [[System Architecture.svg]].

## Request path

Customer and staff browser pages are built with React, TypeScript, and Vite. They call the Spring Boot API under `/api/v1`. Spring Security authenticates requests and applies role checks. Controllers validate DTOs, services enforce business rules in transactions, and JPA repositories persist to MySQL.

Inventory changes update the locked balance projection and the movement ledger in the same transaction. Flyway migrations define schema changes. V1–V7 are authored; verification against the local MySQL server remains pending.

Supplier credits, refunds, and payment verification are recorded from staff-entered references. No payment gateway or accounts-payable system is connected.

See [[Backend Architecture]], [[Frontend Architecture]], and [[../03 - API Documentation/Supplier Returns]].
