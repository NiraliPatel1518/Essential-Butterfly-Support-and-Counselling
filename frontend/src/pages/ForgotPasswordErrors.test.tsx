import { describe, it, expect, vi, afterEach } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import ForgotPassword from "./ForgotPassword";
import { renderApp, jsonResponse } from "../test/render";

/*
 * Forgot password: error messages and form help.
 *
 * The page now reads the error from the backend in three ways:
 *   - JSON with "message"  -> shows the message
 *   - JSON with "error"    -> shows the error text
 *   - plain text           -> shows the text
 *   - empty answer         -> shows a friendly default message
 * The email field also tells screen readers about the help text and the error.
 */

afterEach(() => {
  vi.unstubAllGlobals();
});

// The reset request fails with the given body. Website features are an empty list.
function failResetWith(body: unknown, status = 400) {
  const fetchMock = vi.fn((url: string) =>
    String(url).includes("/api/features")
      ? jsonResponse([])
      : jsonResponse(body, false, status)
  );
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

async function submitEmail() {
  await userEvent.type(
    screen.getByLabelText(/email address/i),
    "user@example.com"
  );
  await userEvent.click(
    screen.getByRole("button", { name: /send reset link/i })
  );
}

describe("ForgotPassword errors", () => {
  it("shows the message from a JSON answer", async () => {
    failResetWith({ message: "Please enter a valid email address." });
    renderApp("/forgot-password", <ForgotPassword />);

    await submitEmail();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Please enter a valid email address."
    );
  });

  it("shows the error text when the JSON answer has no message", async () => {
    failResetWith({ error: "Too many requests" }, 429);
    renderApp("/forgot-password", <ForgotPassword />);

    await submitEmail();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Too many requests"
    );
  });

  it("shows plain text answers as they are", async () => {
    failResetWith("Invalid account type");
    renderApp("/forgot-password", <ForgotPassword />);

    await submitEmail();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Invalid account type"
    );
  });

  it("shows a friendly message when the answer is empty", async () => {
    failResetWith("", 500);
    renderApp("/forgot-password", <ForgotPassword />);

    await submitEmail();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Unable to process your request. Please try again."
    );
  });

  it("shows help text and links the error to the email field", async () => {
    failResetWith({ message: "Please enter a valid email address." });
    renderApp("/forgot-password", <ForgotPassword />);

    const email = screen.getByLabelText(/email address/i);
    expect(
      screen.getByText("Use the email address connected to your account.")
    ).toBeInTheDocument();
    expect(email).toHaveAttribute("aria-invalid", "false");
    expect(email.getAttribute("aria-describedby")).toBe(
      "forgot-password-help"
    );

    await submitEmail();

    const alert = await screen.findByRole("alert");
    expect(email).toHaveAttribute("aria-invalid", "true");
    expect(email.getAttribute("aria-describedby")).toContain(alert.id);
  });
});
