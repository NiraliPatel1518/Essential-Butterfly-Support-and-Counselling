import { afterEach, describe, expect, test, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AdminLoginOtpModal from "./AdminLoginOtpModal";

/*
 * Admin sign-in code window (step 2 of the admin login).
 *
 * This file has 11 tests:
 *   1  - The window shows the hidden email address
 *   2  - Correct code -> the sign-in token is passed on
 *   3  - Wrong code -> the error from the backend is shown
 *   4  - A JSON error from the backend is shown as a plain message
 *   5  - A bad token from the backend is refused
 *   6  - Code shorter than 6 digits -> error, nothing is sent
 *   7  - Only digits can be typed
 *   8  - Cancel button
 *   9  - Resend: 60 second wait, then the code can be sent again
 *   10 - Resend: the wait time from the backend is shown
 *   11 - Network failure -> error is shown
 *
 * The backend is NOT needed. fetch is replaced with a fake.
 */

const TOKEN = "header.payload.signature";

// A fake backend answer. The component reads it with response.text().
function reply(ok: boolean, body: string, status = ok ? 200 : 400) {
  return Promise.resolve({ ok, status, text: () => Promise.resolve(body) });
}

function stubFetch(...answers: Array<Promise<unknown>>) {
  const fetchMock = vi.fn();
  answers.forEach((answer) => fetchMock.mockReturnValueOnce(answer));
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function setup() {
  const onVerified = vi.fn();
  const onCancel = vi.fn();
  render(
    <AdminLoginOtpModal
      challengeToken="7.secret"
      maskedEmail="ad***@test.com"
      onVerified={onVerified}
      onCancel={onCancel}
    />
  );
  return { onVerified, onCancel };
}

async function enterCode(code: string) {
  const user = userEvent.setup();
  await user.type(screen.getByPlaceholderText("Enter code"), code);
  await user.click(screen.getByRole("button", { name: "Verify and continue" }));
}

// Moves the clock forward one second at a time, so the countdown re-draws.
async function waitSeconds(seconds: number) {
  for (let i = 0; i < seconds; i++) {
    await act(async () => {
      vi.advanceTimersByTime(1000);
    });
  }
}

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe("AdminLoginOtpModal", () => {
  test("shows the hidden email address the code was sent to", () => {
    stubFetch();
    setup();

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText("ad***@test.com")).toBeInTheDocument();
  });

  test("passes the token on after the correct code", async () => {
    const fetchMock = stubFetch(reply(true, TOKEN));
    const { onVerified } = setup();

    await enterCode("123456");

    expect(fetchMock.mock.calls[0][0]).toBe(
      "http://localhost:8080/api/admin-auth/login-otp/verify"
    );
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
      challengeToken: "7.secret",
      code: "123456",
    });
    await vi.waitFor(() => expect(onVerified).toHaveBeenCalledWith(TOKEN));
  });

  test("shows the backend error when the code is wrong", async () => {
    stubFetch(reply(false, "The verification code is incorrect. 4 attempt(s) remaining."));
    const { onVerified } = setup();

    await enterCode("000000");

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "The verification code is incorrect. 4 attempt(s) remaining."
    );
    expect(onVerified).not.toHaveBeenCalled();
  });

  test("shows the message of a JSON error", async () => {
    stubFetch(reply(false, JSON.stringify({ message: "Your verification code has expired." })));
    setup();

    await enterCode("000000");

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Your verification code has expired."
    );
  });

  test("refuses a token that does not look like a token", async () => {
    stubFetch(reply(true, "not-a-token"));
    const { onVerified } = setup();

    await enterCode("123456");

    expect(await screen.findByRole("alert")).toHaveTextContent(/invalid sign-in token/i);
    expect(onVerified).not.toHaveBeenCalled();
  });

  test("asks for 6 digits and sends nothing when the code is short", async () => {
    const fetchMock = stubFetch();
    setup();

    await enterCode("123");

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Enter the 6-digit verification code."
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });

  test("only accepts digits in the code box", async () => {
    stubFetch();
    setup();

    await userEvent.setup().type(screen.getByPlaceholderText("Enter code"), "12ab34");

    expect(screen.getByPlaceholderText("Enter code")).toHaveValue("1234");
  });

  test("calls onCancel when the admin returns to login", async () => {
    stubFetch();
    const { onCancel } = setup();

    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: /cancel and return to login/i }));

    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  test("lets the admin resend the code after the 60 second wait", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const fetchMock = stubFetch(reply(true, "A new verification code has been sent."));
    setup();

    // during the wait there is no resend button
    expect(screen.getByText("Resend available in 60s")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Resend code" })).not.toBeInTheDocument();

    await waitSeconds(60);
    await userEvent
      .setup({ advanceTimers: vi.advanceTimersByTime.bind(vi) })
      .click(screen.getByRole("button", { name: "Resend code" }));

    expect(fetchMock.mock.calls[0][0]).toBe(
      "http://localhost:8080/api/admin-auth/login-otp/resend"
    );
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ challengeToken: "7.secret" });
    expect(await screen.findByRole("status")).toHaveTextContent(
      "A new verification code has been sent."
    );
    // the next wait is longer
    expect(screen.getByText("Resend available in 90s")).toBeInTheDocument();
  });

  test("shows the wait time sent by the backend when resending too early", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    stubFetch(reply(false, "Please wait 25 second(s) before requesting another code."));
    setup();

    await waitSeconds(60);
    await userEvent
      .setup({ advanceTimers: vi.advanceTimersByTime.bind(vi) })
      .click(screen.getByRole("button", { name: "Resend code" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Please wait 25 second(s)");
    expect(screen.getByText("Resend available in 25s")).toBeInTheDocument();
  });

  test("shows an error when the server cannot be reached", async () => {
    const fetchMock = vi.fn().mockRejectedValueOnce(new Error("Network down"));
    vi.stubGlobal("fetch", fetchMock);
    setup();

    await enterCode("123456");

    expect(await screen.findByRole("alert")).toHaveTextContent("Network down");
  });
});
