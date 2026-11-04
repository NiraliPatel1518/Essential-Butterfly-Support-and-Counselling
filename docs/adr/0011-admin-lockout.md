# ADR-0011: Admin account locks after repeated wrong passwords

Status: Accepted
Date: 2026-11-03

## Context
The admin login can be attacked by guessing passwords. The admin account controls all client data.

## Decision
After 5 wrong passwords the admin account is locked for 15 minutes. While it is locked the backend answers HTTP 423 with a clear message. The admin can use the password reset to get back in.

## Options considered
- No limit: easy to guess passwords by trial.
- Lock the account: stops guessing, but an attacker can also lock Casey out for a while.

## Consequences
Good:
- Brute-force guessing is slow and noisy.
- Tests cover the lockout and unlock.

Risks and limits:
- Someone can lock the admin account on purpose. Password reset is the way out.
- Client login has no lockout yet (security review SR-03).

## Related
Code: `AdminAuthService`. Review: `SECURITY_REVIEW.md`.
