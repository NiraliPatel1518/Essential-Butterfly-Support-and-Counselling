# ADR-0010: Newsletter: subscribe and unsubscribe first, email sending second

Status: Proposed
Date: 2026-11-03

## Context
Sending real email needs an SMTP account, secret keys and a safe way to pass them to Docker. This could block the sprint. Subscriber emails are also personal data.

## Decision
Must have: subscribe with consent, unsubscribe with a token, active recipient count, saving each newsletter. Stretch goal: send the email through SMTP. If sending is not ready, the newsletter is saved with status `PENDING`. SMTP keys are read from environment variables or secrets, never from the code. Every email carries an unsubscribe link.

## Options considered
- Build everything at once: risks an unfinished feature.
- Build the data flow first and add sending last: always something working to demo.

## Consequences
Good:
- A working, testable feature even without email delivery.
- Consent and unsubscribe are built in from the start.

Risks and limits:
- Until SMTP works, no real email leaves the system.
- Unsubscribing by token (not by email address alone) stops other people from unsubscribing someone else.

## Related
Sprint 2: UC-08.
