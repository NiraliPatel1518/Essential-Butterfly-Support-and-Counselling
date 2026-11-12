import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { screen, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AdminUpdates from "./AdminUpdates";
import { renderApp, jsonResponse } from "../test/render";

type Route = [string, RegExp, () => Promise<unknown>];

// The first route with the same method and a matching URL answers.
// Anything else gets an empty list.
function stubRoutes(routes: Route[]) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    const method = init?.method ?? "GET";
    const route = routes.find(([m, pattern]) => m === method && pattern.test(url));
    return route ? route[2]() : jsonResponse([]);
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function update(overrides: Record<string, unknown> = {}) {
  return {
    id: 5,
    title: "Summer camp",
    content: "Registration is open.",
    published: true,
    createdAt: "2026-01-01T10:00:00",
    updatedAt: "2026-01-01T10:00:00",
    photos: [],
    socialLinks: [],
    ...overrides,
  };
}

const LIST = /\/api\/admin\/updates$/;

async function fillBasics(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText("Update title"), "New title");
  await user.type(screen.getByLabelText(/description \/ update content/i), "Some content");
}

beforeEach(() => {
  localStorage.clear();
  localStorage.setItem("adminToken", "admin-token");
  // jsdom does not implement scrollIntoView, and the Edit button calls it.
  Element.prototype.scrollIntoView = vi.fn();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  localStorage.clear();
});

