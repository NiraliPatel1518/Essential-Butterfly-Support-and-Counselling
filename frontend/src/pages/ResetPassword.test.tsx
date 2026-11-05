import { describe, it, expect, vi, afterEach } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import ResetPassword from "./ResetPassword";
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

describe("ResetPassword", () => {
  it("renders reset form", () => {
    vi.stubGlobal("fetch", mockFetch());

    renderApp("/reset-password?token=abc", <ResetPassword />);

    expect(
      screen.getByRole("heading", { name: /reset your password/i })
    ).toBeInTheDocument();
  });

  it("rejects a missing token", async () => {
    const fetchMock = mockFetch();
    vi.stubGlobal("fetch", fetchMock);

    renderApp("/reset-password", <ResetPassword />);

    await userEvent.type(
      screen.getByLabelText(/new password/i),
      "Password123"
    );

    await userEvent.type(
      screen.getByLabelText(/confirm password/i),
      "Password123"
    );

    await userEvent.click(
      screen.getByRole("button", { name: /reset password/i })
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      /link is invalid/i
    );

    expect(
      fetchMock.mock.calls.some(([url]) =>
        String(url).includes("/api/password/reset")
      )
    ).toBe(false);
  });

  it("rejects mismatched passwords", async () => {
    const fetchMock = mockFetch();
    vi.stubGlobal("fetch", fetchMock);

    renderApp("/reset-password?token=abc", <ResetPassword />);

    await userEvent.type(
      screen.getByLabelText(/new password/i),
      "Password123"
    );

    await userEvent.type(
      screen.getByLabelText(/confirm password/i),
      "Different123"
    );

    await userEvent.click(
      screen.getByRole("button", { name: /reset password/i })
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      /passwords do not match/i
    );

    expect(
      fetchMock.mock.calls.some(([url]) =>
        String(url).includes("/api/password/reset")
      )
    ).toBe(false);
  });

  it("submits valid password reset", async () => {
    const fetchMock = mockFetch();
    vi.stubGlobal("fetch", fetchMock);

    renderApp("/reset-password?token=abc", <ResetPassword />);

    await userEvent.type(
      screen.getByLabelText(/new password/i),
      "Password123"
    );

    await userEvent.type(
      screen.getByLabelText(/confirm password/i),
      "Password123"
    );

    await userEvent.click(
      screen.getByRole("button", { name: /reset password/i })
    );

    expect(await screen.findByRole("status")).toHaveTextContent(
      /reset successfully/i
    );

    const resetRequest = fetchMock.mock.calls.find(([url]) =>
      String(url).includes("/api/password/reset")
    );

    expect(resetRequest).toBeDefined();

    expect(JSON.parse(resetRequest?.[1].body)).toEqual({
      token: "abc",
      newPassword: "Password123",
      confirmPassword: "Password123",
    });
  });
});