import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AdminTestimonials from "./AdminTestimonials";
import { renderApp, jsonResponse } from "../test/render";

function item(overrides: Record<string, unknown> = {}) {
  return {
    id: 5,
    displayName: "Jane",
    content: "Casey was a great support for us.",
    photos: [],
    status: "PENDING",
    publicationConsent: true,
    displayOnPublic: false,
    featuredOnHome: false,
    createdAt: "2026-01-01T10:00:00",
    ownerEmail: "jane@mail.com",
    ...overrides,
  };
}

beforeEach(() => {
  localStorage.clear();
  localStorage.setItem("adminToken", "admin-token");
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  localStorage.clear();
});

describe("AdminTestimonials", () => {
  it("says there is nothing to review when the list is empty", async () => {
    vi.stubGlobal("fetch", vi.fn(() => jsonResponse([])));
    renderApp("/admin", <AdminTestimonials />);

    expect(await screen.findByText("No pending testimonials.")).toBeInTheDocument();
  });

  it("shows a pending testimonial with approve and reject buttons", async () => {
    vi.stubGlobal("fetch", vi.fn(() => jsonResponse([item()])));
    renderApp("/admin", <AdminTestimonials />);

    expect(await screen.findByRole("heading", { name: "Jane" })).toBeInTheDocument();
    expect(screen.getByText("Casey was a great support for us.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Approve" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Reject" })).toBeInTheDocument();
  });

  it("sends the approval with the admin token and reloads the list", async () => {
    let list = [item()];
    const fetchMock = vi.fn((url: string, _init?: RequestInit) => {
      if (url.includes("/approve")) {
        list = [item({ status: "APPROVED", displayOnPublic: false })];
        return jsonResponse(null);
      }
      return jsonResponse(list);
    });
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();
    renderApp("/admin", <AdminTestimonials />);

    await user.click(await screen.findByRole("button", { name: "Approve" }));

    expect(
      await screen.findByLabelText(/show on public testimonials page/i)
    ).toBeInTheDocument();

    const approveCall = fetchMock.mock.calls.find(([url]) =>
      url.includes("/api/admin/testimonials/5/approve")
    );
    expect(approveCall).toBeDefined();
    expect(approveCall![1]?.method).toBe("POST");
    expect(JSON.stringify(approveCall![1]?.headers)).toContain("Bearer admin-token");
  });

  it("does not allow a homepage feature unless the testimonial is public", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() =>
        jsonResponse([item({ status: "APPROVED", displayOnPublic: false })])
      )
    );
    renderApp("/admin", <AdminTestimonials />);

    expect(
      await screen.findByLabelText(/feature on homepage/i)
    ).toBeDisabled();
    expect(screen.getByLabelText(/show on public testimonials page/i)).not.toBeChecked();
  });

  it("removes a testimonial from public display only after confirmation", async () => {
    const fetchMock = vi.fn((url: string, _init?: RequestInit) =>
      url.includes("/remove")
        ? jsonResponse(null)
        : jsonResponse([item({ status: "APPROVED", displayOnPublic: true })])
    );
    vi.stubGlobal("fetch", fetchMock);
    const confirmSpy = vi.spyOn(window, "confirm").mockReturnValue(false);
    const user = userEvent.setup();
    renderApp("/admin", <AdminTestimonials />);

    const removeButton = await screen.findByRole("button", {
      name: /remove from public display/i,
    });

    await user.click(removeButton);
    expect(
      fetchMock.mock.calls.some(([url]) => url.includes("/remove"))
    ).toBe(false);

    confirmSpy.mockReturnValue(true);
    await user.click(removeButton);
    expect(
      fetchMock.mock.calls.some(([url]) => url.includes("/remove"))
    ).toBe(true);
  });

  it("shows an error message when the list cannot be loaded", async () => {
    vi.stubGlobal("fetch", vi.fn(() => jsonResponse("Not allowed", false, 403)));
    renderApp("/admin", <AdminTestimonials />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Not allowed");
  });
});