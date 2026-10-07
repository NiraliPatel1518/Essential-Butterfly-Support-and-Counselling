import { describe, expect, test } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AdminResetPassword from "./AdminResetPassword";
import { mockFetch, renderPage } from "../test/helpers";

/*
 * UC-03: Admin Reset Password
 *
 * This file has 5 tests for the Admin Reset Password page:
 *   Test 1 - Link has no token -> error is shown, backend is not called
 *   Test 2 - Password shorter than 8 characters -> error is shown
 *   Test 3 - Passwords do not match -> error is shown
 *   Test 4 - Valid password -> token is sent and admin success message is shown
 *   Test 5 - Expired token -> backend error is shown
 *
 * The backend is NOT needed. mockFetch() creates a fake backend response.
 */

describe("Admin Reset Password page", () => {
  // A reset link with a sample token, like the one printed by the backend
  const url = "/admin/reset-password?token=admin123";

  // Helper: types the new password and confirm password, then clicks "Reset Password".
  async function submit(newPass: string, confirm: string) {
    const user = userEvent.setup();
    await user.type(screen.getByLabelText(/new password/i), newPass);
    await user.type(screen.getByLabelText(/confirm password/i), confirm);
    await user.click(screen.getByRole("button", { name: "Reset Password" }));
  }

  /*
   * TEST 1: Reset link has no token
   * Checks that:
   *   - The message "This password reset link is invalid." is shown
   *   - No request is sent to the backend
   */
  test("shows error when link has no token", async () => {
    // Arrange: open the page WITHOUT ?token= in the address
    const fetchMock = mockFetch(true);
    renderPage("/admin/reset-password", <AdminResetPassword />);

    // Act: enter a valid password and submit
    await submit("NewPassword1", "NewPassword1");

    // Assert: error is shown and backend is not called
    expect(await screen.findByText("This password reset link is invalid.")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  /*
   * TEST 2: Password is too short
   * Checks that:
   *   - The message "Password must be at least 8 characters." is shown
   *   - No request is sent to the backend
   */
  test("rejects password shorter than 8 characters", async () => {
    // Arrange: open the page with a valid token
    const fetchMock = mockFetch(true);
    renderPage("/admin/reset-password", <AdminResetPassword />, url);

    // Act: enter a 5-character password
    await submit("short", "short");

    // Assert: error is shown and backend is not called
    expect(
      await screen.findByText("Password must be at least 8 characters.")
    ).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  /*
   * TEST 3: Passwords do not match
   * Checks that:
   *   - The message "Passwords do not match." is shown
   *   - No request is sent to the backend
   */
  test("rejects passwords that do not match", async () => {
    // Arrange: open the page with a valid token
    const fetchMock = mockFetch(true);
    renderPage("/admin/reset-password", <AdminResetPassword />, url);

    // Act: enter two different passwords
    await submit("NewPassword1", "Different12");

    // Assert: error is shown and backend is not called
    expect(await screen.findByText("Passwords do not match.")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  /*
   * TEST 4: Successful admin password reset
   * Checks that:
   *   - The token from the link is sent to the backend with the new password
   *   - The admin success message is shown
   */
  test("sends token from the URL and shows admin success message", async () => {
    // Arrange: backend will answer "success", open the page with a token
    const fetchMock = mockFetch(true, "Password reset successfully");
    renderPage("/admin/reset-password", <AdminResetPassword />, url);

    // Act: enter a valid new password
    await submit("NewPassword1", "NewPassword1");

    // Assert: correct data sent and success message shown
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(requestUrl).toBe("http://localhost:8080/api/password/reset");
    expect(JSON.parse(options.body)).toEqual({
      token: "admin123",
      newPassword: "NewPassword1",
      confirmPassword: "NewPassword1",
    });
    expect(
      await screen.findByText(/your administrator password has been reset successfully/i)
    ).toBeInTheDocument();
  });

  /*
   * TEST 5: Token is expired or already used
   * Checks that:
   *   - The error message from the backend is shown
   */
  test("shows backend error for expired token", async () => {
    // Arrange: backend will answer "expired token"
    mockFetch(false, "Invalid, expired, or already used reset token");
    renderPage("/admin/reset-password", <AdminResetPassword />, url);

    // Act: enter a valid new password
    await submit("NewPassword1", "NewPassword1");

    // Assert: backend error is shown
    expect(
      await screen.findByText("Invalid, expired, or already used reset token")
    ).toBeInTheDocument();
  });
});
