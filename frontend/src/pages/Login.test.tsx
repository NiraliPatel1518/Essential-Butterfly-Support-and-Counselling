import { describe, it, expect, vi, afterEach } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Login from "./Login";
import { renderApp } from "../test/render";

afterEach(() => {
  vi.unstubAllGlobals();
  localStorage.clear();
});

function mockFetch() {
  return vi.fn().mockImplementation((url: string) => {
    if (String(url).includes("/api/features")) {
      return Promise.resolve({
        ok: true,
        json: async () => [],
      });
    }

    return Promise.resolve({
      ok: false,
      text: async () => "Invalid email or password",
    });
  });
}

describe("Unified Login", () => {
  it("renders unified login form", () => {
    vi.stubGlobal("fetch", mockFetch());

    renderApp("/login", <Login />);

    expect(
      screen.getByRole("heading", { name: /client login/i })
    ).toBeInTheDocument();

    expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
    expect(screen.getByLabelText("Password")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /^log in$/i })
    ).toBeInTheDocument();
  });

  it("does not submit empty credentials", async () => {
    const fetchMock = mockFetch();
    vi.stubGlobal("fetch", fetchMock);

    renderApp("/login", <Login />);

    await userEvent.click(
      screen.getByRole("button", { name: /^log in$/i })
    );

    expect(
      fetchMock.mock.calls.some(([url]) =>
        String(url).includes("/api/auth/login")
      )
    ).toBe(false);
  });

  it("shows backend authentication errors", async () => {
    const fetchMock = mockFetch();
    vi.stubGlobal("fetch", fetchMock);

    renderApp("/login", <Login />);

    await userEvent.type(
      screen.getByLabelText(/email address/i),
      "user@example.com"
    );

    await userEvent.type(
      screen.getByLabelText("Password"),
      "WrongPassword1"
    );

    await userEvent.click(
      screen.getByRole("button", { name: /^log in$/i })
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      /invalid email or password/i
    );
  });
});