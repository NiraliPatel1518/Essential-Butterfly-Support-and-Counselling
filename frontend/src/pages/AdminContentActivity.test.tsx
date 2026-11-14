import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AdminContentActivity from "./AdminContentActivity";
import { renderApp, jsonResponse } from "../test/render";
import type { AuditEntry } from "../api/contentApi";

function entry(id: number, action: string, extra: Partial<AuditEntry> = {}): AuditEntry {
  return {
    id,
    occurredAt: "2026-11-10T14:30:00",
    action,
    entityType: "CONTENT",
    entityId: "services",
    actor: "casey@example.com",
    details: null,
    ...extra,
  } as AuditEntry;
}

function stub(reply: () => Promise<unknown>) {
  const fetchMock = vi.fn((_url: string, _init?: RequestInit) => reply());
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

async function show(entries: AuditEntry[]) {
  const fetchMock = stub(() => jsonResponse(entries));
  renderApp("/admin", <AdminContentActivity />);
  await waitFor(() => expect(screen.queryByText("Loading recent changes...")).not.toBeInTheDocument());
  return fetchMock;
}

beforeEach(() => localStorage.setItem("adminToken", "admin-token"));
afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("AdminContentActivity", () => {
  it("shows a loading message first", async () => {
    stub(() => jsonResponse([]));
    renderApp("/admin", <AdminContentActivity />);

    expect(screen.getByRole("status")).toHaveTextContent("Loading recent changes...");
    await screen.findByText("No changes have been recorded yet.");
  });

  it("asks for the latest 50 changes with the admin token", async () => {
    const fetchMock = await show([]);

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain("/api/admin/audit-log?limit=50");
    expect((init?.headers as Record<string, string>).Authorization).toBe("Bearer admin-token");
  });

  it("says when nothing has been recorded", async () => {
    await show([]);

    expect(screen.getByText("No changes have been recorded yet.")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("shows a table with the four columns", async () => {
    await show([entry(1, "CONTENT_PUBLISHED")]);

    const headers = screen.getAllByRole("columnheader").map((h) => h.textContent);
    expect(headers).toEqual(["When", "What happened", "Item", "By"]);
  });

  it.each([
    ["CONTENT_DRAFT_SAVED", "Page draft saved"],
    ["CONTENT_PUBLISHED", "Page published"],
    ["RESOURCE_CREATED", "Resource added"],
    ["RESOURCE_UPDATED", "Resource updated"],
    ["RESOURCE_PUBLISHED", "Resource published"],
    ["RESOURCE_UNPUBLISHED", "Resource unpublished"],
    ["RESOURCE_REMOVED", "Resource removed"],
    ["MEMORY_WALL_PHOTO_ADDED", "Memory Wall photo added"],
    ["MEMORY_WALL_PHOTO_PUBLISHED", "Memory Wall photo published"],
    ["MEMORY_WALL_PHOTO_UNPUBLISHED", "Memory Wall photo unpublished"],
    ["MEMORY_WALL_PHOTO_UPDATED", "Memory Wall photo description changed"],
    ["MEMORY_WALL_PHOTO_DELETED", "Memory Wall photo deleted"],
    ["SECURITY_UNSAFE_FILE_REJECTED", "Unsafe file rejected"],
    ["SECURITY_UNSAFE_LINK_REJECTED", "Unsafe link rejected"],
  ])("shows %s as plain words", async (action, words) => {
    await show([entry(1, action)]);

    expect(screen.getByText(words)).toBeInTheDocument();
  });

  it("shows an unknown action as it is", async () => {
    await show([entry(1, "SOMETHING_NEW")]);

    expect(screen.getByText("SOMETHING_NEW")).toBeInTheDocument();
  });

  it("shows who did it, or Unknown", async () => {
    await show([entry(1, "CONTENT_PUBLISHED"), entry(2, "CONTENT_PUBLISHED", { actor: null })]);

    expect(screen.getByText("casey@example.com")).toBeInTheDocument();
    expect(screen.getByText("Unknown")).toBeInTheDocument();
  });

  it("names the item from the title in the details", async () => {
    await show([entry(1, "RESOURCE_CREATED", { details: JSON.stringify({ title: "Coping guide" }) })]);

    expect(screen.getByText("Coping guide")).toBeInTheDocument();
  });

  it("falls back to the page, then to the reason", async () => {
    await show([
      entry(1, "CONTENT_PUBLISHED", { details: JSON.stringify({ page_key: "about" }) }),
      entry(2, "SECURITY_UNSAFE_FILE_REJECTED", { details: JSON.stringify({ reason: "Wrong file type" }) }),
    ]);

    expect(screen.getByText("about")).toBeInTheDocument();
    expect(screen.getByText("Wrong file type")).toBeInTheDocument();
  });

  it("does not break on details that are not valid JSON", async () => {
    await show([entry(1, "CONTENT_PUBLISHED", { details: "not json {" })]);

    expect(screen.getByText("Page published")).toBeInTheDocument();
  });

  it("shows every entry as a row", async () => {
    await show([entry(1, "CONTENT_PUBLISHED"), entry(2, "RESOURCE_CREATED"), entry(3, "RESOURCE_REMOVED")]);

    expect(within(screen.getByRole("table")).getAllByRole("row")).toHaveLength(4);
  });

  it("Refresh asks for the changes again", async () => {
    const fetchMock = await show([]);

    const before = fetchMock.mock.calls.length;
    await userEvent.click(screen.getByRole("button", { name: /Refresh/ }));

    await waitFor(() => expect(fetchMock.mock.calls.length).toBeGreaterThan(before));
  });

  it("shows the server message when loading fails", async () => {
    stub(() => jsonResponse({ message: "Log is unavailable." }, false, 409));
    renderApp("/admin", <AdminContentActivity />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Log is unavailable.");
  });

  it("sends the administrator to the login page when the session expired", async () => {
    stub(() => jsonResponse("", false, 401));
    renderApp("/admin", <AdminContentActivity />);

    expect(await screen.findByText(/Landed on \/login/)).toBeInTheDocument();
    expect(localStorage.getItem("adminToken")).toBeNull();
  });
});
