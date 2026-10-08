# Security Review – Sprint 1

**Project:** Essential Butterfly Support and Counselling
**Team:** NATE tech
**Scope:** UC-01 Create Account / Log In, UC-02 Submit Intake or Contact Message, UC-03 Admin Logs In Securely
**Code reviewed:** `main` branch, commit `42ca5d9` (October 7, 2026)
**Method:** Manual code review of the backend (Spring Boot) and frontend (React), plus the automated tests in [TESTING.md](TESTING.md)

## Summary

| Result | Count |
|---|---|
| Security controls working well | 12 |
| Critical findings | 2 |
| Medium findings | 4 |
| Low findings | 3 |
| Notes for deployment | 2 |

The main security features are in place and tested: hashed passwords, role-based admin access, admin lockout and safe password reset tokens.

The two critical findings are about **secrets in the code**. Because the GitHub repository is **public**, anyone can read them. They should be fixed before the website goes live.

## What is working well

| # | Control | Where | Proof |
|---|---|---|---|
| 1 | Passwords are hashed with BCrypt. Plain text passwords are never saved. | `SecurityConfig.passwordEncoder()`, `AuthService.signup()` | Test BE-AUTH-01 |
| 2 | Login uses a signed JWT token. A changed or fake token is rejected. | `JwtService`, `SecurityConfig.jwtDecoder()` | Tests BE-JWT-03, BE-JWT-04 |
| 3 | Tokens have a role (`CLIENT` or `ADMIN`). Only `ADMIN` can use `/api/admin/**`. | `SecurityConfig` | Test BE-JWT-07 |
| 4 | Admin accounts are stored in a separate table from client accounts. | `AdminUser`, `ClientUser` | Code review |
| 5 | Admin account locks for 15 minutes after 5 wrong passwords. | `AdminAuthService` | Tests BE-ADMAUTH-04 to 10, API-ADMAUTH-04 |
| 6 | Password reset tokens are random (UUID), expire after 30 minutes and work only once. | `PasswordResetService` | Tests BE-PWD-01, BE-PWD-08 to 10 |
| 7 | "Forgot password" gives the same message whether the email exists or not, so attackers cannot find out who has an account. | `PasswordResetController` | Test API-PWD-04 |
| 8 | The intake form takes the user's email from the login token, not from the form, so a user cannot submit for someone else. | `IntakeSubmissionController` | Test API-INTAKE-01 |
| 9 | All forms are checked on the server (required fields, email format, password length). | DTO classes with `@Valid` | Tests in `EmailFormatValidationTest`, `AuthControllerTest` |
| 10 | Users are logged out after 30 minutes with no activity. | `SessionTimeout.tsx` | Tests FE-SESSION-04 to 07 |
| 11 | Database queries use JPA, which protects against SQL injection. React escapes text on the page, which protects against most XSS. | Repositories, React components | Code review |
| 12 | Database password and `application.properties` are not in GitHub. | `.gitignore`, `docker-compose.yml` uses `${POSTGRES_PASSWORD}` | Code review |

## Findings

Severity levels: **Critical** = fix before going live, **Medium** = fix in Sprint 2, **Low** = nice to fix.

### SR-01 – JWT secret key is written in the code (Critical)

- **Where:** `SecurityConfig.java` line 30 and `JwtService.java` line 15 (the same secret is copied in two places).
- **Risk:** The repository is public. Anyone who reads the secret can create their own token with `role: ADMIN` and get full admin access.
- **Fix:**
  1. Read the secret from an environment variable, for example `JWT_SECRET`, in one place only.
  2. Use a new long random secret (at least 64 characters).
  3. Never put the real secret in GitHub.

### SR-02 – Default admin password is written in the code (Critical)

- **Where:** `AdminDataInitializer.java` creates `admin@test.com` with password `Admin@12345`.
- **Risk:** The repository is public, so anyone can log in as the admin on any server that runs this code.
- **Fix:** Read the admin email and password from environment variables. Use a strong password. Ask the admin to change it on first login (later sprint).

