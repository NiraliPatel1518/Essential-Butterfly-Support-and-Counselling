import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AdminAccountSettings from "./AdminAccountSettings";

/*
 * Admin account card: shows the admin login email and lets the admin
 * change it. The new address must be confirmed with a 6-digit code.
 *
 * This file has 13 tests:
 *   1  - Profile is loaded with the admin token
 *   2  - Profile cannot be loaded -> error, Change email is disabled
 *   3  - Change email opens a window with the current email
 *   4  - Invalid email -> error, nothing is sent
 *   5  - Valid email -> code is requested, "verify" step opens
 *   6  - Email already used -> backend error, stays on the email step
 *   7  - Code shorter than 6 digits -> error, nothing is sent
 *   8  - Correct code -> new token saved, new email shown
 *   9  - Wrong code -> error, old token is kept
 *   10 - Bad token from the backend is refused
 *   11 - "Edit email" goes back to the email step
 *   12 - Resend code after the 60 second wait
 *   13 - Close button closes the window
 *
 * The backend is NOT needed. fetch is replaced with a fake.
 */

const OLD_TOKEN = "old.admin.token";
const NEW_TOKEN = "new.admin.token";

type Handler = (init?: RequestInit) => Promise<unknown>;

// A fake backend answer. The component reads it with text() or json().
function reply(ok: boolean, body: string, status = ok ? 200 : 400) {
  return Promise.resolve({
    ok,
    status,
    text: () => Promise.resolve(body),
    json: () => Promise.resolve(JSON.parse(body)),
  });
}

const profileReply = () =>
  reply(true, JSON.stringify({ fullName: "Admin", email: "admin@test.com" }));

// The fake backend picks the answer from the end of the URL.
function stubBackend(handlers: Record<string, Handler> = {}) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    const key = Object.keys(handlers).find((path) => url.endsWith(path));
    if (key) return handlers[key](init);
    if (url.endsWith("/profile")) return profileReply();
    return reply(false, "Unexpected request");
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function callsTo(fetchMock: ReturnType<typeof stubBackend>, path: string) {
  return fetchMock.mock.calls.filter(([url]) => String(url).endsWith(path));
}

async function openWindow() {
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "Change email" }));
  return user;
}

// Opens the window, types a new email and asks for the code.
async function requestCodeFor(email: string) {
  const user = await openWindow();
  const input = screen.getByLabelText("Email address");
  await user.clear(input);
  await user.type(input, email);
  await user.click(screen.getByRole("button", { name: "Send verification code" }));
  return user;
}

beforeEach(() => {
  localStorage.setItem("adminToken", OLD_TOKEN);
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  localStorage.clear();
});

