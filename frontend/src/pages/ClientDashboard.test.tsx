import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import ClientDashboard from "./ClientDashboard";
import { renderApp, jsonResponse } from "../test/render";

type Reply = () => Promise<unknown>;

// Answers the calls the dashboard makes. Anything else gets an empty list.
function stubApi(
  replies: { status?: Reply; me?: Reply; phone?: Reply; verifyPhone?: Reply } = {}
) {
  const fetchMock = vi.fn((url: string, _init?: RequestInit) => {
      if (url.includes("/api/phone-verification/status")) {
        return (replies.phone ?? (() => jsonResponse([])))();
      }
      if (url.includes("/api/phone-verification/verify")) {
        return (
          replies.verifyPhone ??
          (() => jsonResponse("Phone number verified successfully."))
        )();
      }
      if (url.includes("/api/intake/status")) {
        return (replies.status ?? (() => jsonResponse({}, false, 404)))();
      }
      if (url.includes("/api/auth/me")) {
        return (replies.me ?? (() => jsonResponse({ fullName: "Alex Lee" })))();
      }
      return jsonResponse([]);
    });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

beforeEach(() => {
  localStorage.clear();
  localStorage.setItem("authToken", "test-token");
});

afterEach(() => {
  vi.unstubAllGlobals();
  localStorage.clear();
});

describe("ClientDashboard", () => {
  it("sends a visitor without a token to the login page", async () => {
    localStorage.clear();
    stubApi();
    renderApp("/dashboard", <ClientDashboard />);

    expect(await screen.findByText("Landed on /login")).toBeInTheDocument();
  });

  it("greets the client by first name", async () => {
    stubApi({ me: () => jsonResponse({ fullName: "Alex Lee" }) });
    renderApp("/dashboard", <ClientDashboard />);

    expect(
      await screen.findByRole("heading", { name: /welcome back, alex!/i })
    ).toBeInTheDocument();
  });

  it("tells a new client that no intake request exists yet", async () => {
    stubApi();
    renderApp("/dashboard", <ClientDashboard />);

    expect(
      await screen.findByText(/you haven't submitted an intake request yet/i)
    ).toBeInTheDocument();
  });

  it("shows the review status of a submitted intake request", async () => {
    stubApi({ status: () => jsonResponse({ status: "IN_PROGRESS" }) });
    renderApp("/dashboard", <ClientDashboard />);

    expect(
      await screen.findByRole("heading", { name: "Under Review" })
    ).toBeInTheDocument();
    expect(
      screen.getByText("Your intake request is currently being reviewed.")
    ).toBeInTheDocument();
  });

  it("shows the rejection reason when a request is rejected", async () => {
    stubApi({
      status: () =>
        jsonResponse({
          status: "REJECTED",
          rejectionReason: "We are not taking new clients this month.",
        }),
    });
    renderApp("/dashboard", <ClientDashboard />);

    expect(
      await screen.findByRole("heading", { name: /reason for rejection/i })
    ).toBeInTheDocument();
    expect(
      screen.getByText("We are not taking new clients this month.")
    ).toBeInTheDocument();
  });

  it("logs the client out and goes to login when the session has expired", async () => {
    stubApi({
      status: () => jsonResponse({}, false, 401),
      me: () => jsonResponse({}, false, 401),
    });
    renderApp("/dashboard", <ClientDashboard />);

    expect(
      await screen.findByText("Landed on /login?sessionExpired=true")
    ).toBeInTheDocument();
    expect(localStorage.getItem("authToken")).toBeNull();
  });

  it("shows a retry message when the server fails", async () => {
    stubApi({ status: () => jsonResponse("Server down", false, 500) });
    renderApp("/dashboard", <ClientDashboard />);

    expect(await screen.findByRole("alert")).toHaveTextContent(/server down/i);
    expect(
      screen.getByRole("button", { name: /try again/i })
    ).toBeInTheDocument();
  });

  it("removes the token and returns to login on sign out", async () => {
    stubApi();
    const user = userEvent.setup();
    renderApp("/dashboard", <ClientDashboard />);

    await user.click(await screen.findByRole("button", { name: /my account/i }));
    await user.click(screen.getByRole("button", { name: /sign out/i }));

    expect(await screen.findByText("Landed on /login")).toBeInTheDocument();
    expect(localStorage.getItem("authToken")).toBeNull();
  });
});

describe("ClientDashboard phone verification", () => {
  const unverified = { phoneVerified: false, phoneNumber: null, pendingPhoneNumber: null };
  const verified = { phoneVerified: true, phoneNumber: "+14165551234", pendingPhoneNumber: null };

  async function openPhoneItem() {
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: /my account/i }));
    await user.click(screen.getByRole("button", { name: /phone verified/i }));
    return user;
  }

  it("asks the phone status with the client token", async () => {
    const fetchMock = stubApi({ phone: () => jsonResponse(unverified) });
    renderApp("/dashboard", <ClientDashboard />);

    await screen.findByLabelText("Phone verification required");
    const call = fetchMock.mock.calls.find(([url]) =>
      String(url).endsWith("/api/phone-verification/status")
    );
    expect(call).toBeDefined();
    expect(call![1]?.headers).toEqual({ Authorization: "Bearer test-token" });
  });

  it("shows a warning dot on My Account while the phone is not verified", async () => {
    stubApi({ phone: () => jsonResponse(unverified) });
    renderApp("/dashboard", <ClientDashboard />);

    expect(await screen.findByLabelText("Phone verification required")).toBeInTheDocument();
  });

  it("shows no warning dot when the phone is verified", async () => {
    stubApi({ phone: () => jsonResponse(verified) });
    renderApp("/dashboard", <ClientDashboard />);

    await screen.findByRole("heading", { name: /welcome back, alex!/i });
    expect(screen.queryByLabelText("Phone verification required")).not.toBeInTheDocument();
  });

  it("shows a verification-required mark in the account menu for an unverified phone", async () => {
    stubApi({ phone: () => jsonResponse(unverified) });
    const user = userEvent.setup();
    renderApp("/dashboard", <ClientDashboard />);

    await user.click(await screen.findByRole("button", { name: /my account/i }));

    expect(screen.getByLabelText("Verification required")).toBeInTheDocument();
  });

  it("shows a verified mark in the account menu for a verified phone", async () => {
    stubApi({ phone: () => jsonResponse(verified) });
    const user = userEvent.setup();
    renderApp("/dashboard", <ClientDashboard />);

    await user.click(await screen.findByRole("button", { name: /my account/i }));

    expect(await screen.findByLabelText("Verified")).toBeInTheDocument();
  });

  it("opens the phone verification window from the account menu", async () => {
    stubApi({ phone: () => jsonResponse(unverified) });
    renderApp("/dashboard", <ClientDashboard />);

    await openPhoneItem();

    expect(screen.getByRole("dialog")).toHaveTextContent("Verify your phone number");
  });

  it("offers to update the number when the phone is already verified", async () => {
    stubApi({ phone: () => jsonResponse(verified) });
    renderApp("/dashboard", <ClientDashboard />);

    await openPhoneItem();

    expect(screen.getByRole("dialog")).toHaveTextContent("Update your phone number");
  });

  it("continues on the code step when a number is waiting for its code", async () => {
    stubApi({
      phone: () =>
        jsonResponse({ phoneVerified: false, phoneNumber: null, pendingPhoneNumber: "+14165559999" }),
    });
    renderApp("/dashboard", <ClientDashboard />);

    await openPhoneItem();

    expect(screen.getByLabelText("6-digit verification code")).toBeInTheDocument();
    expect(screen.getByText("+14165559999")).toBeInTheDocument();
  });

  it("closes the window and removes the warning dot after the phone is verified", async () => {
    stubApi({
      phone: () =>
        jsonResponse({ phoneVerified: false, phoneNumber: null, pendingPhoneNumber: "+14165559999" }),
    });
    renderApp("/dashboard", <ClientDashboard />);

    const user = await openPhoneItem();
    await user.type(screen.getByLabelText("6-digit verification code"), "123456");
    await user.click(screen.getByRole("button", { name: "Verify phone number" }));

    await vi.waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(screen.queryByLabelText("Phone verification required")).not.toBeInTheDocument();
  });

  it("closes the window with the close button", async () => {
    stubApi({ phone: () => jsonResponse(unverified) });
    renderApp("/dashboard", <ClientDashboard />);

    const user = await openPhoneItem();
    await user.click(screen.getByRole("button", { name: "Close phone verification" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("goes to login when the phone status says the session has expired", async () => {
    stubApi({ phone: () => jsonResponse({}, false, 401) });
    renderApp("/dashboard", <ClientDashboard />);

    expect(await screen.findByText("Landed on /login?sessionExpired=true")).toBeInTheDocument();
    expect(localStorage.getItem("authToken")).toBeNull();
  });

  it("still shows the dashboard when the phone status cannot be loaded", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    stubApi({ phone: () => jsonResponse("Server down", false, 500) });
    renderApp("/dashboard", <ClientDashboard />);

    expect(
      await screen.findByRole("heading", { name: /welcome back, alex!/i })
    ).toBeInTheDocument();
    expect(screen.queryByLabelText("Phone verification required")).not.toBeInTheDocument();
  });
});
