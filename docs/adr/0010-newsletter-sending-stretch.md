# ADR-0010: Newsletter with consent, unsubscribe token and Gmail sending

Status: Accepted
Date: 2026-11-03

## Context
Casey wants to send news to families who agreed to receive it. Subscriber emails are personal data, so consent and an easy unsubscribe are required. Sending email needs an account and secret keys.

## Decision
- A visitor subscribes with an email address and a consent tick. The email is saved in lower case, and the same email is not saved twice.
- Each subscriber gets a random unsubscribe token. Every newsletter email has an unsubscribe link that uses this token (`GET /api/newsletter/unsubscribe`).
- The admin writes a newsletter as a draft, can edit it and preview it. Only a draft can be sent.
- Emails are sent through Gmail SMTP. Each delivery is saved with its own status, so the admin can see which emails failed and retry only the failed ones.
- The Gmail address and app password are read from environment variables or Docker secrets, never from the code.
- The base URL used in unsubscribe links comes from `APP_PUBLIC_BASE_URL`.

## Options considered
- Build everything at once with a paid email service: more reliable, but costs money and needs more setup.
- Gmail SMTP with per-email delivery records: free, and good enough for a small list.

## Consequences
Good:
- Consent and unsubscribe are built in.
- A partial failure does not lose information: failed emails can be retried.
- Unsubscribing by token (not by email address alone) stops other people from unsubscribing someone else.

Risks and limits:
- Gmail has daily sending limits and may mark bulk email as spam. A real email service should be used if the list grows.
- If the Gmail secrets are missing, sending fails with a clear message, but the rest of the site works.

## Related
Code: `NewsletterService`, `NewsletterMailService`, `NewsletterController`. Use case: UC-08.
