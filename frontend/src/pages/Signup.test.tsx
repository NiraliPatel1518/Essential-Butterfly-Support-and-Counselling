import { describe, expect, test } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Signup from "./Signup";
import { mockFetch, mockFetchNetworkError, renderPage } from "../test/helpers";

/*
 * UC-01: Create Account
 *
 * This file has 5 tests for the Signup page:
 *   Test 1 - Form data is sent to the correct API in the correct format
 *   Test 2 - Successful signup -> success message and redirect to /login
 *   Test 3 - Passwords do not match -> error is shown, backend is not called
 *   Test 4 - Email already exists -> backend error is shown
 *   Test 5 - Backend is not running -> friendly error is shown
 *
 * The backend is NOT needed. mockFetch() creates a fake backend response.
 */

// Helper: fills in all signup fields and ticks the terms checkbox.
// It does not click the "Create Account" button.
async function fillForm(password = "Password123", confirm = "Password123") {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText("Full name"), "Jane Doe");
  await user.type(screen.getByLabelText("Email address"), "jane@mail.com");
  await user.type(screen.getByLabelText("Create a password"), password);
  await user.type(screen.getByLabelText("Confirm password"), confirm);
  await user.click(screen.getByRole("checkbox"));
  return user;
}

describe("Signup page", () => {
  /*
   * TEST 1: Correct request is sent
   * Checks that:
   *   - The request goes to /api/auth/signup using POST
   *   - Full name, email, password and confirm password are all sent
   */
  test("sends the form data to /api/auth/signup", async () => {
    // Arrange: backend will answer "success", open the Signup page
    const fetchMock = mockFetch(true, "Account created successfully");
    renderPage("/signup", <Signup />);

    // Act: fill the form and click "Create Account"
    const user = await fillForm();
    await user.click(screen.getByRole("button", { name: "Create Account" }));

    // Assert: one request, correct URL, method and data
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/auth/signup");
    expect(options.method).toBe("POST");
    expect(JSON.parse(options.body)).toEqual({
      fullName: "Jane Doe",
      email: "jane@mail.com",
      password: "Password123",
      confirmPassword: "Password123",
    });
  });

  /*
   * TEST 2: Successful signup
   * Checks that:
   *   - A success message is shown
   *   - The user is sent to /login (after a short delay)
   */
  test("shows success message and goes to /login", async () => {
    // Arrange: backend will answer "success"
    mockFetch(true, "Account created successfully");
    renderPage("/signup", <Signup />);

    // Act: fill the form and click "Create Account"
    const user = await fillForm();
    await user.click(screen.getByRole("button", { name: "Create Account" }));

    // Assert: success message, then redirect to /login
    expect(await screen.findByRole("status")).toHaveTextContent(/account created/i);
    expect(
      await screen.findByText("Landed on /login", {}, { timeout: 2000 })
    ).toBeInTheDocument();
  });

  /*
   * TEST 3: Passwords do not match
   * Checks that:
   *   - The message "Passwords do not match." is shown
   *   - No request is sent to the backend
   */
  test("shows error and does NOT call backend when passwords do not match", async () => {
    // Arrange: open the Signup page
    const fetchMock = mockFetch(true);
    renderPage("/signup", <Signup />);

    // Act: use two different passwords and click "Create Account"
    const user = await fillForm("Password123", "Different123");
    await user.click(screen.getByRole("button", { name: "Create Account" }));

    // Assert: error is shown and backend is not called
    expect(screen.getByRole("alert")).toHaveTextContent("Passwords do not match.");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  /*
   * TEST 4: Email already registered
   * Checks that:
   *   - The error message from the backend is shown
   */
  test("shows backend error when email already exists", async () => {
    // Arrange: backend will answer "email already exists"
    mockFetch(false, "An account with this email already exists");
    renderPage("/signup", <Signup />);

    // Act: fill the form and click "Create Account"
    const user = await fillForm();
    await user.click(screen.getByRole("button", { name: "Create Account" }));

    // Assert: backend error is shown
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "An account with this email already exists"
    );
  });

  /*
   * TEST 5: Backend is not running
   * Checks that:
   *   - A friendly "Unable to connect to the server" message is shown
   */
  test("shows friendly message when backend is down", async () => {
    // Arrange: fake a network failure (server is off)
    mockFetchNetworkError();
    renderPage("/signup", <Signup />);

    // Act: fill the form and click "Create Account"
    const user = await fillForm();
    await user.click(screen.getByRole("button", { name: "Create Account" }));

    // Assert: friendly error is shown
    expect(await screen.findByRole("alert")).toHaveTextContent(
      /unable to connect to the server/i
    );
  });
});