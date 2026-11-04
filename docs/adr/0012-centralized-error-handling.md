# ADR-0012: Centralized error handling

Status: Accepted
Date: 2026-11-03

## Context
Controllers used to build their own error answers, and unexpected errors could show internal details to the user.

## Decision
A single `@RestControllerAdvice` class (`GlobalExceptionHandler`) turns exceptions into one JSON shape: `status`, `error`, `message`. It maps bad input to 400, conflicts to 409 and a locked admin to 423, and logs unexpected errors on the server.

## Options considered
- try/catch in each controller: repeated code, different answers.
- One handler: same shape everywhere, one place to change.

## Consequences
Good:
- The frontend can rely on one error format.
- Users do not see stack traces.

Risks and limits:
- Every new controller must throw the shared exception types, or the answer will not match this format.

## Related
Code: `GlobalExceptionHandler`.
