---
title: Deployment Guide
tags: [ims, deployment]
status: planned
---

# Deployment Guide

## Local target
- MySQL runs under AMPPS; Spring connects directly over JDBC.
- phpMyAdmin is for local administration and must not be exposed publicly.
- Spring Boot serves the API; Vite serves the frontend during development.

## Before production
- Provision a dedicated database and least-privilege service account.
- Inject secrets outside the repository; enable HTTPS and secure cookies.
- Set explicit CORS origins, backups, restore drills, monitoring, and rollback.
- Run Flyway migrations and security/integration tests against the target environment.

No deployment has been performed.
