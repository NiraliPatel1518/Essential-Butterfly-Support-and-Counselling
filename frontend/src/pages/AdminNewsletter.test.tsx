import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AdminNewsletter from "./AdminNewsletter";
import { renderApp, jsonResponse } from "../test/render";

type Route = [string, RegExp, () => Promise<unknown>];

// The first route with the same method and a matching URL answers.
// Anything else gets an empty object.
function stubRoutes(routes: Route[]) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    const method = init?.method ?? "GET";
    const route = routes.find(([m, pattern]) => m === method && pattern.test(url));
    return route ? route[2]() : jsonResponse({});
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function campaign(overrides: Record<string, unknown> = {}) {
  return {
    id: 4,
    subject: "Spring news",
    content: "Hello everyone",
    status: "DRAFT",
    recipientCount: 0,
    successCount: 0,
    failedCount: 0,
    ...overrides,
  };
}

function overview(overrides: Record<string, unknown> = {}) {
  return {
    activeSubscribers: 3,
    subscribers: [
      { id: 1, email: "one@mail.com" },
      { id: 2, email: "two@mail.com" },
      { id: 3, email: "three@mail.com" },
    ],
    campaigns: [campaign()],
    ...overrides,
  };
}

const LIST = /\/api\/admin\/newsletter$/;

beforeEach(() => {
  localStorage.clear();
  localStorage.setItem("adminToken", "admin-token");
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  localStorage.clear();
});

