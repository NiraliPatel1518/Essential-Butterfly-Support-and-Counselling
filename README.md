# Essential Butterfly Support and Counselling

A website for Essential Butterfly Support and Counselling, a service run by Casey that supports neurodiverse individuals and families. Visitors can learn about the services, contact Casey and submit an intake request. Casey signs in to a protected admin area.

This is a Sheridan College capstone project built by team **NATE Tech** (Nirali Patel, Tirth Shah, Ethan Chramow, Syed Ayan Ahmed) for a real client.

[![Tests](https://github.com/NiraliPatel1518/Essential-Butterfly-Support-and-Counselling/actions/workflows/tests.yml/badge.svg)](https://github.com/NiraliPatel1518/Essential-Butterfly-Support-and-Counselling/actions)

## Features

| Sprint | Use case | Status |
|---|---|---|
| 1 | Create account / log in | Done |
| 1 | Submit intake request / contact message | Done |
| 1 | Admin logs in securely | Done |
| 2 | View requests and their status (UC-04) | Planned |
| 2 | Admin controls public feature visibility (UC-05) | Planned |
| 2 | Admin publishes updates (UC-06) | Planned |
| 2 | Testimonials (UC-07) | Planned |
| 2 | Newsletter (UC-08) | Planned |

## Tech stack

- **Frontend:** React, TypeScript, Vite, react-router-dom, Vitest
- **Backend:** Java 17, Spring Boot, Spring Security (JWT), Spring Data JPA, Maven, JUnit 5
- **Database:** PostgreSQL 17 (Docker)
- **Delivery:** Docker, GitHub Actions, Docker Hub

## Project structure

```
backend/     Spring Boot API, Dockerfile, docker-compose.yml (database)
frontend/    React application
docs/adr/    Architecture Decision Records
docs/operations/  Operations runbook
SECURITY_REVIEW.md  Sprint 1 security review
TESTING.md          Test plan and results
```

## Run it locally

You need Docker, Java 17 and Node.js.

1. **Database**
   ```
   cd backend
   docker compose up -d
   ```
   Set the database password in your environment first. Do not commit real passwords.
2. **Backend**
   ```
   cd backend
   ./mvnw spring-boot:run
   ```
   The API runs on http://localhost:8080.
3. **Frontend**
   ```
   cd frontend
   npm install
   npm run dev
   ```
   Open http://localhost:5173.

More detail, restart steps and troubleshooting: [Operations runbook](docs/operations/runbook.md).

## Run the tests

```
cd backend && ./mvnw test
cd frontend && npm test
```

Test cases, tools and results are listed in [TESTING.md](TESTING.md). Tests also run automatically on GitHub Actions for every push and pull request.

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
