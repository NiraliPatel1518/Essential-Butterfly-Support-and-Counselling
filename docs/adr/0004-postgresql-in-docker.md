# ADR-0004: PostgreSQL in Docker, backend as a Docker image

Status: Accepted
Date: 2026-11-03

## Context
The team needs the same database on every computer and a way to deploy the backend without manual setup.

## Decision
- PostgreSQL 17 runs from the official image through `backend/docker-compose.yml`, with a named volume so data survives restarts and a health check.
- The backend has a multi-stage `Dockerfile`: a JDK image builds the jar, and a smaller JRE image runs it.
- GitHub Actions builds, tests and pushes the backend image to Docker Hub.

## Options considered
- Install PostgreSQL on each computer: slow and different per person.
- Docker Compose for the database: one command, same version for everyone.
- Multi-stage image build: the final image has no build tools, so it is smaller and has less to attack.

## Consequences
Good:
- Same database version for everyone.
- Smaller backend image.
- Data stays in a volume when the container restarts.

Risks and limits:
- Today only the backend has an image. The frontend is not containerised yet and the Compose file starts only the database.
- Next step (Sprint 2): run database, backend and frontend as separate containers on a private Docker network, and pass passwords with Docker secrets instead of plain values.
- The Compose file must not contain real passwords (see the operations runbook).

## Related
Code: `backend/Dockerfile`, `backend/docker-compose.yml`, `.github/workflows/ci-cd.yml`.
