import { describe, expect, test } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Navbar from "./Navbar";
import { renderPage } from "../test/helpers";

/*
 * UC-01 / UC-03: Navbar login and sign out
 *
 * This file has 5 tests for the Navbar:
 *   Test 1 - Nobody logged in -> "Client Login" button is shown
 *   Test 2 - Client logged in -> "Sign Out" button is shown
 *   Test 3 - Admin logged in -> "Sign Out" button is shown
 *   Test 4 - Sign Out removes both tokens and goes to the Home page
 *   Test 5 - All main navigation links are present
 */

describe("Navbar", () => {
  /*
   * TEST 1: Nobody is logged in
   * Checks that:
   *   - "Client Login" is shown and links to /login
   *   - "Sign Out" is NOT shown
   */
  test("shows Client Login when nobody is logged in", () => {
    // Arrange + Act: no token saved, show the navbar
    renderPage("/page", <Navbar />);

    // Assert: login link is shown, sign out is not
    expect(screen.getByRole("link", { name: "Client Login" })).toHaveAttribute("href", "/login");
    expect(screen.queryByRole("button", { name: "Sign Out" })).not.toBeInTheDocument();
  });

  /*
   * TEST 2: Client is logged in
   * Checks that:
   *   - "Sign Out" is shown instead of "Client Login"
   */
  test("shows Sign Out when a client is logged in", () => {
    // Arrange: client token saved
    localStorage.setItem("authToken", "client.jwt");

    // Act: show the navbar
    renderPage("/page", <Navbar />);

    // Assert: sign out is shown, login is not
    expect(screen.getByRole("button", { name: "Sign Out" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Client Login" })).not.toBeInTheDocument();
  });

  /*
   * TEST 3: Admin is logged in
   * Checks that:
   *   - "Sign Out" is shown for an admin too
   */
  test("shows Sign Out when an admin is logged in", () => {
    // Arrange: admin token saved
    localStorage.setItem("adminToken", "admin.jwt");

    // Act: show the navbar
    renderPage("/page", <Navbar />);

    // Assert: sign out is shown
    expect(screen.getByRole("button", { name: "Sign Out" })).toBeInTheDocument();
  });

  /*
   * TEST 4: Sign Out
   * Checks that:
   *   - Both the client and admin tokens are removed
   *   - The user is sent to the Home page
   */
  test("Sign Out removes tokens and goes to Home", async () => {
    // Arrange: both tokens saved, show the navbar on /page
    localStorage.setItem("authToken", "client.jwt");
    localStorage.setItem("adminToken", "admin.jwt");
    renderPage("/page", <Navbar />);

    // Act: click "Sign Out"
    await userEvent.click(screen.getByRole("button", { name: "Sign Out" }));

    // Assert: tokens removed and user is on the Home page
    expect(localStorage.getItem("authToken")).toBeNull();
    expect(localStorage.getItem("adminToken")).toBeNull();
    expect(await screen.findByText("Landed on /")).toBeInTheDocument();
  });

  /*
   * TEST 5: Navigation links
   * Checks that:
   *   - Home, About Casey, Services & Supports, Contact and Intake links exist
   *   - Each link points to the correct page
   */
  test("has all main navigation links", () => {
    // Arrange + Act: show the navbar
    renderPage("/page", <Navbar />);

    // Assert: every main link has the correct address
    const nav = screen.getByRole("navigation", { name: "Main navigation" });
    const linkFor = (name: string) =>
      Array.from(nav.querySelectorAll("a")).find((a) => a.textContent?.trim() === name);

    expect(linkFor("Home")).toHaveAttribute("href", "/");
    expect(linkFor("About Casey")).toHaveAttribute("href", "/about");
    expect(linkFor("Services & Supports")).toHaveAttribute("href", "/services");
    expect(linkFor("Contact")).toHaveAttribute("href", "/contact");
    expect(linkFor("Intake")).toHaveAttribute("href", "/intake");
  });
});