### SR-03 – No lockout or limit on client login and forgot password (Medium)

- **Where:** `/api/auth/login`, `/api/password/forgot`
- **Risk:** Admin login is protected by a lockout, but client login is not. An attacker can try many passwords. The forgot password endpoint can also be called again and again.
- **Fix:** Add the same lockout to client accounts, or add rate limiting (for example, 5 tries per minute per IP address).

### SR-04 – Login token is stored in localStorage (Medium)

- **Where:** `Login.tsx`, `AdminLogin.tsx`
- **Risk:** If an XSS bug is ever added, a script could read the token. React lowers this risk, but does not remove it.
- **Fix (later sprint):** Store the token in an `HttpOnly` cookie, or keep the token life short (see SR-09).

### SR-05 – Password reset: link is printed in the server console and old links stay valid (Medium)

- **Where:** `PasswordResetService.createResetToken()`
- **Risk:**
  - The reset link is printed with `System.out.println`. Anyone who can see server logs can reset any password. Real users never receive the link.
  - When a user asks for a new link, older links still work until they expire.
  - After a password reset, old login tokens still work for up to 24 hours.
- **Fix:** Send the link by email (for example, Spring Mail). Remove the console output. Mark older tokens as used when a new one is created.

### SR-06 – Login token uses the email exactly as typed (Medium)

- **Where:** `AuthController.login()` uses `request.getEmail()` without `trim().toLowerCase()`.
- **Risk:** A user who logs in as `Jane@Mail.com` gets a token for `Jane@Mail.com`, but the account is saved as `jane@mail.com`. The intake form then fails with "User account not found". It also means one person can have tokens with different email spellings.
- **Fix:** Use `request.getEmail().trim().toLowerCase()`. Then remove `@Disabled` from test API-AUTH-09.

### SR-07 – Signup tells you if an email already has an account (Low)

- **Where:** `AuthService.signup()` returns "An account with this email already exists".
- **Risk:** Someone can check which emails are registered. Forgot password already hides this, so the two are not consistent.
- **Fix:** This is common and helpful for real users, so the team can accept this risk. If not, show a general message and send an email instead.

### SR-08 – Weak password rule (Low)

- **Where:** `SignupRequest`, `ResetPasswordRequest`
- **Risk:** The only rule is "at least 8 characters", so `password` is allowed.
- **Fix:** Require at least one letter and one number, or check against a list of common passwords.

### SR-09 – Token lasts 24 hours on the server (Low)

- **Where:** `JwtService.generateToken()`
- **Risk:** The website logs users out after 30 minutes of no activity, but the token itself still works for 24 hours if someone copies it.
- **Fix:** Shorten the token life (for example, 1 hour) or add refresh tokens later.

## Notes for deployment (later sprint)

- **N-01 – HTTPS:** The site runs on `http://localhost`. When it goes live it must use HTTPS so passwords and tokens are encrypted.
- **N-02 – Hardcoded addresses:** `http://localhost:8080` is written in 11 frontend files, and CORS only allows `http://localhost:5173`. Move these to environment variables before deployment.

## Sprint 1 checklist (Trello)

| Trello item | Result |
|---|---|
| UC-01: Security review (password storage, session security) | ✅ Reviewed. Password storage is safe (BCrypt). Session security works (JWT + role + 30 min timeout). Open items: SR-01, SR-03, SR-04, SR-06, SR-09 |
| UC-03: Security review (admin role protection) | ✅ Reviewed. Role check and admin lockout work and are tested. Open items: SR-01, SR-02 |

## Recommended order of fixes

| Priority | Finding | Time needed |
|---|---|---|
| 1 | SR-06 Email in login token | 5 minutes |
| 2 | SR-01 JWT secret to environment variable | 30 minutes |
| 3 | SR-02 Admin password to environment variable | 20 minutes |
| 4 | SR-05 Send reset link by email | 2 to 3 hours |
| 5 | SR-03 Client login lockout | 1 hour |
| 6 | SR-08, SR-09, SR-04 | Later sprint |
