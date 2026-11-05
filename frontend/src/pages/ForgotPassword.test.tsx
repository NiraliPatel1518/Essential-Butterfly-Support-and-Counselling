import { describe, it, expect, vi, afterEach } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import ForgotPassword from "./ForgotPassword";
import { renderApp } from "../test/render";

afterEach(() => {
  vi.unstubAllGlobals();
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
      ok: true,
      text: async () => "",
    });
  });
}

describe("ForgotPassword", () => {
  it("renders recovery form", () => {
    vi.stubGlobal("fetch", mockFetch());

    renderApp("/forgot-password", <ForgotPassword />);

    expect(
      screen.getByRole("heading", { name: /forgot your password/i })
    ).toBeInTheDocument();

    expect(
      screen.getByRole("button", { name: /send reset link/i })
    ).toBeInTheDocument();
  });

  it("does not send a reset request without an email", async () => {
    const fetchMock = mockFetch();
    vi.stubGlobal("fetch", fetchMock);

    renderApp("/forgot-password", <ForgotPassword />);

    await userEvent.click(
      screen.getByRole("button", { name: /send reset link/i })
    );

    expect(
      fetchMock.mock.calls.some(([url]) =>
        String(url).includes("/api/password/forgot")
      )
    ).toBe(false);
  });

  it("sends normalized email and shows confirmation", async () => {
    const fetchMock = mockFetch();
    vi.stubGlobal("fetch", fetchMock);

    renderApp("/forgot-password", <ForgotPassword />);

    await userEvent.type(
      screen.getByLabelText(/email address/i),
      "USER@EXAMPLE.COM"
    );

    await userEvent.click(
      screen.getByRole("button", { name: /send reset link/i })
    );

    expect(await screen.findByRole("status")).toHaveTextContent(
      /if an account exists/i
    );

    const resetRequest = fetchMock.mock.calls.find(([url]) =>
      String(url).includes("/api/password/forgot")
    );

    expect(resetRequest).toBeDefined();
    expect(String(resetRequest?.[0])).toContain(
      "email=user%40example.com"
    );
  });
});