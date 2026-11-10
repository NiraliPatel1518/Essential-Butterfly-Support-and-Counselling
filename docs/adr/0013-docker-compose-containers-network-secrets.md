# ADR-0013: Separate containers, two networks and Docker secrets

Status: Accepted
Date: 2026-11-05

## Context
The application has three parts: database, backend and frontend. They should run separately so one can be restarted or replaced without the others. Passwords must not be written in files that go to GitHub.

## Decision
- `docker-compose.yml` runs three services: `db`, `backend` and `frontend`, each in its own container.
- Two networks are used. `frontend-network` connects the frontend and the backend. `backend-network` connects the backend and the database. It is marked `internal`, so the database cannot be reached from outside Docker.
- Only the frontend (port 5173) and the backend (port 8080) are published to the host.
- The database password and the Gmail credentials are Docker secrets, read from files in the `secrets/` folder. The backend reads them at start and passes them to Spring as environment variables.
- Non-secret settings (database address, public URL) are environment variables in the Compose file.
- The frontend container uses nginx. It serves the React build and forwards `/api/` calls to the backend. The API address is set at build time with `VITE_API_BASE_URL`.
- GitHub Actions builds the images, starts them with Compose, checks that the database, backend and frontend respond, and only then publishes the images to Docker Hub.

## Options considered
- One container for everything: simple, but harder to update and less safe.
- Passwords in a `.env` file: easy, but the file can be committed by mistake (this happened once).
- Docker secrets from files: the password is not in the image or in Git.

## Consequences
Good:
- The database is not exposed to the internet.
- Secrets are kept out of Git and out of the images.
- The same Compose file works on every computer.

Risks and limits:
- The `secrets/` folder must be in `.gitignore`. A secret committed by mistake must be changed at once.
- The CI pipeline creates placeholder secrets for its test run. They are not real.
- Plain Docker Compose secrets are files on one machine. A real deployment may need a secret manager.
- The JWT signing key and the default admin password are still in the source code (SECURITY_REVIEW.md, SR-01 and SR-02). They should also move to secrets.
- CI checks that the services respond, but there are no full API integration tests yet.

## Related
Code: `docker-compose.yml`, `essential-butterfly-backend/Dockerfile`, `frontend/Dockerfile`, `frontend/nginx.conf`, `.github/workflows/ci-cd.yml`.
