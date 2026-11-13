import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import FeatureVisibility from "./FeatureVisibility";
import { renderApp, jsonResponse } from "../test/render";

function featureList(
  enabled: Record<string, boolean> = { NEWSLETTER: true, TESTIMONIALS: true, UPDATES: true, APPOINTMENTS: true }
) {
  return ["NEWSLETTER", "TESTIMONIALS", "UPDATES", "APPOINTMENTS"].map((key, index) => ({
    featureKey: key,
    label: key,
    enabled: enabled[key],
    displayOrder: index + 1,
  }));
}

type Handler = () => Promise<unknown>;

// Only the admin features URL is answered. Other URLs (the public feature
// provider) get an empty list.
function stubApi(handlers: { get?: Handler; put?: Handler } = {}) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    if (String(url).includes("/api/admin/features")) {
      if (init?.method === "PUT") {
        return (handlers.put ?? (() => jsonResponse(featureList())))();
      }
      return (handlers.get ?? (() => jsonResponse(featureList())))();
    }
    return jsonResponse([]);
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function checkbox(name: RegExp) {
  return screen.getByRole("checkbox", { name });
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

describe("FeatureVisibility", () => {
  it("shows a loading message and then the four features", async () => {
    stubApi();
    renderApp("/admin", <FeatureVisibility />);

    expect(screen.getByText("Loading your website settings...")).toBeInTheDocument();

    expect(await screen.findByText("Newsletter Signup")).toBeInTheDocument();
    expect(screen.getByText("Testimonials")).toBeInTheDocument();
    expect(screen.getByText("Updates")).toBeInTheDocument();
    expect(screen.getByText("Appointments")).toBeInTheDocument();
  });

  it("sends the admin token when loading", async () => {
    const fetchMock = stubApi();
    renderApp("/admin", <FeatureVisibility />);
    await screen.findByText("Newsletter Signup");

    const call = fetchMock.mock.calls.find(([url]) => String(url).includes("/api/admin/features"));
    expect(JSON.stringify(call![1]?.headers)).toContain("Bearer admin-token");
  });

  it("shows every feature as visible when the server has no settings yet", async () => {
    stubApi({ get: () => jsonResponse([]) });
    renderApp("/admin", <FeatureVisibility />);
    await screen.findByText("Newsletter Signup");

    expect(screen.getAllByText("Visible")).toHaveLength(4);
    expect(screen.getByText("Features currently visible").parentElement).toHaveTextContent("4 / 4");
    expect(checkbox(/newsletter signup/i)).toBeChecked();
  });

  it("reflects the saved settings from the server", async () => {
    stubApi({
      get: () => jsonResponse(featureList({ NEWSLETTER: false, TESTIMONIALS: true, UPDATES: true, APPOINTMENTS: true })),
    });
    renderApp("/admin", <FeatureVisibility />);
    await screen.findByText("Newsletter Signup");

    expect(checkbox(/newsletter signup/i)).not.toBeChecked();
    expect(screen.getByText("Hidden")).toBeInTheDocument();
    expect(screen.getByText("Features currently visible").parentElement).toHaveTextContent("3 / 4");
  });

  it("starts with nothing to save", async () => {
    stubApi();
    renderApp("/admin", <FeatureVisibility />);
    await screen.findByText("Newsletter Signup");

    expect(screen.getByText("All changes are saved")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /save changes/i })).toBeDisabled();
    expect(screen.queryByRole("button", { name: "Discard" })).not.toBeInTheDocument();
  });

  it("marks changes as unsaved when a feature is switched off", async () => {
    stubApi();
    const user = userEvent.setup();
    renderApp("/admin", <FeatureVisibility />);
    await screen.findByText("Newsletter Signup");

    await user.click(checkbox(/testimonials/i));

    expect(screen.getByText("Unsaved changes")).toBeInTheDocument();
    expect(screen.getByText("Hidden")).toBeInTheDocument();
    expect(screen.getByText("Features currently visible").parentElement).toHaveTextContent("3 / 4");
    expect(screen.getByRole("button", { name: /save changes/i })).toBeEnabled();
    expect(screen.getByRole("button", { name: "Discard" })).toBeInTheDocument();
  });

  it("saves the new settings, tells the website, and confirms", async () => {
    const fetchMock = stubApi({
      put: () => jsonResponse(featureList({ NEWSLETTER: true, TESTIMONIALS: false, UPDATES: true, APPOINTMENTS: true })),
    });
    const updated = vi.fn();
    window.addEventListener("public-features-updated", updated);
    const user = userEvent.setup();
    renderApp("/admin", <FeatureVisibility />);
    await screen.findByText("Newsletter Signup");

    await user.click(checkbox(/testimonials/i));
    await user.click(screen.getByRole("button", { name: /save changes/i }));

    expect(await screen.findByText("Your website feature settings have been saved.")).toBeInTheDocument();
    expect(screen.getByText("All changes are saved")).toBeInTheDocument();
    expect(updated).toHaveBeenCalled();

    const put = fetchMock.mock.calls.find(([, init]) => init?.method === "PUT");
    expect(JSON.parse(String(put![1]?.body))).toEqual([
      { featureKey: "NEWSLETTER", enabled: true },
      { featureKey: "TESTIMONIALS", enabled: false },
      { featureKey: "UPDATES", enabled: true },
      { featureKey: "APPOINTMENTS", enabled: true },
    ]);
    expect(JSON.stringify(put![1]?.headers)).toContain("Bearer admin-token");

    window.removeEventListener("public-features-updated", updated);
  });

  it("keeps the changes unsaved and shows the server message when saving fails", async () => {
    stubApi({ put: () => jsonResponse({ message: "Cannot save" }, false, 500) });
    const user = userEvent.setup();
    renderApp("/admin", <FeatureVisibility />);
    await screen.findByText("Newsletter Signup");

    await user.click(checkbox(/updates/i));
    await user.click(screen.getByRole("button", { name: /save changes/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Cannot save");
    expect(screen.getByText("Unsaved changes")).toBeInTheDocument();
  });

  it("discards changes only after confirmation", async () => {
    stubApi();
    const confirmSpy = vi.spyOn(window, "confirm").mockReturnValue(false);
    const user = userEvent.setup();
    renderApp("/admin", <FeatureVisibility />);
    await screen.findByText("Newsletter Signup");

    await user.click(checkbox(/newsletter signup/i));
    await user.click(screen.getByRole("button", { name: "Discard" }));
    expect(screen.getByText("Unsaved changes")).toBeInTheDocument();

    confirmSpy.mockReturnValue(true);
    await user.click(screen.getByRole("button", { name: "Discard" }));
    expect(screen.getByText("All changes are saved")).toBeInTheDocument();
    expect(checkbox(/newsletter signup/i)).toBeChecked();
  });

  it("shows an error with a retry button when loading fails", async () => {
    let calls = 0;
    stubApi({
      get: () => {
        calls += 1;
        return calls === 1
          ? jsonResponse({ message: "Server is busy" }, false, 503)
          : jsonResponse(featureList());
      },
    });
    const user = userEvent.setup();
    renderApp("/admin", <FeatureVisibility />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Server is busy");

    await user.click(screen.getByRole("button", { name: "Retry" }));

    expect(await screen.findByText("Newsletter Signup")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});