describe("AdminUpdates", () => {
  it("lists the existing updates with their status", async () => {
    stubRoutes([
      [
        "GET",
        LIST,
        () =>
          jsonResponse([
            update(),
            update({ id: 6, title: "Draft post", published: false, photos: ["a", "b"], socialLinks: ["https://x.com"] }),
          ]),
      ],
    ]);
    renderApp("/admin", <AdminUpdates />);

    expect(await screen.findByText("Summer camp")).toBeInTheDocument();
    expect(screen.getByText("Draft post")).toBeInTheDocument();
    expect(screen.getByText("Existing Updates (2)")).toBeInTheDocument();
    expect(screen.getByText("Published")).toBeInTheDocument();
    expect(screen.getByText("Unpublished")).toBeInTheDocument();
    expect(screen.getByText("2 photo(s) · 1 social link(s)")).toBeInTheDocument();
  });

  it("sends the admin token when loading", async () => {
    const fetchMock = stubRoutes([["GET", LIST, () => jsonResponse([update()])]]);
    renderApp("/admin", <AdminUpdates />);
    await screen.findByText("Summer camp");

    const call = fetchMock.mock.calls[0];
    expect(String(call[0])).toContain("/api/admin/updates");
    expect(JSON.stringify(call[1]?.headers)).toContain("Bearer admin-token");
  });

  it("shows the server message when the updates cannot be loaded", async () => {
    stubRoutes([["GET", LIST, () => jsonResponse({ message: "Forbidden" }, false, 403)]]);
    renderApp("/admin", <AdminUpdates />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Forbidden");
  });

  it("keeps the save button off until a title and content are entered", async () => {
    stubRoutes([["GET", LIST, () => jsonResponse([])]]);
    const user = userEvent.setup();
    renderApp("/admin", <AdminUpdates />);

    const save = await screen.findByRole("button", { name: "Save Update" });
    expect(save).toBeDisabled();

    await fillBasics(user);
    expect(save).toBeEnabled();
  });

  it("saves a new published update and confirms it", async () => {
    const fetchMock = stubRoutes([
      ["POST", LIST, () => jsonResponse(update())],
      ["GET", LIST, () => jsonResponse([])],
    ]);
    const user = userEvent.setup();
    renderApp("/admin", <AdminUpdates />);
    await screen.findByText("Add a New Update");

    await fillBasics(user);
    await user.click(screen.getByRole("button", { name: "Save Update" }));

    expect(await screen.findByRole("status")).toHaveTextContent("Update saved successfully.");
    expect(screen.getByLabelText("Update title")).toHaveValue("");

    const post = fetchMock.mock.calls.find(([, init]) => init?.method === "POST");
    expect(JSON.parse(String(post![1]?.body))).toEqual({
      title: "New title",
      content: "Some content",
      published: true,
      photos: [],
      socialLinks: [],
    });
  });

  it("can save an update as unpublished", async () => {
    const fetchMock = stubRoutes([
      ["POST", LIST, () => jsonResponse(update())],
      ["GET", LIST, () => jsonResponse([])],
    ]);
    const user = userEvent.setup();
    renderApp("/admin", <AdminUpdates />);
    await screen.findByText("Add a New Update");

    await fillBasics(user);
    await user.click(screen.getByLabelText(/publish this update/i));
    await user.click(screen.getByRole("button", { name: "Save Update" }));
    await screen.findByRole("status");

    const post = fetchMock.mock.calls.find(([, init]) => init?.method === "POST");
    expect(JSON.parse(String(post![1]?.body)).published).toBe(false);
  });

  it("shows the server message when saving fails", async () => {
    stubRoutes([
      ["POST", LIST, () => jsonResponse({ message: "Title already exists" }, false, 409)],
      ["GET", LIST, () => jsonResponse([])],
    ]);
    const user = userEvent.setup();
    renderApp("/admin", <AdminUpdates />);
    await screen.findByText("Add a New Update");

    await fillBasics(user);
    await user.click(screen.getByRole("button", { name: "Save Update" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Title already exists");
  });

  it("allows at most 5 social media links", async () => {
    const fetchMock = stubRoutes([["GET", LIST, () => jsonResponse([])]]);
    const user = userEvent.setup();
    renderApp("/admin", <AdminUpdates />);
    await screen.findByText("Add a New Update");

    await fillBasics(user);
    fireEvent.change(screen.getByLabelText(/social media links/i), {
      target: { value: Array.from({ length: 6 }, (_, i) => `https://site${i}.com`).join("\n") },
    });
    await user.click(screen.getByRole("button", { name: "Save Update" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "You can add a maximum of 5 social media links."
    );
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === "POST")).toBe(false);
  });

  it("requires every social link to start with http:// or https://", async () => {
    const fetchMock = stubRoutes([["GET", LIST, () => jsonResponse([])]]);
    const user = userEvent.setup();
    renderApp("/admin", <AdminUpdates />);
    await screen.findByText("Add a New Update");

    await fillBasics(user);
    fireEvent.change(screen.getByLabelText(/social media links/i), {
      target: { value: "https://ok.com\ninstagram.com/casey" },
    });
    await user.click(screen.getByRole("button", { name: "Save Update" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Every social media link must start with http:// or https://."
    );
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === "POST")).toBe(false);
  });

  it("loads an update into the form and saves the change with PUT", async () => {
    const fetchMock = stubRoutes([
      ["PUT", /\/api\/admin\/updates\/5$/, () => jsonResponse(update())],
      ["GET", LIST, () => jsonResponse([update({ socialLinks: ["https://x.com/a"] })])],
    ]);
    const user = userEvent.setup();
    renderApp("/admin", <AdminUpdates />);

    await user.click(await screen.findByRole("button", { name: "Edit" }));

    expect(screen.getByText("Edit Update")).toBeInTheDocument();
    expect(screen.getByLabelText("Update title")).toHaveValue("Summer camp");
    expect(screen.getByLabelText(/social media links/i)).toHaveValue("https://x.com/a");

    await user.click(screen.getByRole("button", { name: "Save Changes" }));

    expect(await screen.findByRole("status")).toHaveTextContent("Update saved successfully.");
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === "PUT")).toBe(true);
  });

  it("cancels editing and goes back to a blank form", async () => {
    stubRoutes([["GET", LIST, () => jsonResponse([update()])]]);
    const user = userEvent.setup();
    renderApp("/admin", <AdminUpdates />);

    await user.click(await screen.findByRole("button", { name: "Edit" }));
    await user.click(screen.getByRole("button", { name: "Cancel editing" }));

    expect(screen.getByText("Add a New Update")).toBeInTheDocument();
    expect(screen.getByLabelText("Update title")).toHaveValue("");
  });

  it("does not remove an update unless the admin confirms", async () => {
    const fetchMock = stubRoutes([["GET", LIST, () => jsonResponse([update()])]]);
    vi.spyOn(window, "confirm").mockReturnValue(false);
    const user = userEvent.setup();
    renderApp("/admin", <AdminUpdates />);

    await user.click(await screen.findByRole("button", { name: "Remove" }));

    expect(fetchMock.mock.calls.some(([, init]) => init?.method === "DELETE")).toBe(false);
  });

  it("removes an update after confirmation", async () => {
    const fetchMock = stubRoutes([
      ["DELETE", /\/api\/admin\/updates\/5$/, () => jsonResponse({})],
      ["GET", LIST, () => jsonResponse([update()])],
    ]);
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const user = userEvent.setup();
    renderApp("/admin", <AdminUpdates />);

    await user.click(await screen.findByRole("button", { name: "Remove" }));

    expect(await screen.findByRole("status")).toHaveTextContent("Update removed.");
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === "DELETE")).toBe(true);
  });

  it("only accepts JPEG, PNG and WebP photos", async () => {
    stubRoutes([["GET", LIST, () => jsonResponse([])]]);
    const user = userEvent.setup({ applyAccept: false });
    renderApp("/admin", <AdminUpdates />);
    await screen.findByText("Add a New Update");

    await user.upload(
      screen.getByLabelText(/photos \(maximum 5/i),
      new File(["x"], "doc.pdf", { type: "application/pdf" })
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Only JPEG, PNG and WebP photos are allowed."
    );
  });

  it("rejects a photo larger than 2 MB", async () => {
    stubRoutes([["GET", LIST, () => jsonResponse([])]]);
    const user = userEvent.setup({ applyAccept: false });
    renderApp("/admin", <AdminUpdates />);
    await screen.findByText("Add a New Update");

    const big = new File([new ArrayBuffer(2 * 1024 * 1024 + 1)], "big.png", { type: "image/png" });
    await user.upload(screen.getByLabelText(/photos \(maximum 5/i), big);

    expect(await screen.findByRole("alert")).toHaveTextContent("big.png is larger than 2 MB.");
  });

  it("allows at most 5 photos", async () => {
    stubRoutes([["GET", LIST, () => jsonResponse([])]]);
    const user = userEvent.setup({ applyAccept: false });
    renderApp("/admin", <AdminUpdates />);
    await screen.findByText("Add a New Update");

    const files = Array.from({ length: 6 }, (_, i) => new File(["x"], `p${i}.png`, { type: "image/png" }));
    await user.upload(screen.getByLabelText(/photos \(maximum 5/i), files);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "You can upload a maximum of 5 photos."
    );
  });

  it("marks the first photo as the cover and lets the admin remove it", async () => {
    stubRoutes([["GET", LIST, () => jsonResponse([])]]);
    const user = userEvent.setup({ applyAccept: false });
    renderApp("/admin", <AdminUpdates />);
    await screen.findByText("Add a New Update");

    await user.upload(
      screen.getByLabelText(/photos \(maximum 5/i),
      new File(["x"], "p.png", { type: "image/png" })
    );

    expect(await screen.findByText("Cover photo")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Remove" }));
    expect(screen.queryByText("Cover photo")).not.toBeInTheDocument();
  });
});