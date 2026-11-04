import {
  beforeEach,
  describe,
  expect,
  test,
} from "vitest";

import {
  render,
  screen,
} from "@testing-library/react";

import userEvent from "@testing-library/user-event";

import {
  MemoryRouter,
  useLocation,
} from "react-router-dom";

import Navbar from "./Navbar";

function CurrentLocation() {
  const location = useLocation();

  return (
    <span data-testid="current-location">
      {location.pathname}
    </span>
  );
}

function renderNavbar(
  initialPath = "/"
) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Navbar />
      <CurrentLocation />
    </MemoryRouter>
  );
}

describe("Navbar", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  test(
    "shows Client Login when nobody is logged in",
    () => {
      renderNavbar();

      const clientLoginLink =
        screen.getByRole("link", {
          name: /client login/i,
        });

      expect(clientLoginLink).toBeInTheDocument();

      expect(clientLoginLink).toHaveAttribute(
        "href",
        "/login"
      );

      expect(
        screen.queryByRole("button", {
          name: /sign out/i,
        })
      ).not.toBeInTheDocument();
    }
  );

  test(
    "shows Sign Out when a client is logged in",
    () => {
      localStorage.setItem(
        "authToken",
        "client-test-token"
      );

      renderNavbar();

      expect(
        screen.getByRole("button", {
          name: /sign out/i,
        })
      ).toBeInTheDocument();

      expect(
        screen.queryByRole("link", {
          name: /client login/i,
        })
      ).not.toBeInTheDocument();
    }
  );

  test(
    "shows Sign Out when an admin is logged in",
    () => {
      localStorage.setItem(
        "adminToken",
        "admin-test-token"
      );

      renderNavbar();

      expect(
        screen.getByRole("button", {
          name: /sign out/i,
        })
      ).toBeInTheDocument();

      expect(
        screen.queryByRole("link", {
          name: /client login/i,
        })
      ).not.toBeInTheDocument();
    }
  );

  test(
    "Sign Out removes tokens and goes to Home",
    async () => {
      const user = userEvent.setup();

      localStorage.setItem(
        "authToken",
        "client-test-token"
      );

      localStorage.setItem(
        "adminToken",
        "admin-test-token"
      );

      renderNavbar("/about");

      await user.click(
        screen.getByRole("button", {
          name: /sign out/i,
        })
      );

      expect(
        localStorage.getItem("authToken")
      ).toBeNull();

      expect(
        localStorage.getItem("adminToken")
      ).toBeNull();

      expect(
        screen.getByTestId("current-location")
      ).toHaveTextContent("/");
    }
  );

  test(
    "has all main navigation links",
    () => {
      renderNavbar();

      expect(
        screen.getByRole("link", {
          name: "Home",
        })
      ).toHaveAttribute("href", "/");

      expect(
        screen.getByRole("link", {
          name: "About Casey",
        })
      ).toHaveAttribute("href", "/about");

      expect(
        screen.getByRole("link", {
          name: "Services",
        })
      ).toHaveAttribute("href", "/services");

      expect(
        screen.getByRole("link", {
          name: "Contact",
        })
      ).toHaveAttribute("href", "/contact");

      expect(
        screen.getByRole("link", {
          name: "Secure Intake",
        })
      ).toHaveAttribute("href", "/intake");
    }
  );
});