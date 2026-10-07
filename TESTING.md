# Testing – Essential Butterfly Support and Counselling

![Tests](https://github.com/NiraliPatel1518/Essential-Butterfly-Support-and-Counselling/actions/workflows/tests.yml/badge.svg)

This document describes how we test the Essential Butterfly website for Sprint 1.
It lists our testing tools, our test cases, and where to see live test runs.

## Summary

| Area | Tool | Number of tests | Result |
|---|---|---|---|
| Frontend (React pages and components) | Vitest + React Testing Library | 46 | 46 pass |
| Backend unit tests (services, JWT) | JUnit 5 + Mockito | 39 | 39 pass |
| Backend API tests (controllers) | JUnit 5 + MockMvc | 29 | 28 pass, 1 skipped (known bug) |
| **Total automated tests** | | **114** | **113 pass, 1 skipped** |
| Mobile layout (manual) | Chrome DevTools + Playwright screenshots | 12 pages x 3 screen sizes | See "Manual testing" |

## Testing tools

| Tool | What we use it for |
|---|---|
| **Vitest** | Runs the frontend tests. |
| **React Testing Library** | Opens a page in a fake browser, types into forms and clicks buttons like a real user. |
| **user-event** | Simulates real typing and clicking. |
| **jsdom** | A fake browser, so frontend tests run without opening Chrome. |
| **JUnit 5** | Runs the backend (Java) tests. |
| **Mockito** | Creates fake versions of the database and other services, so each class is tested on its own. |
| **MockMvc** | Sends fake HTTP requests to our API controllers and checks the status code and response. |
| **GitHub Actions** | Runs every test automatically on each push and pull request. |
| **Chrome DevTools / Playwright** | Manual and screenshot-based mobile layout testing. |

## How we test (test levels)

1. **Unit tests (backend services):** test one class at a time. The database is replaced with a fake (mock), so tests are fast and do not need PostgreSQL.
2. **API tests (backend controllers):** send a fake HTTP request to an endpoint and check the status code (200, 201, 400, 401, 423) and message.
3. **Frontend component tests:** render one page, fill in the form like a user, and check what the user sees. The backend is replaced with a fake `fetch`, so the backend does not need to be running.
4. **Manual testing:** check the layout on phone and tablet sizes.

Every test follows the same pattern: **Arrange** (set up data), **Act** (do the action), **Assert** (check the result).

## How to run the tests

### Frontend

```bash
cd frontend
npm install
npm test
```

### Backend

Needs Java 17 or newer. No database is needed.

```bash
cd backend
./mvnw test "-Dtest=*ServiceTest,*ControllerTest"
```

On Windows use `.\mvnw.cmd` instead of `./mvnw`.

Note: the default `contextLoads` test starts the whole app and needs a running PostgreSQL database, so it is not part of this command.

## Live test runs (GitHub Actions)

Tests run automatically on GitHub every time someone pushes code or opens a pull request.

**How to see them:**

1. Open the repository on GitHub.
2. Click the **Actions** tab.
3. Click the latest **Tests** run.
4. Green tick = all tests passed. Red cross = a test failed.
5. Scroll down to **Frontend Test Results** and **Backend Test Results** to see every single test by name.

The badge at the top of this file always shows the result of the latest run.

The workflow file is `.github/workflows/tests.yml`.

## Test cases

Test IDs:
- `FE-...` = frontend test
- `BE-...` = backend unit test
- `API-...` = backend API (controller) test

Each test file has a comment above every test that explains what it checks.

### UC-01: Create Account / Log In (28 tests)

| ID | Level | Test case | Expected result | Status |
|---|---|---|---|---|
| FE-SIGNUP-01 | Frontend | Correct request is sent | The request goes to /api/auth/signup using POST; Full name, email, password and confirm password are all sent | ✅ Pass |
| FE-SIGNUP-02 | Frontend | Successful signup | A success message is shown; The user is sent to /login (after a short delay) | ✅ Pass |
| FE-SIGNUP-03 | Frontend | Passwords do not match | The message "Passwords do not match." is shown; No request is sent to the backend | ✅ Pass |
| FE-SIGNUP-04 | Frontend | Email already registered | The error message from the backend is shown | ✅ Pass |
| FE-SIGNUP-05 | Frontend | Backend is not running | A friendly "Unable to connect to the server" message is shown | ✅ Pass |
| FE-LOGIN-01 | Frontend | Successful client login | The request goes to the client login API; The token from the backend is saved as "authToken"; The user is sent to /client-dashboard | ✅ Pass |
| FE-LOGIN-02 | Frontend | Wrong email or password | The error message from the backend is shown; No token is saved | ✅ Pass |
| FE-LOGIN-03 | Frontend | Backend is not running | A friendly "Unable to connect to the server" message is shown | ✅ Pass |
| FE-LOGIN-04 | Frontend | Forgot password link | The "Forgot password" link points to /forgot-password | ✅ Pass |
| FE-LOGIN-05 | Frontend | Successful admin login | The request goes to the admin login API; The token is saved as "adminToken" (NOT as "authToken"); The admin is sent to /admin-dashboard | ✅ Pass |
| FE-LOGIN-06 | Frontend | Wrong admin email or password | The error message from the backend is shown; No admin token is saved | ✅ Pass |
| FE-LOGIN-07 | Frontend | Admin forgot password link | The "Forgot password" link points to /admin/forgot-password | ✅ Pass |
| BE-AUTH-01 | Backend unit | Valid signup | Extra spaces are removed from the name and email; The email is saved in lowercase; The password is saved in hashed form, never as plain text | ✅ Pass |
| BE-AUTH-02 | Backend unit | Email already exists | The error "An account with this email already exists" is thrown; No user is saved | ✅ Pass |
| BE-AUTH-03 | Backend unit | Duplicate check ignores email case | "JANE@MAIL.COM" is checked as "jane@mail.com"; So the same person cannot sign up twice using capital letters | ✅ Pass |
| BE-AUTH-04 | Backend unit | Passwords do not match | The error "Passwords do not match" is thrown; No user is saved and the password is not hashed | ✅ Pass |
| BE-AUTH-05 | Backend unit | Correct login | Login returns true when email and password are correct; Extra spaces and capital letters in the email do not matter | ✅ Pass |
| BE-AUTH-06 | Backend unit | Wrong password | Login returns false when the password does not match | ✅ Pass |
| BE-AUTH-07 | Backend unit | Email not registered | Login returns false when no account has this email; The password is not even checked | ✅ Pass |
| API-AUTH-01 | Backend API | Valid signup | The response is 201 Created with "Account created successfully"; The signup service is called | ✅ Pass |
| API-AUTH-02 | Backend API | Email already exists | The response is 400 Bad Request; The error message from the service is sent back | ✅ Pass |
| API-AUTH-03 | Backend API | Wrong email format | The response is 400 Bad Request; The service is never called (blocked by validation) | ✅ Pass |
| API-AUTH-04 | Backend API | Password too short | The response is 400 Bad Request; The service is never called (blocked by validation) | ✅ Pass |
| API-AUTH-05 | Backend API | Full name is empty | The response is 400 Bad Request | ✅ Pass |
| API-AUTH-06 | Backend API | Successful login | The response is 200 OK; The login token is sent back in the response; The token is made with the role "CLIENT" | ✅ Pass |
| API-AUTH-07 | Backend API | Wrong login details | The response is 401 Unauthorized with "Invalid email or password"; No token is created | ✅ Pass |
| API-AUTH-08 | Backend API | Password is empty | The response is 400 Bad Request | ✅ Pass |
| API-AUTH-09 | Backend API | Token should use lowercase email | The login token is created with the lowercase email | ⏸️ Skipped (known bug) |

### UC-02: Submit Intake Request or Contact Message (21 tests)

| ID | Level | Test case | Expected result | Status |
|---|---|---|---|---|
| FE-INTAKE-01 | Frontend | User is not logged in | The message "Please log in before submitting the intake form." is shown; No request is sent to the backend | ✅ Pass |
| FE-INTAKE-02 | Frontend | Successful submission | The request goes to the correct API URL; The login token is sent in the Authorization header; The form data is sent correctly; A success message is shown; The form is cleared after submission | ✅ Pass |
| FE-INTAKE-03 | Frontend | Backend returns an error | The error message from the backend is shown to the user | ✅ Pass |
| FE-INTAKE-04 | Frontend | Required fields are empty | The form is not sent when the user clicks submit without filling it in | ✅ Pass |
| FE-CONTACT-01 | Frontend | Correct request is sent | The request goes to /api/contact using POST; Name, email, subject and message are all sent | ✅ Pass |
| FE-CONTACT-02 | Frontend | Successful send | The thank you message is shown; The form is empty again | ✅ Pass |
| FE-CONTACT-03 | Frontend | Backend returns an error | The error message from the backend is shown | ✅ Pass |
| FE-CONTACT-04 | Frontend | Backend is not running | An error message is shown instead of a thank you message | ✅ Pass |
| FE-CONTACT-05 | Frontend | Required fields are empty | Nothing is sent when the user clicks "Send Message" with an empty form | ✅ Pass |
| BE-INTAKE-01 | Backend unit | Successful intake submission | The submission is linked to the logged-in user; All form fields are saved correctly; The status is "NEW" and the created date is set | ✅ Pass |
| BE-INTAKE-02 | Backend unit | User account not found | The error "User account not found" is thrown; No submission is saved | ✅ Pass |
| BE-CONTACT-01 | Backend unit | Message is saved correctly | Extra spaces are removed from all fields; The email is saved in lowercase; The status is "NEW" and the created date is set | ✅ Pass |
| BE-CONTACT-02 | Backend unit | Public visitor (not logged in) | The message is saved without a linked user account | ✅ Pass |
| API-INTAKE-01 | Backend API | Successful intake submission | The response is 200 with "Intake submission received successfully"; The user's email is taken from the login token, not from the form | ✅ Pass |
| API-INTAKE-02 | Backend API | Required fields are empty | The response is 400 Bad Request; The service is never called (blocked by validation) | ✅ Pass |
| API-INTAKE-03 | Backend API | User account not found | The response is 400 with "User account not found"; Example: an admin token is used, but admins are not client accounts | ✅ Pass |
| API-CONTACT-01 | Backend API | Valid contact message | Valid contact message | ✅ Pass |
| API-CONTACT-02 | Backend API | Email format is wrong | Email format is wrong | ✅ Pass |
| API-CONTACT-03 | Backend API | Subject is empty | Subject is empty | ✅ Pass |
| API-CONTACT-04 | Backend API | Message is empty | Message is empty | ✅ Pass |
| API-CONTACT-05 | Backend API | Service throws an error | Service throws an error | ✅ Pass |

### UC-03: Admin Logs In Securely (19 tests)

| ID | Level | Test case | Expected result | Status |
|---|---|---|---|---|
| FE-ADMPWD-01 | Frontend | Reset link has no token | The message "This password reset link is invalid." is shown; No request is sent to the backend | ✅ Pass |
| FE-ADMPWD-02 | Frontend | Password is too short | The message "Password must be at least 8 characters." is shown; No request is sent to the backend | ✅ Pass |
| FE-ADMPWD-03 | Frontend | Passwords do not match | The message "Passwords do not match." is shown; No request is sent to the backend | ✅ Pass |
| FE-ADMPWD-04 | Frontend | Successful admin password reset | The token from the link is sent to the backend with the new password; The admin success message is shown | ✅ Pass |
| FE-ADMPWD-05 | Frontend | Token is expired or already used | The error message from the backend is shown | ✅ Pass |
| BE-ADMAUTH-01 | Backend unit | Correct admin login | The admin account is returned when email and password are correct; Extra spaces and capital letters in the email do not matter | ✅ Pass |
| BE-ADMAUTH-02 | Backend unit | Wrong admin password | Nothing (null) is returned when the password does not match | ✅ Pass |
| BE-ADMAUTH-03 | Backend unit | Email is not an admin | Nothing (null) is returned when no admin has this email; The password is not even checked | ✅ Pass |
| BE-ADMAUTH-04 | Backend unit | Failed attempt is counted | One wrong password changes the count from 0 to 1; The new count is saved to the database | ✅ Pass |
| BE-ADMAUTH-05 | Backend unit | 4 failed attempts | The account is still NOT locked after the 4th wrong password | ✅ Pass |
| BE-ADMAUTH-06 | Backend unit | 5th failed attempt locks the account | After the 5th wrong password, the account is locked; The lock lasts about 15 minutes | ✅ Pass |
| BE-ADMAUTH-07 | Backend unit | Locked account | Login throws an error while the account is locked; This happens even if the password is correct; The password is not even checked | ✅ Pass |
| BE-ADMAUTH-08 | Backend unit | Lock time is over | When the lock has expired, the admin can log in again; The lock and the failed count are cleared | ✅ Pass |
| BE-ADMAUTH-09 | Backend unit | Successful login resets the count | After a correct login, the failed count goes back to 0; So old mistakes do not add up over time | ✅ Pass |
| BE-ADMAUTH-10 | Backend unit | Unlock after password reset | unlockAfterPasswordReset clears the lock and the failed count; The change is saved | ✅ Pass |
| API-ADMAUTH-01 | Backend API | Successful admin login | The response is 200 OK; The admin token is sent back in the response; The token is made with the role "ADMIN" | ✅ Pass |
| API-ADMAUTH-02 | Backend API | Wrong admin details | The response is 401 Unauthorized with "Invalid admin email or password"; No token is created | ✅ Pass |
| API-ADMAUTH-03 | Backend API | Wrong email format | The response is 400 Bad Request; The service is never called (blocked by validation) | ✅ Pass |
| API-ADMAUTH-04 | Backend API | Admin account is locked | The response is 423 Locked; The message tells the admin to use the password reset option; No token is created | ✅ Pass |

### UC-01 + UC-03 shared: tokens, sessions, password reset, navbar (46 tests)

| ID | Level | Test case | Expected result | Status |
|---|---|---|---|---|
| FE-PWD-01 | Frontend | Email field is empty | The message "Please enter your email address." is shown; No request is sent to the backend | ✅ Pass |
| FE-PWD-02 | Frontend | Client requests a reset link | The request uses type=CLIENT; Special characters in the email (like +) are encoded correctly; The same general message is shown (it does not reveal if the email exists) | ✅ Pass |
| FE-PWD-03 | Frontend | Admin requests a reset link | The admin page sends type=ADMIN (not CLIENT) | ✅ Pass |
| FE-PWD-04 | Frontend | Reset link has no token | The message "This password reset link is invalid." is shown; No request is sent to the backend | ✅ Pass |
| FE-PWD-05 | Frontend | Password is too short | The message "Password must be at least 8 characters." is shown; No request is sent to the backend | ✅ Pass |
| FE-PWD-06 | Frontend | Passwords do not match | The message "Passwords do not match." is shown; No request is sent to the backend | ✅ Pass |
| FE-PWD-07 | Frontend | Successful password reset | The token from the link is sent to the backend with the new password; A success message is shown | ✅ Pass |
| FE-PWD-08 | Frontend | Token is expired or already used | The error message from the backend is shown | ✅ Pass |
| FE-NAV-01 | Frontend | Nobody is logged in | "Client Login" is shown and links to /login; "Sign Out" is NOT shown | ✅ Pass |
| FE-NAV-02 | Frontend | Client is logged in | "Sign Out" is shown instead of "Client Login" | ✅ Pass |
| FE-NAV-03 | Frontend | Admin is logged in | "Sign Out" is shown for an admin too | ✅ Pass |
| FE-NAV-04 | Frontend | Sign Out | Both the client and admin tokens are removed; The user is sent to the Home page | ✅ Pass |
| FE-NAV-05 | Frontend | Navigation links | Home, About Casey, Services & Supports, Contact and Intake links exist; Each link points to the correct page | ✅ Pass |
| FE-SESSION-01 | Frontend | Client Login with ?sessionExpired=true | The message "Your session has expired. Please log in again." is shown | ✅ Pass |
| FE-SESSION-02 | Frontend | Normal Client Login visit | The session expired message is NOT shown | ✅ Pass |
| FE-SESSION-03 | Frontend | Admin Login with ?sessionExpired=true | The session expired message is shown on the admin page too | ✅ Pass |
| FE-SESSION-04 | Frontend | Client is inactive for 30 minutes | The client token is removed; The user is sent to /login?sessionExpired=true | ✅ Pass |
| FE-SESSION-05 | Frontend | Admin is inactive for 30 minutes | The admin token is removed; The admin is sent to /admin-login?sessionExpired=true | ✅ Pass |
| FE-SESSION-06 | Frontend | User is active | Moving the mouse restarts the timer; So an active user is NOT logged out after 30 minutes in total | ✅ Pass |
| FE-SESSION-07 | Frontend | Nobody is logged in | The user is not redirected anywhere | ✅ Pass |
| BE-PWD-01 | Backend unit | Client requests a reset link | A CLIENT token is saved for the correct user; The token is not empty and not used yet; The token expires in about 30 minutes | ✅ Pass |
| BE-PWD-02 | Backend unit | Email is not registered | No error is thrown (so attackers cannot tell which emails exist); No token is created | ✅ Pass |
| BE-PWD-03 | Backend unit | Admin requests a reset link | An ADMIN token is saved for the correct admin | ✅ Pass |
| BE-PWD-04 | Backend unit | Admin email not found | No token is created | ✅ Pass |
| BE-PWD-05 | Backend unit | Every request gets a new token | Two reset requests create two different tokens | ✅ Pass |
| BE-PWD-06 | Backend unit | Successful client password reset | The new password is saved in hashed form; The token is marked as used, so it cannot be used again | ✅ Pass |
| BE-PWD-07 | Backend unit | Successful admin password reset | The admin password is changed; A locked admin account is unlocked (failed count back to 0); The client table is not touched | ✅ Pass |
| BE-PWD-08 | Backend unit | Token does not exist | Reset fails; No new password is created | ✅ Pass |
| BE-PWD-09 | Backend unit | Token was already used | Reset fails; The user is not changed | ✅ Pass |
| BE-PWD-10 | Backend unit | Token has expired | Reset fails; The user is not changed | ✅ Pass |
| BE-PWD-11 | Backend unit | User account was deleted | Reset fails; The token is NOT marked as used | ✅ Pass |
| BE-JWT-01 | Backend unit | Token is accepted | A token made by JwtService can be read by the decoder; The token holds the correct email | ✅ Pass |
| BE-JWT-02 | Backend unit | Token lifetime | The token is valid for exactly 24 hours; The token is not already expired | ✅ Pass |
| BE-JWT-03 | Backend unit | Tampered token | If someone changes even the last 2 characters of a token, | ✅ Pass |
| BE-JWT-04 | Backend unit | Fake token | A random string is not accepted as a token | ✅ Pass |
| BE-JWT-05 | Backend unit | Client role | A token made for a client holds role = "CLIENT" | ✅ Pass |
| BE-JWT-06 | Backend unit | Admin role | A token made for an admin holds role = "ADMIN" | ✅ Pass |
| BE-JWT-07 | Backend unit | Spring Security understands the role | An admin token becomes the permission "ROLE_ADMIN"; A client token becomes "ROLE_CLIENT" (and NOT "ROLE_ADMIN") | ✅ Pass |
| API-PWD-01 | Backend API | Client asks for a reset link | The response is 200 OK; Only the client reset is called (not the admin one) | ✅ Pass |
| API-PWD-02 | Backend API | Admin asks for a reset link | The response is 200 OK; The admin reset is called; "admin" in lowercase also works | ✅ Pass |
| API-PWD-03 | Backend API | Unknown account type | The response is 400 with "Invalid account type"; The service is never called | ✅ Pass |
| API-PWD-04 | Backend API | Email is not registered | The response is 200 with the same general message; So nobody can find out which emails have accounts | ✅ Pass |
| API-PWD-05 | Backend API | Successful password reset | The response is 200 with "Password reset successfully" | ✅ Pass |
| API-PWD-06 | Backend API | Passwords do not match | The response is 400 with "Passwords do not match"; The service is never called | ✅ Pass |
| API-PWD-07 | Backend API | Invalid or expired token | The response is 400 with the token error message | ✅ Pass |
| API-PWD-08 | Backend API | New password too short | The response is 400 Bad Request; The service is never called (blocked by validation) | ✅ Pass |

## Manual testing: mobile layout

Tested 12 pages on 3 screen sizes: iPhone SE (375px), iPhone Pro Max (430px) and iPad (820px).

| Page | Phone | Tablet |
|---|---|---|
| Home | Navigation menu missing, last section overlaps the image | Last section overlaps the image |
| Login / Signup / Admin Login | Pass | Pass |
| Forgot / Reset Password (client and admin) | Pass | Pass |
| Contact | Pass | Pass |
| Intake | Pass | Pass |
| Navbar (all pages) | No menu button on phones | Pass |
| Footer (all pages) | Phone and location icons missing, small text | Phone and location icons missing |

No page scrolls sideways on any screen size.

## Known issues

| Issue | Found by | Status |
|---|---|---|
| Login token uses the email exactly as typed (for example `Jane@Mail.com`), but the database stores it in lowercase. The intake form can then fail with "User account not found". | Test `API-AUTH-09` | Open. Fix in `AuthController.login`: use `request.getEmail().trim().toLowerCase()`. Then remove `@Disabled` from the test. |
| Navigation links are hidden on phones and there is no menu button. | Mobile testing | Open |
| `/client-dashboard` and `/admin-dashboard` pages do not exist yet. Login redirects there. | Test `FE-LOGIN-01`, `FE-LOGIN-05` | Open |

## Not tested yet

- About and Services pages (no content yet)
- Full end-to-end tests with a real backend and database (planned for a later sprint)
