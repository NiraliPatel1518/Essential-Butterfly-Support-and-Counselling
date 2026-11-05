# ADR-0012: Centralized error handling

Status: Accepted
Date: 2026-11-03

## Context
Controllers used to build their own error answers, and unexpected errors could show internal details to the user.

## Decision
A single `@RestControllerAdvice` class (`GlobalExceptionHandler`) turns exceptions into one JSON shape: `status`, `error`, `message`. It maps bad input to 400, conflicts and a locked admin account to 409 or 423, and logs unexpected errors on the server. A last handler catches any other exception and returns a general message without details.

## Options considered
- try/catch in each controller: repeated code, different answers.
- One handler: same shape everywhere, one place to change.

## Consequences
Good:
- The frontend can rely on one error format.
- Users do not see stack traces.

Risks and limits:
- Some newer services (newsletter, updates) throw Spring's `ResponseStatusException`. The handler does not map it yet, so those errors can have a slightly different JSON shape. A handler for it should be added so every error looks the same.
- Every new controller must throw the shared exception types, or the answer will not match this format.

## Related
Code: `GlobalExceptionHandler`.