describe("AdminAccountSettings", () => {
  test("loads the admin profile with the admin token", async () => {
    const fetchMock = stubBackend();
    render(<AdminAccountSettings />);

    expect(await screen.findByText("admin@test.com")).toBeInTheDocument();
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/admin-account/profile");
    expect(init?.headers).toEqual({ Authorization: `Bearer ${OLD_TOKEN}` });
  });

  test("shows an error and disables Change email when the profile cannot be loaded", async () => {
    stubBackend({ "/profile": () => reply(false, "Not allowed", 403) });
    render(<AdminAccountSettings />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Not allowed");
    expect(screen.getByText("Unavailable")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Change email" })).toBeDisabled();
  });

  test("opens a window with the current email filled in", async () => {
    stubBackend();
    render(<AdminAccountSettings />);

    await openWindow();

    expect(screen.getByRole("dialog")).toHaveTextContent("Change admin email");
    expect(screen.getByLabelText("Email address")).toHaveValue("admin@test.com");
  });

  test("refuses an invalid email and sends nothing", async () => {
    const fetchMock = stubBackend();
    render(<AdminAccountSettings />);

    await requestCodeFor("bad@x");

    expect(await screen.findByRole("alert")).toHaveTextContent("Enter a valid email address.");
    expect(callsTo(fetchMock, "/email-change/request")).toHaveLength(0);
  });

  test("requests a code for the new email and opens the verify step", async () => {
    const fetchMock = stubBackend({
      "/email-change/request": () => reply(true, "A verification code has been sent."),
    });
    render(<AdminAccountSettings />);

    await requestCodeFor("  New@Test.com ");

    expect(await screen.findByText("Verify your new email")).toBeInTheDocument();
    const [, init] = callsTo(fetchMock, "/email-change/request")[0];
    expect(JSON.parse(String(init?.body))).toEqual({ email: "new@test.com" });
    expect(screen.getByRole("status")).toHaveTextContent(
      "A verification code has been sent to new@test.com."
    );
  });

  test("shows the backend error and stays on the email step", async () => {
    stubBackend({
      "/email-change/request": () =>
        reply(false, "A client account already uses this email address. Choose a different email."),
    });
    render(<AdminAccountSettings />);

    await requestCodeFor("client@test.com");

    expect(await screen.findByRole("alert")).toHaveTextContent("A client account already uses");
    expect(screen.getByText("Change admin email")).toBeInTheDocument();
  });

  test("asks for 6 digits and sends nothing when the code is short", async () => {
    const fetchMock = stubBackend({ "/email-change/request": () => reply(true, "ok") });
    render(<AdminAccountSettings />);

    const user = await requestCodeFor("new@test.com");
    await user.type(await screen.findByPlaceholderText("Enter code"), "123");
    await user.click(screen.getByRole("button", { name: "Verify and update email" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Enter the 6-digit verification code."
    );
    expect(callsTo(fetchMock, "/email-change/verify")).toHaveLength(0);
  });

  test("saves the new token and shows the new email after the correct code", async () => {
    const authChanged = vi.fn();
    window.addEventListener("auth-changed", authChanged);
    const fetchMock = stubBackend({
      "/email-change/request": () => reply(true, "ok"),
      "/email-change/verify": () => reply(true, NEW_TOKEN),
    });
    render(<AdminAccountSettings />);

    const user = await requestCodeFor("new@test.com");
    await user.type(await screen.findByPlaceholderText("Enter code"), "123456");
    await user.click(screen.getByRole("button", { name: "Verify and update email" }));

    expect(await screen.findByText("new@test.com")).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      "Your admin email and login email have been updated successfully."
    );
    const [, init] = callsTo(fetchMock, "/email-change/verify")[0];
    expect(JSON.parse(String(init?.body))).toEqual({ code: "123456" });
    expect(localStorage.getItem("adminToken")).toBe(NEW_TOKEN);
    expect(authChanged).toHaveBeenCalled();
    window.removeEventListener("auth-changed", authChanged);
  });

  test("shows the error and keeps the old token when the code is wrong", async () => {
    stubBackend({
      "/email-change/request": () => reply(true, "ok"),
      "/email-change/verify": () =>
        reply(false, "The verification code is incorrect. 4 attempt(s) remaining."),
    });
    render(<AdminAccountSettings />);

    const user = await requestCodeFor("new@test.com");
    await user.type(await screen.findByPlaceholderText("Enter code"), "000000");
    await user.click(screen.getByRole("button", { name: "Verify and update email" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("4 attempt(s) remaining");
    expect(localStorage.getItem("adminToken")).toBe(OLD_TOKEN);
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  test("refuses a token that does not look like a token", async () => {
    stubBackend({
      "/email-change/request": () => reply(true, "ok"),
      "/email-change/verify": () => reply(true, "not-a-token"),
    });
    render(<AdminAccountSettings />);

    const user = await requestCodeFor("new@test.com");
    await user.type(await screen.findByPlaceholderText("Enter code"), "123456");
    await user.click(screen.getByRole("button", { name: "Verify and update email" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/invalid session token/i);
    expect(localStorage.getItem("adminToken")).toBe(OLD_TOKEN);
  });

  test("goes back to the email step with Edit email", async () => {
    stubBackend({ "/email-change/request": () => reply(true, "ok") });
    render(<AdminAccountSettings />);

    const user = await requestCodeFor("new@test.com");
    await user.click(await screen.findByRole("button", { name: "Edit email" }));

    expect(screen.getByText("Change admin email")).toBeInTheDocument();
    expect(screen.getByLabelText("Email address")).toHaveValue("new@test.com");
  });

  test("lets the admin resend the code after the 60 second wait", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const fetchMock = stubBackend({
      "/email-change/request": () => reply(true, "ok"),
      "/email-change/resend": () => reply(true, "A new verification code has been sent."),
    });
    render(<AdminAccountSettings />);

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime.bind(vi) });
    await user.click(await screen.findByRole("button", { name: "Change email" }));
    await user.clear(screen.getByLabelText("Email address"));
    await user.type(screen.getByLabelText("Email address"), "new@test.com");
    await user.click(screen.getByRole("button", { name: "Send verification code" }));

    expect(await screen.findByText("Resend available in 60s")).toBeInTheDocument();
    for (let i = 0; i < 60; i++) {
      await act(async () => {
        vi.advanceTimersByTime(1000);
      });
    }
    await user.click(screen.getByRole("button", { name: "Resend code" }));

    expect(await screen.findByText("Resend available in 90s")).toBeInTheDocument();
    expect(callsTo(fetchMock, "/email-change/resend")).toHaveLength(1);
    expect(screen.getByRole("status")).toHaveTextContent("A new verification code has been sent.");
  });

  test("closes the window with the close button", async () => {
    stubBackend();
    render(<AdminAccountSettings />);

    const user = await openWindow();
    await user.click(screen.getByRole("button", { name: "Close" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
