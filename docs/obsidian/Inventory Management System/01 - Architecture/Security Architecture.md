---
title: Security Architecture
tags: [ims, security]
status: planned
---

# Security Architecture

Implemented controls: short-lived signed access tokens, hashed rotating refresh tokens in HttpOnly/SameSite=Strict cookies, exact-Origin checks for refresh/logout, BCrypt password hashing, DTO validation, a strict CORS allowlist, and role guards. Remaining controls include fine-grained permissions/object scopes, login rate limits, secure uploads, persistent audit events, production secret injection, and a split least-privilege migration/runtime DB account. Cookie-origin behavior still needs integration testing.

These controls are not complete until implemented and verified. See [[../04 - Security/Threat Model]].
