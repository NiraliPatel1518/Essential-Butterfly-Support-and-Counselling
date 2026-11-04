# ADR-0006: Contact form is open to everyone

Status: Accepted
Date: 2026-11-03

## Context
Casey wants anyone to be able to send a short question without creating an account. The intake form holds more detail, so it needs a login.

## Decision
`POST /api/contact` is public. The intake form and everything else that is personal need a login. Contact messages are saved with status `NEW` and are not linked to an account.

## Options considered
- Require login for contact: fewer spam messages, but fewer families will write.
- Public contact form: easy for visitors, with more risk of spam.

## Consequences
Good:
- No barrier for first contact.
- Server-side checks still run on every field, including email format.

Risks and limits:
- Spam is possible. Rate limiting or a captcha is not built yet.
- A contact message has no owner, so a client cannot see it later in My Requests.
- The `client_user_id` column exists but stays empty.

## Related
Code: `ContactMessageController`, `SecurityConfig`.
