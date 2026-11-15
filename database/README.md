# MySQL Setup & Least-Privilege Security

The Spring Boot API connects to MySQL directly. phpMyAdmin or the `mysql` CLI is used only to provision and inspect the database.

1. Run [create-database.sql](file:///c:/Users/shizu/OneDrive/Desktop/IMS/database/create-database.sql) to create the `inventory_management_system` database (`utf8mb4_0900_ai_ci`).
2. Run [setup-users.sql](file:///c:/Users/shizu/OneDrive/Desktop/IMS/database/setup-users.sql) (after replacing the placeholder passwords) to provision two separate accounts:
   - `ims_migrator`: Used for Flyway schema migrations (`V1` through `V8`).
   - `ims_app`: Least-privilege runtime account granted only `SELECT, INSERT, UPDATE, DELETE` on `inventory_management_system.*`.
3. Set the matching URL, username, and password in the root `.env` file, then load it in each PowerShell terminal with `. .\scripts\load-env.ps1`.
4. Use [scripts/backup-db.ps1](file:///c:/Users/shizu/OneDrive/Desktop/IMS/scripts/backup-db.ps1) and [scripts/restore-db.ps1](file:///c:/Users/shizu/OneDrive/Desktop/IMS/scripts/restore-db.ps1) for transactional backups with SHA-256 integrity verification.
