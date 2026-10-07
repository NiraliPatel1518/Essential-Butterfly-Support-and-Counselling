import { describe, expect, test } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import ForgotPassword from "./ForgotPassword";
import ResetPassword from "./ResetPassword";
import AdminForgotPassword from "./AdminForgotPassword";
import { mockFetch, renderPage } from "../test/helpers";

/*
 * UC-01 / UC-03: Forgot Password and Reset Password (client + admin)
 *
 * This file has 8 tests:
 *
 *   Forgot Password page (3 tests)
 *     Test 1 - Empty email -> error is shown, backend is not called
 *     Test 2 - Client email -> request sent with type=CLIENT, message is shown
 *     Test 3 - Admin page -> request sent with type=ADMIN
 *
 *   Reset Password page (5 tests)
 *     Test 4 - Link has no token -> error is shown
 *     Test 5 - Password shorter than 8 characters -> error is shown
 *     Test 6 - Passwords do not match -> error is shown
 *     Test 7 - Valid password -> token is sent and success message is shown
 *     Test 8 - Expired token -> backend error is shown
 *
 * The backend is NOT needed. mockFetch() creates a fake backend response.
 */

describe("Forgot Password page", () => {
  /*
   * TEST 1: Email field is empty
   * Checks that:
   *   - The message "Please enter your email address." is shown
   *   - No request is sent to the backend
   */
  test("asks for email when field is empty", async () => {
    // Arrange: open the Forgot Password page
    const fetchMock = mockFetch(true);
    renderPage("/forgot-password", <ForgotPassword />);

    // Act: click the button without typing an email
    await userEvent.click(screen.getByRole("button", { name: "Send Reset Link" }));

    // Assert: error is shown and backend is not called
    expect(await screen.findByText("Please enter your email address.")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  /*
   * TEST 2: Client requests a reset link
   * Checks that:
   *   - The request uses type=CLIENT
   *   - Special characters in the email (like +) are encoded correctly
   *   - The same general message is shown (it does not reveal if the email exists)
   */
  test("calls backend with type=CLIENT and shows generic message", async () => {
    // Arrange: open the Forgot Password page
    const fetchMock = mockFetch(true);
    renderPage("/forgot-password", <ForgotPassword />);

    // Act: type an email and click the button
    await userEvent.type(screen.getByLabelText("Email Address"), "jane+1@mail.com");
    await userEvent.click(screen.getByRole("button", { name: "Send Reset Link" }));

    // Assert: correct URL and message
    expect(fetchMock.mock.calls[0][0]).toBe(
      "http://localhost:8080/api/password/forgot?type=CLIENT&email=jane%2B1%40mail.com"
    );
    expect(
      await screen.findByText(/if an account exists with this email/i)
    ).toBeInTheDocument();
  });

  /*
   * TEST 3: Admin requests a reset link
   * Checks that:
   *   - The admin page sends type=ADMIN (not CLIENT)
   */
  test("admin page calls backend with type=ADMIN", async () => {
    // Arrange: open the Admin Forgot Password page
    const fetchMock = mockFetch(true);
    renderPage("/admin/forgot-password", <AdminForgotPassword />);

    // Act: type the admin email and click the button
    await userEvent.type(screen.getByLabelText(/email/i), "admin@test.com");
    await userEvent.click(screen.getByRole("button", { name: /send/i }));

    // Assert: request is marked as ADMIN
    expect(fetchMock.mock.calls[0][0]).toContain("type=ADMIN");
  });
});

describe("Reset Password page", () => {
  // A reset link with a sample token, like the one printed by the backend
  const url = "/reset-password?token=abc123";

  // Helper: types the new password and confirm password, then clicks "Reset Password".
  // An empty value means the field is left blank.
  async function submit(newPass: string, confirm: string) {
    const user = userEvent.setup();
    if (newPass) await user.type(screen.getByLabelText("New Password"), newPass);
    if (confirm) await user.type(screen.getByLabelText("Confirm Password"), confirm);
    await user.click(screen.getByRole("button", { name: "Reset Password" }));
  }

  /*
   * TEST 4: Reset link has no token
   * Checks that:
   *   - The message "This password reset link is invalid." is shown
   *   - No request is sent to the backend
   */
  test("shows error when link has no token", async () => {
    // Arrange: open the page WITHOUT ?token= in the address
    const fetchMock = mockFetch(true);
    renderPage("/reset-password", <ResetPassword />, "/reset-password");

    // Act: enter a valid password and submit
    await submit("NewPassword1", "NewPassword1");

    // Assert: error is shown and backend is not called
    expect(await screen.findByText("This password reset link is invalid.")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  /*
   * TEST 5: Password is too short
   * Checks that:
   *   - The message "Password must be at least 8 characters." is shown
   *   - No request is sent to the backend
   */
  test("rejects password shorter than 8 characters", async () => {
    // Arrange: open the page with a valid token
    const fetchMock = mockFetch(true);
    renderPage("/reset-password", <ResetPassword />, url);

    // Act: enter a 5-character password
    await submit("short", "short");

    // Assert: error is shown and backend is not called
    expect(
      await screen.findByText("Password must be at least 8 characters.")
    ).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  /*
   * TEST 6: Passwords do not match
   * Checks that:
   *   - The message "Passwords do not match." is shown
   *   - No request is sent to the backend
   */
  test("rejects passwords that do not match", async () => {
    // Arrange: open the page with a valid token
    const fetchMock = mockFetch(true);
    renderPage("/reset-password", <ResetPassword />, url);

    // Act: enter two different passwords
    await submit("NewPassword1", "Different12");

    // Assert: error is shown and backend is not called
    expect(await screen.findByText("Passwords do not match.")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  /*
   * TEST 7: Successful password reset
   * Checks that:
   *   - The token from the link is sent to the backend with the new password
   *   - A success message is shown
   */
  test("sends token from the URL and shows success", async () => {
    // Arrange: backend will answer "success", open the page with a token
    const fetchMock = mockFetch(true, "Password reset successfully");
    renderPage("/reset-password", <ResetPassword />, url);

    // Act: enter a valid new password
    await submit("NewPassword1", "NewPassword1");

    // Assert: correct data sent and success message shown
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
      token: "abc123",
      newPassword: "NewPassword1",
      confirmPassword: "NewPassword1",
    });
    expect(
      await screen.findByText(/your password has been reset successfully/i)
    ).toBeInTheDocument();
  });

  /*
   * TEST 8: Token is expired or already used
   * Checks that:
   *   - The error message from the backend is shown
   */
  test("shows backend error for expired token", async () => {
    // Arrange: backend will answer "expired token"
    mockFetch(false, "Invalid, expired, or already used reset token");
    renderPage("/reset-password", <ResetPassword />, url);

    // Act: enter a valid new password
    await submit("NewPassword1", "NewPassword1");

    // Assert: backend error is shown
    expect(
      await screen.findByText("Invalid, expired, or already used reset token")
    ).toBeInTheDocument();
  });
});