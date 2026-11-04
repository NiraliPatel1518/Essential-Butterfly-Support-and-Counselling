import { describe, expect, test } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Login from "./Login";
import AdminLogin from "./AdminLogin";
import { mockFetch, mockFetchNetworkError, renderPage } from "../test/helpers";

/*
 * UC-01: Client Log In  +  UC-03: Admin Log In
 *
 * This file has 7 tests:
 *
 *   Client Login page (4 tests)
 *     Test 1 - Correct login -> token is saved and user is redirected
 *     Test 2 - Wrong password -> error is shown and no token is saved
 *     Test 3 - Backend is not running -> friendly error is shown
 *     Test 4 - "Forgot password" link goes to the correct page
 *
 *   Admin Login page (3 tests)
 *     Test 5 - Correct admin login -> adminToken is saved and admin is redirected
 *     Test 6 - Wrong admin password -> error is shown and no token is saved
 *     Test 7 - Admin "Forgot password" link goes to the correct page
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
    const fetchMock = mockFetch(true, "client.jwt.token");
    renderPage("/login", <Login />);

    // Act: log in with correct details
    await login("jane@mail.com", "Password123");

    // Assert: correct API, token saved, user redirected
    expect(fetchMock.mock.calls[0][0]).toBe("http://localhost:8080/api/auth/login");
    expect(localStorage.getItem("authToken")).toBe("client.jwt.token");
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
   * TEST 5: Successful admin login
   * Checks that:
   *   - The request goes to the admin login API
   *   - The token is saved as "adminToken" (NOT as "authToken")
   *   - The admin is sent to /admin-dashboard
   */
  test("saves adminToken (not authToken) and redirects to admin dashboard", async () => {
    // Arrange: backend will answer with an admin token, open the Admin Login page
    const fetchMock = mockFetch(true, "admin.jwt.token");
    renderPage("/admin-login", <AdminLogin />);

    // Act: log in with correct admin details
    await login("admin@test.com", "Admin@12345");

    // Assert: correct API, correct token name, admin redirected
    expect(fetchMock.mock.calls[0][0]).toBe("http://localhost:8080/api/admin/login");
    expect(localStorage.getItem("adminToken")).toBe("admin.jwt.token");
    expect(localStorage.getItem("authToken")).toBeNull();
    expect(await screen.findByText(/^Landed on/)).toHaveTextContent(
      "Landed on /admin-dashboard"
    );
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
    renderPage("/admin-login", <AdminLogin />);

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
    renderPage("/admin-login", <AdminLogin />);

    // Assert: link has the correct address
    expect(screen.getByRole("link", { name: /forgot/i })).toHaveAttribute(
      "href",
      "/admin/forgot-password"
    );
  });
});