describe("AdminNewsletter", () => {
  it("shows the active subscriber count and the subscriber emails", async () => {
    stubRoutes([["GET", LIST, () => jsonResponse(overview())]]);
    renderApp("/admin", <AdminNewsletter />);

    expect(await screen.findByText("one@mail.com")).toBeInTheDocument();
    expect(screen.getByText("three@mail.com")).toBeInTheDocument();
    expect(screen.getByText("Active subscribers").parentElement).toHaveTextContent("3");
  });

  it("explains when there are no subscribers or campaigns", async () => {
    stubRoutes([
      ["GET", LIST, () => jsonResponse({ activeSubscribers: 0, subscribers: [], campaigns: [] })],
    ]);
    renderApp("/admin", <AdminNewsletter />);

    expect(await screen.findByText("No active subscribers yet.")).toBeInTheDocument();
    expect(screen.getByText("No campaigns are available yet.")).toBeInTheDocument();
  });

  it("sends the admin token when loading", async () => {
    const fetchMock = stubRoutes([["GET", LIST, () => jsonResponse(overview())]]);
    renderApp("/admin", <AdminNewsletter />);
    await screen.findByText("one@mail.com");

    const call = fetchMock.mock.calls[0];
    expect(String(call[0])).toContain("/api/admin/newsletter");
    expect(JSON.stringify(call[1]?.headers)).toContain("Bearer admin-token");
  });

  it("shows the server message when the data cannot be loaded", async () => {
    stubRoutes([["GET", LIST, () => jsonResponse({ message: "Not allowed" }, false, 403)]]);
    renderApp("/admin", <AdminNewsletter />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Not allowed");
  });

  it("saves a new draft and then edits it", async () => {
    const fetchMock = stubRoutes([
      ["POST", LIST, () => jsonResponse(campaign({ id: 9, subject: "Hello", content: "World" }))],
      ["GET", LIST, () => jsonResponse(overview({ campaigns: [] }))],
    ]);
    const user = userEvent.setup();
    renderApp("/admin", <AdminNewsletter />);
    await screen.findByText("Create a newsletter");

    await user.type(screen.getByLabelText("Email subject"), "Hello");
    await user.type(screen.getByLabelText("Newsletter content"), "World");
    await user.click(screen.getByRole("button", { name: "Save draft" }));

    expect(await screen.findByRole("status")).toHaveTextContent("Draft saved successfully.");
    expect(screen.getByText("Edit draft #9")).toBeInTheDocument();

    const post = fetchMock.mock.calls.find(([, init]) => init?.method === "POST");
    expect(JSON.parse(String(post![1]?.body))).toEqual({ subject: "Hello", content: "World" });
  });

  it("loads a draft into the editor and saves it with PUT", async () => {
    const fetchMock = stubRoutes([
      ["PUT", /\/api\/admin\/newsletter\/4$/, () => jsonResponse(campaign())],
      ["GET", LIST, () => jsonResponse(overview())],
    ]);
    const user = userEvent.setup();
    renderApp("/admin", <AdminNewsletter />);

    await user.click(await screen.findByRole("button", { name: "Edit draft" }));

    expect(screen.getByLabelText("Email subject")).toHaveValue("Spring news");
    expect(screen.getByLabelText("Newsletter content")).toHaveValue("Hello everyone");
    expect(screen.getByText("Edit draft #4")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Save draft" }));

    expect(await screen.findByRole("status")).toHaveTextContent("Draft saved successfully.");
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === "PUT")).toBe(true);
  });

  it("clears the editor", async () => {
    stubRoutes([["GET", LIST, () => jsonResponse(overview())]]);
    const user = userEvent.setup();
    renderApp("/admin", <AdminNewsletter />);

    await user.click(await screen.findByRole("button", { name: "Edit draft" }));
    await user.click(screen.getByRole("button", { name: "Clear editor" }));

    expect(screen.getByLabelText("Email subject")).toHaveValue("");
    expect(screen.getByLabelText("Newsletter content")).toHaveValue("");
    expect(screen.getByText("Create a newsletter")).toBeInTheDocument();
  });

  it("shows a preview with the number of active recipients", async () => {
    stubRoutes([
      [
        "GET",
        /\/api\/admin\/newsletter\/4\/preview$/,
        () => jsonResponse({ campaign: campaign(), activeRecipientCount: 3 }),
      ],
      ["GET", LIST, () => jsonResponse(overview())],
    ]);
    const user = userEvent.setup();
    renderApp("/admin", <AdminNewsletter />);

    await user.click(await screen.findByRole("button", { name: "Preview" }));

    expect(await screen.findByText("Newsletter preview")).toBeInTheDocument();
    expect(screen.getByText("Active recipients:").parentElement).toHaveTextContent("3");

    await user.click(screen.getByRole("button", { name: "Close preview" }));
    expect(screen.queryByText("Newsletter preview")).not.toBeInTheDocument();
  });

  it("does not send the newsletter unless the admin confirms", async () => {
    const fetchMock = stubRoutes([["GET", LIST, () => jsonResponse(overview())]]);
    vi.spyOn(window, "confirm").mockReturnValue(false);
    const user = userEvent.setup();
    renderApp("/admin", <AdminNewsletter />);

    await user.click(await screen.findByRole("button", { name: "Send" }));

    expect(fetchMock.mock.calls.some(([url]) => /\/send$/.test(String(url)))).toBe(false);
  });

  it("sends the newsletter after confirmation and shows the result", async () => {
    const fetchMock = stubRoutes([
      [
        "POST",
        /\/api\/admin\/newsletter\/4\/send$/,
        () => jsonResponse(campaign({ status: "SENT", recipientCount: 3, successCount: 2, failedCount: 1 })),
      ],
      ["GET", LIST, () => jsonResponse(overview())],
    ]);
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const user = userEvent.setup();
    renderApp("/admin", <AdminNewsletter />);

    await user.click(await screen.findByRole("button", { name: "Send" }));

    expect(await screen.findByRole("status")).toHaveTextContent(
      "Send completed. Recipients: 3; successful: 2; failed: 1."
    );
    expect(fetchMock.mock.calls.some(([url]) => /\/send$/.test(String(url)))).toBe(true);
  });

  it("shows the server message when sending fails", async () => {
    stubRoutes([
      ["POST", /\/send$/, () => jsonResponse({ message: "Mail server is down" }, false, 503)],
      ["GET", LIST, () => jsonResponse(overview())],
    ]);
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const user = userEvent.setup();
    renderApp("/admin", <AdminNewsletter />);

    await user.click(await screen.findByRole("button", { name: "Send" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Mail server is down");
  });

  it("offers retry only for campaigns with failed deliveries", async () => {
    stubRoutes([
      [
        "GET",
        LIST,
        () =>
          jsonResponse(
            overview({
              campaigns: [
                campaign({ id: 1, subject: "Clean", status: "SENT", recipientCount: 2, successCount: 2 }),
                campaign({ id: 2, subject: "Partly failed", status: "SENT", recipientCount: 2, successCount: 1, failedCount: 1 }),
              ],
            })
          ),
      ],
    ]);
    renderApp("/admin", <AdminNewsletter />);

    await screen.findByText("Partly failed");

    expect(screen.getAllByRole("button", { name: "Retry failed" })).toHaveLength(1);
    expect(screen.queryByRole("button", { name: "Send" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Edit draft" })).not.toBeInTheDocument();
  });

  it("retries failed deliveries after confirmation", async () => {
    const fetchMock = stubRoutes([
      [
        "POST",
        /\/api\/admin\/newsletter\/2\/retry-failed$/,
        () => jsonResponse(campaign({ id: 2, status: "SENT", successCount: 2, failedCount: 0 })),
      ],
      [
        "GET",
        LIST,
        () =>
          jsonResponse(
            overview({
              campaigns: [campaign({ id: 2, status: "SENT", recipientCount: 2, successCount: 1, failedCount: 1 })],
            })
          ),
      ],
    ]);
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const user = userEvent.setup();
    renderApp("/admin", <AdminNewsletter />);

    await user.click(await screen.findByRole("button", { name: "Retry failed" }));

    expect(await screen.findByRole("status")).toHaveTextContent(
      "Retry completed. Successful: 2; failed: 0."
    );
    expect(fetchMock.mock.calls.some(([url]) => /retry-failed$/.test(String(url)))).toBe(true);
  });
});