# Flyway Baseline Transition

## Why this project uses a single baseline

This service has been running against a Railway PostgreSQL database that was created by Hibernate schema auto-update rather than by Flyway migrations.

Because there is no reliable Flyway schema history in production, the safest migration strategy is:

1. Freeze the current schema as a single baseline migration.
2. Mark the existing production schema as already being at that baseline.
3. Start creating only forward migrations after that point.

That is why the migration history in `src/main/resources/db/migration` has been squashed into `V1__init.sql`.

## Recommended production cutover

### 1. Deploy code with Flyway still disabled

Use:

- `SPRING_FLYWAY_ENABLED=false`
- `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`

This confirms the application still boots against the existing Railway schema without Hibernate making further changes.

### 2. Create Flyway baseline metadata in the existing Railway database

Do a one-time deploy with:

- `SPRING_FLYWAY_ENABLED=true`
- `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`
- `SPRING_FLYWAY_BASELINE_VERSION=1`
- `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`

Because the production database is already non-empty, Flyway will create `flyway_schema_history`, register version `1`, and skip executing `V1__init.sql`.

### 3. Enable Flyway for normal app startup

After the baseline metadata exists, switch to:

- `SPRING_FLYWAY_ENABLED=true`
- `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`
- `SPRING_FLYWAY_BASELINE_ON_MIGRATE=false`

At that point, startup should validate the existing schema and Flyway will consider `V1__init.sql` already applied.

## New environments

For a brand-new empty database:

1. Set `SPRING_FLYWAY_ENABLED=true`
2. Set `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`
3. Leave `SPRING_FLYWAY_BASELINE_ON_MIGRATE=false`

Flyway will run `V1__init.sql` and create the schema from scratch.

## Important notes

- Do not run `migrate` with `baselineOnMigrate=true` against an empty database. That would skip `V1__init.sql`.
- Do not enable Hibernate auto-update again after the cutover. Future schema changes should be added as `V2__...`, `V3__...` migrations.
- If any environment already contains a `flyway_schema_history` table from a previous failed attempt, inspect it before enabling Flyway. You may need `flyway repair` or manual cleanup first.
