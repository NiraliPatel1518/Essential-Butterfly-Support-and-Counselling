
import {
  beforeEach,
  afterEach,
  describe,
  expect,
  test,
  vi,
} from "vitest";

import {
  render,
  screen,
  waitFor,
} from "@testing-library/react";

import userEvent from "@testing-library/user-event";

import {
  MemoryRouter,
  useLocation,
} from "react-router-dom";

import Navbar from "./Navbar";
import { PublicFeatureProvider } from "../context/PublicFeatureContext";

function CurrentLocation() {
  const location = useLocation();

  return (
    <span data-testid="current-location">
      {location.pathname}
    </span>
  );
}

function renderNavbar(initialPath = "/") {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <PublicFeatureProvider>
        <Navbar />
        <CurrentLocation />
      </PublicFeatureProvider>
    </MemoryRouter>
  );
}

beforeEach(() => {
  localStorage.clear();

  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve([]),
    })
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("Navbar", () => {
  test("shows Client Login when nobody is logged in", async () => {
    renderNavbar();

    const loginLink = screen.getByRole("link", {
      name: /client login/i,
    });

    expect(loginLink).toHaveAttribute("href", "/login");

    expect(
      screen.queryByRole("button", { name: /sign out/i })
    ).not.toBeInTheDocument();
  });

  test("shows My Dashboard and Sign Out when a client is logged in", () => {
    localStorage.setItem("authToken", "client-test-token");

    renderNavbar();

    expect(
      screen.getByRole("link", { name: /my dashboard/i })
    ).toHaveAttribute("href", "/client-dashboard");

    expect(
      screen.getByRole("button", { name: /sign out/i })
    ).toBeInTheDocument();
  });

  test("shows Admin Dashboard and Sign Out when an admin is logged in", () => {
    localStorage.setItem("adminToken", "admin-test-token");

    renderNavbar();

    expect(
      screen.getByRole("link", { name: /admin dashboard/i })
    ).toHaveAttribute("href", "/admin-dashboard");

    expect(
      screen.getByRole("button", { name: /sign out/i })
    ).toBeInTheDocument();
  });

  test("sign out removes both tokens and navigates home", async () => {
    const user = userEvent.setup();

    localStorage.setItem("authToken", "client-test-token");
    localStorage.setItem("adminToken", "admin-test-token");

    renderNavbar("/about");

    await user.click(
      screen.getByRole("button", { name: /sign out/i })
    );

    expect(localStorage.getItem("authToken")).toBeNull();
    expect(localStorage.getItem("adminToken")).toBeNull();

    expect(
      screen.getByTestId("current-location")
    ).toHaveTextContent("/");
  });

  test("renders all main navigation links", () => {
    renderNavbar();

    expect(
      screen.getByRole("link", { name: "Home" })
    ).toHaveAttribute("href", "/");

    expect(
      screen.getByRole("link", { name: "About Casey" })
    ).toHaveAttribute("href", "/about");

    expect(
      screen.getByRole("link", { name: "Services" })
    ).toHaveAttribute("href", "/services");

    expect(
      screen.getByRole("link", { name: "Contact" })
    ).toHaveAttribute("href", "/contact");
  });

  test("shows Updates when the backend enables the feature", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        json: () =>
          Promise.resolve([
            {
              featureKey: "UPDATES",
              label: "Updates",
              enabled: true,
              displayOrder: 1,
            },
          ]),
      })
    );

    renderNavbar();

    expect(
      await screen.findByRole("link", { name: "Updates" })
    ).toHaveAttribute("href", "/updates");
  });

  test("hides Updates when the backend disables the feature", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        json: () =>
          Promise.resolve([
            {
              featureKey: "UPDATES",
              label: "Updates",
              enabled: false,
              displayOrder: 1,
            },
          ]),
      })
    );

    renderNavbar();

    await waitFor(() => {
      expect(
        screen.queryByRole("link", { name: "Updates" })
      ).not.toBeInTheDocument();
    });
  });
});
