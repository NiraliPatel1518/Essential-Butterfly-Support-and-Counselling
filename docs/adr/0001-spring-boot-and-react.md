# ADR-0001: Spring Boot backend and React frontend

Status: Accepted
Date: 2026-11-03

## Context
The Essential Butterfly website needs public pages, forms, user accounts, an admin area and a database. The frontend and backend should be separate so each can be built, tested and deployed on its own.

## Decision
- Backend: Java 17, Spring Boot, Spring Security, Spring Data JPA, built with Maven.
- Frontend: React with TypeScript, built with Vite, routing with react-router-dom.
- Database: PostgreSQL (see ADR-0004).
- The frontend talks to the backend with JSON over HTTP (REST).

## Options considered
- Server-rendered pages (for example Thymeleaf): fewer parts, but the interface is harder to test and to make accessible in small pieces.
- React + Spring Boot REST API: two parts to run, but each part is tested alone (Vitest on the frontend, JUnit on the backend).

## Consequences
Good:
- Clear split: the backend owns data and rules, the frontend owns screens.
- Spring Security gives tested building blocks for login, JWT and roles.
- Spring Data JPA protects database queries from SQL injection (see SECURITY_REVIEW.md).

Risks and limits:
- Two projects must be started and deployed together. Docker Compose does this (ADR-0013).
- The frontend must know the backend address. It is read from the `VITE_API_BASE_URL` setting at build time, with `http://localhost:8080` as the default.
- CORS must be configured so the browser can call the backend. Today only `http://localhost:5173` is allowed, so a real domain must be added before going live.

## Related
Code: `essential-butterfly-backend/pom.xml`, `frontend/package.json`.
