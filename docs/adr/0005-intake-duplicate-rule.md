# ADR-0005: Block a second intake while the first is still NEW

Status: Accepted
Date: 2026-11-03

## Context
A family could press Submit twice, or send the intake form again before Casey has looked at the first one. This would create duplicate records for Casey to clean up.

## Decision
When a logged-in client submits an intake, the backend checks if that client already has an intake with status `NEW` (`existsByClientUserAndStatus`). If yes, the new one is refused. Once the admin changes the status, the client can submit again.

## Options considered
- No check: simple, but creates duplicates.
- Compare form text: fragile, because small changes defeat it.
- One open request per client account: easy to explain and test.

## Consequences
Good:
- No duplicate open requests per client.
- The rule uses the account taken from the login token, not from the form.

Risks and limits:
- The check is per account, not per email address or phone number. A person with two accounts could submit twice.
- The client stays blocked until an admin changes the status. This is why Sprint 2 (UC-04) adds a way for Casey to change the status.
- The contact form has no duplicate check.

## Related
Code: `IntakeSubmissionService`, `IntakeSubmissionRepository`. Sprint 2: UC-04.
