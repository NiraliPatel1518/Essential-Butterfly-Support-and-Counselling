# ADR-0009: Website updates are plain text, with limited photos and links

Status: Accepted
Date: 2026-11-03

## Context
Casey publishes updates that show on the public site. A rich-text editor lets users send HTML, and HTML shown on a public page can run scripts (cross-site scripting). Casey also wants photos and links to social media in an update.

## Decision
- The title (up to 180 characters) and the content (up to 20,000 characters) are stored and shown as plain text. No HTML is accepted or rendered. React shows the text as text.
- An update can have up to 5 photos. Each photo must be JPEG, PNG or WebP and under 2 MB. Photos are stored in the database as data URLs.
- An update can have social links. Each link must start with `http://` or `https://`.
- An update is either published or a draft. Only published updates appear on the public site.
- The list of updates is public. Opening the full update needs a valid login.

## Options considered
- Rich-text editor with a sanitiser: nicer formatting, but more code to build and test.
- Plain text with checked photos and links: simple and safe, with limited formatting.

## Consequences
Good:
- Removes a whole class of script-injection problems.
- File type and size are checked on the server, not only in the browser.

Risks and limits:
- No bold or inline links inside the text. If Casey needs them later, we must add a sanitiser and write a new ADR.
- Photos stored in the database make it grow fast. File storage (for example a folder volume or cloud storage) should replace this if there are many photos.
- Social links are only checked for `http` or `https`. They are not checked to be safe sites.

## Related
Code: `WebsiteUpdateService`, `WebsiteUpdateRequest`, `WebsiteUpdateController`. Use case: UC-06.
