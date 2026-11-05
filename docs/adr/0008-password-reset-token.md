# ADR-0008: Password reset with a one-time token

Status: Accepted
Date: 2026-11-03

## Context
Users and the admin can forget their password. The reset must be safe and must not tell attackers whether an email has an account.

## Decision
- A random UUID token is stored in the database with an expiry of 30 minutes and a `used` flag.
- A token works once.
- The forgot-password answer is always the same, whether or not the email exists.
- The new password is hashed with BCrypt.
- For now the reset link is written to the server console. Sending real email is not built.

## Options considered
- Security questions: weak and easy to guess.
- Emailed one-time link: standard approach, but needs an email service.
- One-time token with console output: works for a demo and keeps the safe design.

## Consequences
Good:
- Old or used links are refused.
- The same answer for every email prevents guessing who has an account.

Risks and limits:
- Without an email service, a real user cannot receive the link. This must be added before going live.
- The reset flow is also the way to unlock an admin account (see ADR-0011).

## Related
Code: `PasswordResetService`, `PasswordResetController`.
