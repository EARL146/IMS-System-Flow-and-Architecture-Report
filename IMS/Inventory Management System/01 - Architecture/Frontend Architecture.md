---
title: Frontend Architecture
tags: [ims, frontend]
status: in-progress
---

# Frontend Architecture

Planned structure: routes, customer/admin layouts, domain pages, typed API services, auth state, form validation, design tokens, and reusable feedback/loading/empty states. The server is authoritative for role access, prices, stock, and status transitions.

Framework: React + TypeScript + Vite. Add libraries only as used. Customer and admin experiences share an API client but have separate route/layout boundaries.
