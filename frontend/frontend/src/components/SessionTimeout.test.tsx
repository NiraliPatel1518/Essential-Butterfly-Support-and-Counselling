import { afterEach, describe, expect, test, vi } from "vitest";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import SessionTimeout from "./SessionTimeout";
import Login from "../pages/Login";
import AdminLogin from "../pages/AdminLogin";
import { renderPage } from "../test/helpers";

/*
 * UC-01 / UC-03: Session expiry
 *
 * This file has 7 tests:
 *
 *   Session expired message (3 tests)
 *     Test 1 - Client Login shows "session expired" when ?sessionExpired=true
 *     Test 2 - Client Login does NOT show it on a normal visit
 *     Test 3 - Admin Login shows "session expired" when ?sessionExpired=true
 *
 *   Automatic logout after 30 minutes of no activity (4 tests)
 *     Test 4 - Client is logged out and sent to /login?sessionExpired=true
 *     Test 5 - Admin is logged out and sent to /admin-login?sessionExpired=true
 *     Test 6 - Any activity (like moving the mouse) restarts the 30-minute timer
 *     Test 7 - Nothing happens when nobody is logged in
 *
 * Fake timers are used, so the tests do not really wait 30 minutes.
 */

const THIRTY_MINUTES = 30 * 60 * 1000;

// Helper: shows the current address (path + query) on the screen
function LocationProbe() {
  const location = useLocation();
  return <p data-testid="location">{location.pathname + location.search}</p>;
}

// Helper: renders SessionTimeout on a page called /dashboard
function renderWithTimeout() {
  return render(
    <MemoryRouter initialEntries={["/dashboard"]}>
      <SessionTimeout />
      <LocationProbe />
      <Routes>
        <Route path="*" element={<p>Some page</p>} />
      </Routes>
    </MemoryRouter>
  );
}

afterEach(() => {
  vi.useRealTimers();
});

describe("Session expired message", () => {
  /*
   * TEST 1: Client Login with ?sessionExpired=true
   * Checks that:
   *   - The message "Your session has expired. Please log in again." is shown
   */
  test("client login shows session expired message", () => {
    // Arrange + Act: open the login page with the sessionExpired flag
    renderPage("/login", <Login />, "/login?sessionExpired=true");

    // Assert: message is shown
    expect(
      screen.getByText("Your session has expired. Please log in again.")
    ).toBeInTheDocument();
  });

  /*
   * TEST 2: Normal Client Login visit
   * Checks that:
   *   - The session expired message is NOT shown
   */
  test("client login does not show the message on a normal visit", () => {
    // Arrange + Act: open the login page normally
    renderPage("/login", <Login />);

    // Assert: no message
    expect(screen.queryByText(/your session has expired/i)).not.toBeInTheDocument();
  });

  /*
   * TEST 3: Admin Login with ?sessionExpired=true
   * Checks that:
   *   - The session expired message is shown on the admin page too
   */
  test("admin login shows session expired message", () => {
    // Arrange + Act: open the admin login page with the sessionExpired flag
    renderPage("/admin-login", <AdminLogin />, "/admin-login?sessionExpired=true");

    // Assert: message is shown
    expect(
      screen.getByText("Your session has expired. Please log in again.")
    ).toBeInTheDocument();
  });
});

describe("Automatic logout after 30 minutes", () => {
  /*
   * TEST 4: Client is inactive for 30 minutes
   * Checks that:
   *   - The client token is removed
   *   - The user is sent to /login?sessionExpired=true
   */
  test("logs out an inactive client", () => {
    // Arrange: client is logged in, fake clock
    vi.useFakeTimers();
    localStorage.setItem("authToken", "client.jwt");
    renderWithTimeout();

    // Act: 30 minutes pass with no activity
    act(() => {
      vi.advanceTimersByTime(THIRTY_MINUTES);
    });

    // Assert: logged out and redirected
    expect(localStorage.getItem("authToken")).toBeNull();
    expect(screen.getByTestId("location")).toHaveTextContent("/login?sessionExpired=true");
  });

  /*
   * TEST 5: Admin is inactive for 30 minutes
   * Checks that:
   *   - The admin token is removed
   *   - The admin is sent to /admin-login?sessionExpired=true
   */
  test("logs out an inactive admin", () => {
    // Arrange: admin is logged in, fake clock
    vi.useFakeTimers();
    localStorage.setItem("adminToken", "admin.jwt");
    renderWithTimeout();

    // Act: 30 minutes pass with no activity
    act(() => {
      vi.advanceTimersByTime(THIRTY_MINUTES);
    });

    // Assert: logged out and redirected to the admin login
    expect(localStorage.getItem("adminToken")).toBeNull();
    expect(screen.getByTestId("location")).toHaveTextContent("/admin-login?sessionExpired=true");
  });

  /*
   * TEST 6: User is active
   * Checks that:
   *   - Moving the mouse restarts the timer
   *   - So an active user is NOT logged out after 30 minutes in total
   */
  test("activity restarts the timer", () => {
    // Arrange: client is logged in, fake clock
    vi.useFakeTimers();
    localStorage.setItem("authToken", "client.jwt");
    renderWithTimeout();

    // Act: 20 minutes pass, user moves the mouse, 20 more minutes pass
    act(() => {
      vi.advanceTimersByTime(20 * 60 * 1000);
    });
    fireEvent.mouseMove(window);
    act(() => {
      vi.advanceTimersByTime(20 * 60 * 1000);
    });

    // Assert: still logged in, still on the same page
    expect(localStorage.getItem("authToken")).toBe("client.jwt");
    expect(screen.getByTestId("location")).toHaveTextContent("/dashboard");
  });

  /*
   * TEST 7: Nobody is logged in
   * Checks that:
   *   - The user is not redirected anywhere
   */
  test("does nothing when nobody is logged in", () => {
    // Arrange: no token, fake clock
    vi.useFakeTimers();
    renderWithTimeout();

    // Act: 30 minutes pass
    act(() => {
      vi.advanceTimersByTime(THIRTY_MINUTES);
    });

    // Assert: still on the same page
    expect(screen.getByTestId("location")).toHaveTextContent("/dashboard");
  });
});
