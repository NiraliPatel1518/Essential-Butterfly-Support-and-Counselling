# ADR-0005: Block a second intake while the first is still open

Status: Accepted
Date: 2026-11-03

## Context
A family could press Submit twice, or send the intake form again before Casey has looked at the first one. This would create duplicate records for Casey to clean up.

## Decision
- A new intake has the status `SUBMITTED`. When Casey opens it, it becomes `IN_PROGRESS`. She then accepts it (`ACCEPTED`) or rejects it with a reason (`REJECTED`).
- When a logged-in client submits an intake, the backend checks if that client already has an intake with status `SUBMITTED`, `IN_PROGRESS` or the older value `NEW`. If yes, the new one is refused with a clear message.
- After the intake is accepted or rejected, the client can submit again.
- The client sees only the status and, if rejected, the reason. They do not see Casey's other notes.

## Options considered
- No check: simple, but creates duplicates.
- Compare form text: fragile, because small changes defeat it.
- One open request per client account: easy to explain and test.

## Consequences
Good:
- No duplicate open requests per client.
- The rule uses the account taken from the login token, not from the form.
- A rejected client sees why and can send a better request.

Risks and limits:
- The check is per account, not per email address or phone number. A person with two accounts could submit twice.
- The client stays blocked until Casey accepts or rejects the request.
- The contact form has no duplicate check.
- `NEW` is kept only for old records. It is shown to clients as `SUBMITTED`.

## Related
Code: `IntakeSubmissionService`, `IntakeSubmissionRepository`, `AdminIntakeSubmissionController`. Use case: UC-04.
