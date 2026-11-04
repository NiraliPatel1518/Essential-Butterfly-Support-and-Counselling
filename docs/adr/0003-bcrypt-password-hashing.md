# ADR-0003: BCrypt for password storage

Status: Accepted
Date: 2026-11-03

## Context
Client and admin passwords must never be stored in a readable form. If the database leaks, passwords must still be hard to recover.

## Decision
Passwords are hashed with Spring Security's `BCryptPasswordEncoder` when an account is created or a password is reset. At login the entered password is compared to the stored hash with `matches()`.

## Options considered
- Plain text or a fast hash (such as SHA-256): easy to attack with guessing.
- BCrypt: slow on purpose, adds a random salt, and is built into Spring Security.

## Consequences
Good:
- Stored passwords are not readable and each hash is different, even for equal passwords.
- No extra library is needed.

Risks and limits:
- Hashing cannot protect a weak password. We require at least 8 characters.
- BCrypt uses the library's default work factor. It can be raised later if servers get faster.

## Related
Code: `SecurityConfig.passwordEncoder()`, `AuthService.signup()`.
