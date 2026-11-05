# ADR-0002: Stateless JWT authentication with roles

Status: Accepted
Date: 2026-11-03

## Context
Visitors (clients) and the admin (Casey) must log in. The backend should not keep login sessions in memory, so it can be restarted or run in more than one container.

## Decision
- After a successful login the backend returns a signed JWT.
- The token holds the user's email and a role (`CLIENT` or `ADMIN`). It is valid for 24 hours.
- The browser sends it as `Authorization: Bearer <token>`.
- Spring Security checks the signature on every request. Only the `ADMIN` role can use `/api/admin/**`.
- The frontend also logs the user out after 30 minutes without activity.

## Options considered
- Server sessions: simple, but the server must store them and share them between containers.
- JWT: nothing stored on the server. A changed or fake token is rejected.

## Consequences
Good:
- Backend stays stateless.
- The role inside the token lets one rule protect all admin URLs.
- Tokens cannot be forged without the signing secret.

Risks and limits:
- A token stays valid until it expires. There is no server-side logout.
- The signing secret must never be in the code. Today it is still in the source (security review SR-01) and must move to an environment variable or Docker secret.
- Tokens are stored in the browser (`localStorage`). A script injected into the page could read them, so we escape all output and avoid HTML from users (see ADR-0009).

## Related
Code: `JwtService`, `SecurityConfig`, `SessionTimeout.tsx`. Review: `SECURITY_REVIEW.md`.
