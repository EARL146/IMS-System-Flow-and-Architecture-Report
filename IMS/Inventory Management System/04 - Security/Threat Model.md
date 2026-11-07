---
title: Threat Model
tags: [ims, security, threat-model]
status: active
---

# Threat Model

| Asset | Threat | Required control |
| --- | --- | --- |
| Accounts | Credential stuffing / takeover | Password hashing, rate limits, short-lived access, rotated refresh tokens |
| Business data | IDOR / tenant leakage | Server-side object and business scope checks |
| Inventory | Forged adjustments / races | RBAC, transactions, row locks, constraints, idempotency, audit |
| Money | Browser price tampering / duplicate events | Server recalculation, decimal arithmetic, provider verification, idempotency |
| Uploads | Executable/path traversal content | Allowlist, signature, size limits, random names, private storage |
| Secrets | Repository/log exposure | Environment/secret manager, redaction, ignore rules |

Mitigations remain pending until implementation and tests verify them.
