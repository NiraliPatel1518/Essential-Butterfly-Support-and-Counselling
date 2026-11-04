# ADR-0009: Website updates are plain text

Status: Proposed
Date: 2026-11-03

## Context
In Sprint 2 Casey will publish updates that show on the public site. A rich-text editor lets users send HTML, and HTML shown on a public page can run scripts (cross-site scripting).

## Decision
Update content is stored and shown as plain text with line breaks. No HTML is accepted or rendered. React shows the text as text.

## Options considered
- Rich-text editor with a sanitiser: nicer formatting, but more code to build and test.
- Plain text: simple and safe, with limited formatting.

## Consequences
Good:
- Removes a whole class of script-injection problems.
- Less to build and test in the sprint.

Risks and limits:
- No bold, links or images inside an update. If Casey needs them later, we must add a sanitiser and write a new ADR.

## Related
Sprint 2: UC-06.
