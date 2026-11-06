---
title: Technical Decisions
tags: [ims, decisions]
status: active
---

# Technical Decisions

## Use Maven Wrapper
**Decision:** Include Maven Wrapper and pin its distribution. **Reason:** Java 21 exists, but Maven is not globally installed.

## Preserve Spring Boot 3
**Decision:** Use Spring Boot 3.5.x as requested. **Reason:** The project brief specifies Boot 3; current generators have moved to Boot 4, so scaffold manually rather than silently changing the major version.

## Inventory ledger plus balance projection
**Decision:** Record each movement and maintain lockable balance rows. **Reason:** History, reconciliation, concurrency control, and current-stock reads need explicit support.

## Keep notes Obsidian-compatible in the repository
**Decision:** Prepare Markdown here until the intended vault path is supplied. **Reason:** Multiple unrelated registered vaults exist; do not write into an unrelated vault.
