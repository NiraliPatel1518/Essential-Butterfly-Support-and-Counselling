# ADR-0004: PostgreSQL in Docker with a persistent volume

Status: Accepted
Date: 2026-11-03

## Context
The team needs the same database on every computer and a way to deploy without manual setup.

## Decision
- PostgreSQL 17 runs from the official image as the `db` service in `docker-compose.yml`.
- Data is kept in the named volume `postgres-data`, so it survives restarts.
- A health check (`pg_isready`) tells Compose when the database is ready. The backend starts only after the database is healthy.
- The database password is not written in the Compose file. It is read from a Docker secret (see ADR-0013).
- The backend creates and updates tables with Hibernate (`ddl-auto=update`).

## Options considered
- Install PostgreSQL on each computer: slow and different per person.
- Docker Compose for the database: one command, same version for everyone.

## Consequences
Good:
- Same database version for everyone.
- Data stays in a volume when the container restarts.
- The database is not reachable from outside Docker (see ADR-0013).

Risks and limits:
- `ddl-auto=update` is convenient for development, but it is not a safe way to manage a live database. A migration tool (for example Flyway) should replace it before real use.
- There is no automatic backup yet. The runbook shows a manual backup.
- `docker compose down -v` deletes the volume and all data.

## Related
Code: `docker-compose.yml`, `essential-butterfly-backend/src/main/resources/application.properties`.
