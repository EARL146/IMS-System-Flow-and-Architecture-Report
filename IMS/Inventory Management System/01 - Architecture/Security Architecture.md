---
title: Security Architecture
tags: [ims, security]
status: planned
---

# Security Architecture

Implemented controls: short-lived signed access tokens, hashed rotating refresh tokens in HttpOnly/SameSite=Strict cookies, exact-Origin checks for refresh/logout, BCrypt password hashing, DTO validation, a strict CORS allowlist, role guards, normalized access-denied responses, customer object ownership checks, and row-locked/idempotent checkout stock reservations. Customer role allow/deny behavior is covered in API smoke tests and cross-customer order access is covered in a service integration test. Remaining controls include fine-grained permissions/business scopes, login rate limits, secure uploads, persistent audit events, production secret injection, and a split least-privilege migration/runtime DB account. Cookie-origin behavior has unit coverage but still needs endpoint integration and security review.

These controls are not complete until implemented and verified. See [[../04 - Security/Threat Model]].
