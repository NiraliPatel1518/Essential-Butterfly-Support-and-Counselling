import { afterEach, describe, expect, test, vi } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Login from "./Login";
import { mockFetch, mockFetchNetworkError, renderPage } from "../test/helpers";

/*
 * UC-01: Client Log In  +  UC-03: Admin Log In
 *
 * This file has 11 tests:
 *
 *   Client Login page (4 tests)
 *     Test 1 - Correct login -> token is saved and user is redirected
 *     Test 2 - Wrong password -> error is shown and no token is saved
 *     Test 3 - Backend is not running -> friendly error is shown
 *     Test 4 - "Forgot password" link goes to the correct page
 *
 *   Admin Login page (7 tests)
 *     Test 5 - Password is correct -> the admin must enter an email code first
 *     Test 6 - Wrong admin password -> error is shown and no token is saved
 *     Test 7 - Admin "Forgot password" link goes to the correct page
 *     Test 8 - Correct email code -> adminToken is saved and admin is redirected
 *     Test 9 - Wrong email code -> error is shown and no token is saved
 *     Test 10 - Code that is not 6 digits is refused before anything is sent
 *     Test 11 - A token without the verified (mfa) flag is never saved
 *
 * The backend is NOT needed. mockFetch() creates a fake backend response.
 */

// Helper: types the email and password, then clicks the "Log In" button.
// Works for both the client and the admin login page.
async function login(email: string, password: string) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText(/email/i), email);
  await user.type(screen.getByLabelText(/^password$/i), password);
  await user.click(screen.getByRole("button", { name: "Log In" }));
}

