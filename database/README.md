# MySQL setup

The Spring Boot API connects to MySQL directly. phpMyAdmin is used only to provision and inspect the database.

1. In phpMyAdmin, open the SQL tab and run [create-database.sql](create-database.sql). It creates only the project database, `inventory_management_system`; it does not touch any other database.
2. In phpMyAdmin's User accounts page, create a dedicated `ims_app` account for host `127.0.0.1`. Use a strong local password and grant access only to `inventory_management_system`.
3. Flyway runs migrations on API startup. The current shared application/migration account needs schema migration privileges as well as application read/write privileges. Split these accounts before production.
4. Set the matching URL, username, and password in the root `.env` file, then load it in each PowerShell terminal with `. .\\scripts\\load-env.ps1`.

The V1 migration is at `backend/src/main/resources/db/migration/V1__initial_schema.sql`. Do not import that migration manually; Flyway tracks and applies it.

## Current verification limit

The local MySQL service is present, but the available unauthenticated `root` login was rejected. The project has not created a database, user, or table on this machine. MySQL migration and phpMyAdmin verification remain pending until a valid admin login and dedicated app account are available.
