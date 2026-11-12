# Essential Butterfly Support and Counselling

A website for Essential Butterfly Support and Counselling, a service run by Casey that supports neurodiverse individuals and families. Visitors can learn about the services, read updates, subscribe to the newsletter, contact Casey and submit an intake request. Clients can see their request status and share a testimonial. Casey signs in to a protected admin area.

This is a Sheridan College capstone project built by team **NATE Tech** (Nirali Patel, Tirth Shah, Ethan Chramow, Syed Ayan Ahmed) for a real client.

[![Tests](https://github.com/NiraliPatel1518/Essential-Butterfly-Support-and-Counselling/actions/workflows/tests.yml/badge.svg)](https://github.com/NiraliPatel1518/Essential-Butterfly-Support-and-Counselling/actions)

## Features

| Sprint | Use case | Status |
|---|---|---|
| 1 | Create account / log in | Done |
| 1 | Submit intake request / contact message | Done |
| 1 | Admin logs in securely | Done |
| 2 | View requests and their status (UC-04) | In progress |
| 2 | Admin controls public feature visibility (UC-05) | In progress |
| 2 | Admin publishes updates (UC-06) | In progress |
| 2 | Testimonials (UC-07) | In progress |
| 2 | Newsletter (UC-08) | In progress |

## Tech stack

- **Frontend:** React 19, TypeScript, Vite, react-router-dom, Vitest
- **Backend:** Java 17, Spring Boot, Spring Security (JWT), Spring Data JPA, Maven, JUnit 5
- **Database:** PostgreSQL 17 (Docker)
- **Delivery:** Docker Compose, GitHub Actions, Docker Hub

## Project structure

```
essential-butterfly-backend/   Spring Boot API and its Dockerfile
frontend/                      React application, Dockerfile and nginx settings
docker-compose.yml             Database, backend and frontend containers
docs/adr/                      Architecture Decision Records
docs/operations/               Operations runbook
SECURITY_REVIEW.md             Security review
TESTING.md                     Test plan and results
```

## Run it with Docker (recommended)

You need Docker.

1. Create a folder named `secrets` in the project root. It is ignored by Git, so never commit it. Put these six files in it (one value per file, no extra spaces):
   - `secrets/postgres_password.txt` : the database password
   - `secrets/gmail_username.txt` : the Gmail address used to send newsletters
   - `secrets/gmail_app_password.txt` : the Gmail app password
   - `secrets/jwt_secret.txt` : a long random secret used to sign login tokens (see below)
   - `secrets/admin_email.txt` : the email of the admin account (for local testing use any email you like)
   - `secrets/admin_password.txt` : the password of the admin account (choose your own, at least 12 characters)
2. Start everything:
   ```
   docker compose up -d --build
   ```
3. Open http://localhost:5173. The API runs on http://localhost:8080.

To create `jwt_secret.txt` on Windows (PowerShell), run this from the project root:

```powershell
$bytes = New-Object byte[] 64
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes) | Set-Content -Path secrets\jwt_secret.txt -NoNewline -Encoding ascii
```

On Mac or Linux, run `openssl rand -base64 64 | tr -d '\n' > secrets/jwt_secret.txt`.
Never commit this file, and never reuse a secret that was ever pushed to GitHub.

The admin account is created from `admin_email.txt` and `admin_password.txt` the first time the backend starts. If either file is empty, no admin account is created. Changing these files later does not change an admin that already exists in the database.

Newsletter email needs real Gmail values. Without them the rest of the site still works.

## Run it for development

You need Docker, Java 17 and Node.js.

1. Start only the database (it needs `secrets/postgres_password.txt`):
   ```
   docker compose up -d db
   ```
   The database is on a private Docker network, so for local development you can also run your own PostgreSQL and set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`.
2. Backend (the app needs the `JWT_SECRET` environment variable, or it will not start):
   ```
   cd essential-butterfly-backend
   export JWT_SECRET="your-long-random-secret"      # Mac/Linux
   $env:JWT_SECRET = "your-long-random-secret"      # Windows PowerShell
   export ADMIN_EMAIL="admin@example.com"           # Mac/Linux
   export ADMIN_PASSWORD="a-strong-password-here"   # Mac/Linux
   $env:ADMIN_EMAIL = "admin@example.com"           # Windows PowerShell
   $env:ADMIN_PASSWORD = "a-strong-password-here"   # Windows PowerShell
   ./mvnw spring-boot:run
   ```
3. Frontend:
   ```
   cd frontend
   npm install
   npm run dev
   ```
   The frontend reads the API address from `VITE_API_BASE_URL` (default `http://localhost:8080`).

More detail, restart steps and troubleshooting: [Operations runbook](docs/operations/runbook.md).

## Run the tests

```
cd essential-butterfly-backend && ./mvnw test
cd frontend && npm test
```

Test cases, tools and results are listed in [TESTING.md](TESTING.md). The backend integration tests need Docker Desktop running. All tests run automatically on GitHub Actions for every push and pull request.

## Documentation

- [Architecture Decision Records](docs/adr/) explain why we made key design choices.
- [Operations runbook](docs/operations/runbook.md) covers start, restart, recovery, troubleshooting and incident response.
- [Security review](SECURITY_REVIEW.md) lists the security controls and open findings.
- [Testing](TESTING.md) describes our automated and manual tests.

## Non-functional goals

- **Accessibility:** WCAG 2.1 Level AA, which also supports AODA requirements.
- **Security:** hashed passwords (BCrypt), JWT with roles, server-side validation, admin lockout, centralized error handling.
- **Efficiency:** multi-stage Docker build for a smaller backend image.

## Contributing

1. Create a branch from `main`.
2. Make your change and add or update tests.
3. Update the README, an ADR or the runbook if the change needs it.
4. Open a pull request and ask a teammate to review it.
