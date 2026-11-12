import { beforeEach, describe, expect, test, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import App from "./App";
import { jsonResponse } from "./test/render";

/*
 * The whole website: pages, redirects, page titles and the navigation bar.
 * App uses the real browser address, so every test sets the address first.
 *
 * This file has 14 tests:
 *   1  - Home page opens at "/"
 *   2  - Unknown address -> "Page not found"
 *   3  - Information page opens (Privacy)
 *   4  - Page title changes with the page
 *   5  - Screen readers are told when the page changes
 *   6  - "Skip to main content" link
 *   7  - /admin-login goes to /login
 *   8  - Old admin forgot-password address goes to /forgot-password
 *   9  - Old admin reset-password address keeps the reset token
 *   10 - Private pages send a visitor to /login (3 tests: client dashboard,
 *        admin dashboard, intake)
 *   11 - A client sees the client dashboard without the website menu and footer
 *   12 - Public pages show the website menu and footer
 *
 * The backend is NOT needed. fetch is replaced with a fake.
 */

function clientToken() {
  const payload = btoa(
    JSON.stringify({ role: "CLIENT", exp: Math.floor(Date.now() / 1000) + 3600 })
  ).replace(/=/g, "");
  return `header.${payload}.signature`;
}

function openApp(path: string) {
  window.history.pushState({}, "", path);
  return render(<App />);
}

beforeEach(() => {
  // jsdom cannot scroll, so the page-change code gets a harmless fake
  vi.spyOn(window, "scrollTo").mockImplementation(() => {});
  // every request gets an empty list
  vi.stubGlobal("fetch", vi.fn(() => jsonResponse([])));
});

describe("App", () => {
  test("opens the home page at /", () => {
    openApp("/");

    expect(document.querySelector("#main-content main")).toBeInTheDocument();
    expect(document.title).toBe("Home | Essential Butterfly");
  });

  test("shows 'Page not found' for an unknown address", () => {
    openApp("/this-page-does-not-exist");

    expect(screen.getByRole("heading", { name: "Page not found" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Return to the home page" })).toHaveAttribute(
      "href",
      "/"
    );
    expect(document.title).toBe("Page Not Found | Essential Butterfly");
  });

  test("opens an information page", () => {
    openApp("/privacy");

    expect(screen.getByRole("heading", { level: 1 })).toBeInTheDocument();
    expect(document.title).toBe("Privacy | Essential Butterfly");
  });

  test("changes the page title with the page", () => {
    openApp("/login");
    expect(document.title).toBe("Sign In | Essential Butterfly");
  });

  test("tells screen readers when the page changes", () => {
    openApp("/accessibility");

    const announcement = document.querySelector('[aria-live="polite"]');
    expect(announcement).toHaveTextContent("Page changed to Accessibility");
  });

  test("has a skip link to the main content", () => {
    openApp("/");

    expect(screen.getByRole("link", { name: "Skip to main content" })).toHaveAttribute(
      "href",
      "#main-content"
    );
    expect(document.getElementById("main-content")).toBeInTheDocument();
  });

  test("sends /admin-login to /login", () => {
    openApp("/admin-login");

    expect(window.location.pathname).toBe("/login");
  });

  test("sends the old admin forgot-password address to /forgot-password", () => {
    openApp("/admin/forgot-password");

    expect(window.location.pathname).toBe("/forgot-password");
  });

  test("keeps the reset token when the old admin reset address is used", () => {
    openApp("/admin/reset-password?token=abc123");

    expect(window.location.pathname).toBe("/reset-password");
    expect(window.location.search).toBe("?token=abc123");
  });

  test.each(["/client-dashboard", "/admin-dashboard", "/intake"])(
    "sends a visitor from %s to /login",
    (path) => {
      openApp(path);

      expect(window.location.pathname).toBe("/login");
    }
  );

  test("hides the website menu and footer on the client dashboard", () => {
    localStorage.setItem("authToken", clientToken());
    openApp("/client-dashboard");

    expect(window.location.pathname).toBe("/client-dashboard");
    expect(document.querySelector("header.site-navbar")).not.toBeInTheDocument();
    expect(document.querySelector("footer.site-footer")).not.toBeInTheDocument();
  });

  test("shows the website menu and footer on public pages", () => {
    openApp("/");

    expect(document.querySelector("header.site-navbar")).toBeInTheDocument();
    expect(document.querySelector("footer.site-footer")).toBeInTheDocument();
  });
});
