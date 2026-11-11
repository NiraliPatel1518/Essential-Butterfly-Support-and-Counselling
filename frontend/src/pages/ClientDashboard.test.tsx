import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import ClientDashboard from "./ClientDashboard";
import { renderApp, jsonResponse } from "../test/render";

type Reply = () => Promise<unknown>;

// Answers the three calls the dashboard makes. Anything else gets an empty list.
function stubApi(replies: { status?: Reply; me?: Reply } = {}) {
  vi.stubGlobal(
    "fetch",
    vi.fn((url: string) => {
      if (url.includes("/api/intake/status")) {
        return (replies.status ?? (() => jsonResponse({}, false, 404)))();
      }
      if (url.includes("/api/auth/me")) {
        return (replies.me ?? (() => jsonResponse({ fullName: "Alex Lee" })))();
      }
      return jsonResponse([]);
    })
  );
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