function tokenFor(role: "CLIENT" | "ADMIN", mfa = false) {
  const encode = (value: object) =>
    btoa(JSON.stringify(value)).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
  return `${encode({ alg: "HS384", typ: "JWT" })}.${encode({ role, mfa, exp: Math.floor(Date.now() / 1000) + 3600 })}.signature`;
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

// Fake backend for the admin two-step login:
//   1) /api/auth/login               -> says an email code is needed
//   2) /api/admin-auth/login-otp/verify -> answers with verifyBody
function stubAdminBackend(verifyBody: string, verifyOk = true) {
  const answer = (ok: boolean, body: string) =>
    Promise.resolve({ ok, status: ok ? 200 : 400, text: () => Promise.resolve(body) });
  const fetchMock = vi.fn((url: string, _init?: RequestInit) =>
    url.includes("/login-otp/verify")
      ? answer(verifyOk, verifyBody)
      : answer(
          true,
          JSON.stringify({
            requiresOtp: true,
            challengeToken: "challenge-123",
            maskedEmail: "a***@test.com",
          })
        )
  );
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

// Logs in as admin and waits for the "enter your code" window.
async function startAdminLogin() {
  await login("admin@test.com", "Admin@12345");
  await screen.findByRole("dialog");
}

async function enterCode(code: string) {
  const user = userEvent.setup();
  await user.type(screen.getByPlaceholderText("Enter code"), code);
  await user.click(screen.getByRole("button", { name: "Verify and continue" }));
}

describe("Client Login page", () => {
  /*
   * TEST 1: Successful client login
   * Checks that:
   *   - The request goes to the client login API
   *   - The token from the backend is saved as "authToken"
   *   - The user is sent to /client-dashboard
   */
  test("saves token and redirects after successful login", async () => {
    // Arrange: backend will answer with a token, open the Login page
    const clientToken = tokenFor("CLIENT");
    const fetchMock = mockFetch(true, clientToken);
    renderPage("/login", <Login />);

    // Act: log in with correct details
    await login("jane@mail.com", "Password123");

    // Assert: correct API, token saved, user redirected
    expect(fetchMock.mock.calls[0][0]).toBe("http://localhost:8080/api/auth/login");
    expect(localStorage.getItem("authToken")).toBe(clientToken);
    expect(await screen.findByText(/^Landed on/)).toHaveTextContent(
      "Landed on /client-dashboard"
    );
  });

  /*
   * TEST 2: Wrong email or password
   * Checks that:
   *   - The error message from the backend is shown
   *   - No token is saved
   */
  test("shows error and saves no token when credentials are wrong", async () => {
    // Arrange: backend will answer with an error
    mockFetch(false, "Invalid email or password");
    renderPage("/login", <Login />);

    // Act: log in with a wrong password
    await login("jane@mail.com", "wrong");

    // Assert: error is shown and nothing is saved
    expect(await screen.findByText("Invalid email or password")).toBeInTheDocument();
    expect(localStorage.getItem("authToken")).toBeNull();
  });

  /*
   * TEST 3: Backend is not running
   * Checks that:
   *   - A friendly "Unable to connect to the server" message is shown
   */
  test("shows friendly message when backend is down", async () => {
    // Arrange: fake a network failure (server is off)
    mockFetchNetworkError();
    renderPage("/login", <Login />);

    // Act: try to log in
    await login("jane@mail.com", "Password123");

    // Assert: friendly error is shown
    expect(await screen.findByText(/unable to connect to the server/i)).toBeInTheDocument();
  });

  /*
   * TEST 4: Forgot password link
   * Checks that:
   *   - The "Forgot password" link points to /forgot-password
   */
  test("has a Forgot password link", () => {
    // Arrange: open the Login page
    renderPage("/login", <Login />);

    // Assert: link has the correct address
    expect(screen.getByRole("link", { name: /forgot/i })).toHaveAttribute(
      "href",
      "/forgot-password"
    );
  });
});

describe("Admin Login page", () => {
  /*
   * TEST 5: Correct password, but the admin is not signed in yet
   * Checks that:
   *   - The login request goes to the normal login API
   *   - A window asks for the email code and shows the hidden email address
   *   - No token is saved before the code is entered
   */
  test("asks for the email code and saves no token yet", async () => {
    const fetchMock = stubAdminBackend(tokenFor("ADMIN", true));
    renderPage("/login", <Login />);

    await startAdminLogin();

    expect(fetchMock.mock.calls[0][0]).toBe("http://localhost:8080/api/auth/login");
    expect(screen.getByText("a***@test.com")).toBeInTheDocument();
    expect(localStorage.getItem("adminToken")).toBeNull();
    expect(localStorage.getItem("authToken")).toBeNull();
  });

  /*
   * TEST 6: Wrong admin email or password
   * Checks that:
   *   - The error message from the backend is shown
   *   - No admin token is saved
   */
  test("shows error when admin credentials are wrong", async () => {
    // Arrange: backend will answer with an error
    mockFetch(false, "Invalid admin email or password");
    renderPage("/login", <Login />);

    // Act: log in with a wrong password
    await login("admin@test.com", "wrong");

    // Assert: error is shown and nothing is saved
    expect(await screen.findByText("Invalid admin email or password")).toBeInTheDocument();
    expect(localStorage.getItem("adminToken")).toBeNull();
  });

  /*
   * TEST 7: Admin forgot password link
   * Checks that:
   *   - The "Forgot password" link points to /admin/forgot-password
   */
  test("has an admin Forgot password link", () => {
    // Arrange: open the Admin Login page
    renderPage("/login", <Login />);

    // Assert: link has the correct address
    expect(screen.getByRole("link", { name: /forgot/i })).toHaveAttribute(
      "href",
      "/forgot-password"
    );
  });

  /*
   * TEST 8: Correct email code
   * Checks that:
   *   - The code and the challenge are sent to the verify API
   *   - The token is saved as "adminToken" (NOT as "authToken")
   *   - The admin is sent to /admin-dashboard
   */
  test("saves adminToken and redirects after the correct email code", async () => {
    const adminToken = tokenFor("ADMIN", true);
    const fetchMock = stubAdminBackend(adminToken);
    renderPage("/login", <Login />);

    await startAdminLogin();
    await enterCode("123456");

    const verifyCall = fetchMock.mock.calls.find(([url]) =>
      String(url).includes("/api/admin-auth/login-otp/verify")
    );
    expect(verifyCall).toBeDefined();
    expect(JSON.parse(String(verifyCall![1]?.body))).toEqual({
      challengeToken: "challenge-123",
      code: "123456",
    });
    expect(localStorage.getItem("adminToken")).toBe(adminToken);
    expect(localStorage.getItem("authToken")).toBeNull();
    expect(await screen.findByText(/^Landed on/)).toHaveTextContent(
      "Landed on /admin-dashboard"
    );
  });

  /*
   * TEST 9: Wrong email code
   * Checks that:
   *   - The error from the backend is shown in the window
   *   - No token is saved
   */
  test("shows the error and saves no token when the email code is wrong", async () => {
    stubAdminBackend("Invalid or expired verification code.", false);
    renderPage("/login", <Login />);

    await startAdminLogin();
    await enterCode("000000");

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Invalid or expired verification code."
    );
    expect(localStorage.getItem("adminToken")).toBeNull();
  });

  /*
   * TEST 10: Code is too short
   * Checks that:
   *   - The window asks for 6 digits
   *   - Nothing is sent to the verify API
   */
  test("asks for a 6-digit code before sending anything", async () => {
    const fetchMock = stubAdminBackend(tokenFor("ADMIN", true));
    renderPage("/login", <Login />);

    await startAdminLogin();
    await enterCode("123");

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Enter the 6-digit verification code."
    );
    expect(
      fetchMock.mock.calls.some(([url]) => String(url).includes("/login-otp/verify"))
    ).toBe(false);
  });

  /*
   * TEST 11: Token without the verified flag
   * Checks that:
   *   - An admin token that does not carry the mfa flag is refused
   *   - No token is saved
   */
  test("refuses an admin token that is not marked as verified", async () => {
    stubAdminBackend(tokenFor("ADMIN", false));
    renderPage("/login", <Login />);

    await startAdminLogin();
    await enterCode("123456");

    expect(
      await screen.findByText(/unable to complete administrator sign-in/i)
    ).toBeInTheDocument();
    expect(localStorage.getItem("adminToken")).toBeNull();
  });
});