# ADR-0007: All admin endpoints live under /api/admin

Status: Accepted
Date: 2026-11-03

## Context
The admin area must be protected so a normal client account can never change data that only Casey should manage.

## Decision
One Spring Security rule protects every URL that starts with `/api/admin/` and requires the `ADMIN` role. Every new admin feature must use this prefix (for example `/api/admin/intake-submissions`, `/api/admin/updates`, `/api/admin/testimonials`, `/api/admin/newsletter`, `/api/admin/features`). Admin accounts are stored in their own table, separate from client accounts.

## Options considered
- Add a role check inside every controller method: easy to forget one.
- One URL rule for the whole prefix: new admin endpoints are protected without extra code.

## Consequences
Good:
- A client token gets 403 on any admin URL.
- Easy to review: one rule to check.

Risks and limits:
- An admin action placed on another URL would only need a login, not the admin role. Reviewers must check the prefix in every pull request.
- The rule only protects the backend. The frontend also needs route guards for a good user experience.

## Related
Code: `SecurityConfig`.
