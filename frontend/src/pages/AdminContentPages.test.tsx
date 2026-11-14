import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AdminContentPages from "./AdminContentPages";
import { renderApp, jsonResponse } from "../test/render";
import { PAGE_LAYOUTS, SERVICES_MAX, defaultPageContent, serializePageContent } from "../content/pageLayouts";
import type { ContentPageAdmin, ContentPageKey } from "../api/contentApi";

function page(key: ContentPageKey, overrides: Partial<ContentPageAdmin> = {}): ContentPageAdmin {
  return {
    pageKey: key,
    label: PAGE_LAYOUTS[key].label,
    published: false,
    hasUnpublishedChanges: false,
    publishedTitle: null,
    publishedBody: null,
    draftTitle: null,
    draftBody: null,
    publishedAt: null,
    updatedAt: "2026-11-01T10:00:00",
    ...overrides,
  };
}

function body(key: ContentPageKey, changes: Record<string, string> = {}) {
  const content = defaultPageContent(key);
  content.fields = { ...content.fields, ...changes };
  return serializePageContent(content);
}

type World = {
  pages: ContentPageAdmin[];
  // Replies that override the normal ones, keyed "METHOD path-part".
  fail: Record<string, () => Promise<unknown>>;
};

function stubApi(world: World) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    const path = String(url);
    const method = init?.method ?? "GET";

    for (const [key, reply] of Object.entries(world.fail)) {
      const [m, part] = key.split(" ");
      if (m === method && path.includes(part)) return reply();
    }

    if (path.includes("/api/admin/content/") && method === "PUT") {
      const key = path.split("/").pop() as ContentPageKey;
      const sent = JSON.parse(String(init?.body));
      return jsonResponse(
        page(key, {
          published: sent.publish ? true : world.pages.find((p) => p.pageKey === key)!.published,
          publishedAt: sent.publish ? "2026-11-14T09:00:00" : null,
          publishedBody: sent.publish ? sent.body : null,
          draftBody: sent.publish ? null : sent.body,
          hasUnpublishedChanges: !sent.publish,
        })
      );
    }

    if (path.includes("/api/admin/content")) return jsonResponse(world.pages);

    return jsonResponse([]);
  });

  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function allPages() {
  return [page("services"), page("about"), page("privacy")];
}

async function show(world: Partial<World> = {}) {
  const full: World = { pages: allPages(), fail: {}, ...world };
  const fetchMock = stubApi(full);
  renderApp("/admin", <AdminContentPages />);
  await waitFor(() => expect(screen.queryByText("Loading website pages...")).not.toBeInTheDocument());
  return fetchMock;
}

function saves(fetchMock: ReturnType<typeof stubApi>) {
  return fetchMock.mock.calls.filter(([, init]) => init?.method === "PUT");
}

beforeEach(() => {
  localStorage.setItem("adminToken", "admin-token");
  sessionStorage.clear();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  localStorage.clear();
  sessionStorage.clear();
});

describe("AdminContentPages: the page list", () => {
  it("shows a loading message and then the editor", async () => {
    stubApi({ pages: allPages(), fail: {} });
    renderApp("/admin", <AdminContentPages />);

    expect(screen.getByText("Loading website pages...")).toBeInTheDocument();

    expect(await screen.findByRole("heading", { name: "Services", level: 4 })).toBeInTheDocument();
  });

  it("asks for the pages with the admin token", async () => {
    const fetchMock = await show();

    const call = fetchMock.mock.calls.find(([url]) => String(url).endsWith("/api/admin/content"))!;
    expect((call[1]?.headers as Record<string, string>).Authorization).toBe("Bearer admin-token");
  });

  it("lists the three pages in a fixed order, whatever order the server sends", async () => {
    await show({ pages: [page("privacy"), page("services"), page("about")] });

    const buttons = within(screen.getByRole("navigation", { name: "Editable pages" })).getAllByRole("button");

    expect(buttons.map((b) => b.textContent)).toEqual([
      "ServicesOriginal wording",
      "About CaseyOriginal wording",
      "Privacy PolicyOriginal wording",
    ]);
  });

  it("labels each page as original, published or with unpublished changes", async () => {
    await show({
      pages: [
        page("services", { published: true, publishedAt: "2026-11-01T10:00:00", publishedBody: body("services") }),
        page("about", { published: true, hasUnpublishedChanges: true, publishedBody: body("about"), draftBody: body("about") }),
        page("privacy"),
      ],
    });

    const nav = screen.getByRole("navigation", { name: "Editable pages" });

    expect(within(nav).getByRole("button", { name: /Services/ })).toHaveTextContent("Published");
    expect(within(nav).getByRole("button", { name: /About Casey/ })).toHaveTextContent("Unpublished changes");
    expect(within(nav).getByRole("button", { name: /Privacy Policy/ })).toHaveTextContent("Original wording");
  });

  it("opens the first page and marks it as the current one", async () => {
    await show();

    expect(
      within(screen.getByRole("navigation", { name: "Editable pages" })).getByRole("button", { name: /Services/ })
    ).toHaveAttribute("aria-current", "page");
  });

  it("says when no pages were found", async () => {
    await show({ pages: [] });

    expect(screen.getByText("No editable pages were found.")).toBeInTheDocument();
  });

  it("shows the problem when loading fails", async () => {
    await show({ fail: { "GET /api/admin/content": () => jsonResponse({ message: "Pages are unavailable." }, false, 409) } });

    expect(await screen.findByRole("alert")).toHaveTextContent("Pages are unavailable.");
  });

  it("sends the administrator to the login page when the session expired", async () => {
    stubApi({ pages: [], fail: { "GET /api/admin/content": () => jsonResponse("", false, 401) } });
    renderApp("/admin", <AdminContentPages />);

    expect(await screen.findByText(/Landed on \/login/)).toBeInTheDocument();
    expect(localStorage.getItem("adminToken")).toBeNull();
  });
});

describe("AdminContentPages: the editor", () => {
  it("shows the original wording of a page that was never edited, with a note", async () => {
    await show();

    expect(screen.getByLabelText(/Main title/)).toHaveValue(PAGE_LAYOUTS.services.slots.find((s) => s.key === "heroTitle")!.default);
    expect(screen.getByText(/This page has not been edited before\./)).toBeInTheDocument();
    expect(screen.getByText("Not edited yet. The website is showing its original wording.", { exact: false })).toBeInTheDocument();
  });

  it("groups the boxes under the section names", async () => {
    await show();

    for (const group of ["Top of the page", "Availability section", "Services section", "Technology section", "Next steps section"]) {
      expect(screen.getByRole("group", { name: group })).toBeInTheDocument();
    }
  });

  it("shows a character counter for every box", async () => {
    await show();

    expect(screen.getByText(/\d+ of 90 characters/)).toBeInTheDocument();
    expect(screen.getByLabelText(/Main title/)).toHaveAttribute("maxlength", "90");
  });

  it("updates the counter while typing", async () => {
    await show();

    const title = screen.getByLabelText(/Main title/);
    await userEvent.clear(title);
    await userEvent.type(title, "Hello");

    expect(screen.getByText("5 of 90 characters")).toBeInTheDocument();
  });

  it("shows the saved draft before the published wording", async () => {
    await show({
      pages: [
        page("services", {
          published: true,
          publishedAt: "2026-11-01T10:00:00",
          hasUnpublishedChanges: true,
          publishedBody: body("services", { heroTitle: "Published title" }),
          draftBody: body("services", { heroTitle: "Draft title" }),
        }),
        page("about"),
        page("privacy"),
      ],
    });

    expect(screen.getByLabelText(/Main title/)).toHaveValue("Draft title");
    expect(screen.getByText(/You have changes that are not published yet\./)).toBeInTheDocument();
    expect(screen.queryByText(/This page has not been edited before/)).not.toBeInTheDocument();
  });

  it("shows the live wording when there is no draft", async () => {
    await show({
      pages: [
        page("services", {
          published: true,
          publishedAt: "2026-11-01T10:00:00",
          publishedBody: body("services", { heroTitle: "Live title" }),
        }),
        page("about"),
        page("privacy"),
      ],
    });

    expect(screen.getByLabelText(/Main title/)).toHaveValue("Live title");
    expect(screen.getByText(/Live on the website since/)).toBeInTheDocument();
  });

  it("links to the public page in a new tab", async () => {
    await show();

    const link = screen.getByRole("link", { name: /View public page/ });

    expect(link).toHaveAttribute("href", "/services");
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
  });

  it("switches to another page and shows its boxes", async () => {
    await show();

    await userEvent.click(screen.getByRole("button", { name: /About Casey/ }));

    expect(screen.getByRole("heading", { name: "About Casey", level: 4 })).toBeInTheDocument();
    expect(screen.getByLabelText(/Button wording/, { selector: "#slot-heroButton" })).toBeInTheDocument();
    expect(screen.queryByRole("group", { name: "Services list" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /View public page/ })).toHaveAttribute("href", "/about");
  });

  it("shows the privacy page with only its four boxes", async () => {
    await show();

    await userEvent.click(screen.getByRole("button", { name: /Privacy Policy/ }));

    expect(screen.getAllByRole("textbox")).toHaveLength(4);
  });

  it("Restore original wording puts the original text back", async () => {
    await show();

    const title = screen.getByLabelText(/Main title/);
    await userEvent.clear(title);
    await userEvent.type(title, "Changed");
    await userEvent.click(screen.getByRole("button", { name: "Restore original wording" }));

    expect(screen.getByLabelText(/Main title/)).toHaveValue(
      PAGE_LAYOUTS.services.slots.find((s) => s.key === "heroTitle")!.default
    );
    expect(screen.getByText("The original wording is back in the editor. Save or publish to keep it.")).toBeInTheDocument();
  });
});

describe("AdminContentPages: the services list", () => {
  it("shows every service with its own boxes", async () => {
    await show();

    const services = defaultPageContent("services").services!;

    expect(screen.getByRole("group", { name: "Services list" })).toBeInTheDocument();
    expect(screen.getAllByLabelText(/Service name/)).toHaveLength(services.length);
    expect(screen.getByRole("heading", { name: "Service 1" })).toBeInTheDocument();
  });

  it("adds an empty service and moves focus to its name box", async () => {
    await show();

    const before = screen.getAllByLabelText(/Service name/).length;
    await userEvent.click(screen.getByRole("button", { name: "Add a service" }));

    const names = screen.getAllByLabelText(/Service name/);
    expect(names).toHaveLength(before + 1);
    expect(names[names.length - 1]).toHaveValue("");
    await waitFor(() => expect(names[names.length - 1]).toHaveFocus());
  });

  it("stops adding at the maximum number of services", async () => {
    await show();

    const add = screen.getByRole("button", { name: "Add a service" });

    for (let count = screen.getAllByLabelText(/Service name/).length; count < SERVICES_MAX; count++) {
      await userEvent.click(add);
    }

    expect(screen.getAllByLabelText(/Service name/)).toHaveLength(SERVICES_MAX);
    expect(add).toBeDisabled();
  });

  it("removes a service and says it goes away when published", async () => {
    await show();

    const before = screen.getAllByLabelText(/Service name/).length;
    const firstName = (screen.getAllByLabelText(/Service name/)[0] as HTMLInputElement).value;

    await userEvent.click(screen.getAllByRole("button", { name: /Remove service/ })[0]);

    expect(screen.getAllByLabelText(/Service name/)).toHaveLength(before - 1);
    expect(
      screen.getByText(`"${firstName}" was removed from your draft. It disappears from the public website when you publish.`)
    ).toBeInTheDocument();
  });

  it("will not remove the last service", async () => {
    await show();

    while (screen.getAllByRole("button", { name: /Remove service/ }).length > 1) {
      await userEvent.click(screen.getAllByRole("button", { name: /Remove service/ })[0]);
    }

    expect(screen.getByRole("button", { name: /Remove service/ })).toBeDisabled();
  });

  it("changes the description and the in development tick", async () => {
    await show();

    const text = screen.getAllByLabelText(/Description/)[0];
    await userEvent.clear(text);
    await userEvent.type(text, "New words");
    await userEvent.click(screen.getAllByRole("checkbox")[0]);

    expect(text).toHaveValue("New words");
    expect(screen.getAllByRole("checkbox")[0]).toBeChecked();
  });
});

describe("AdminContentPages: validation", () => {
  it("will not save when a box is empty and lists the problem", async () => {
    const fetchMock = await show();

    await userEvent.clear(screen.getByLabelText(/Main title/));
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));

    const alert = screen.getByText("Please fix the following before saving:").closest("[role=alert]")!;

    expect(alert).toHaveTextContent("Top of the page: Main title cannot be empty.");
    expect(screen.getByLabelText(/Main title/)).toHaveAttribute("aria-invalid", "true");
    expect(saves(fetchMock)).toHaveLength(0);
  });

  it("moves keyboard focus to the first box that needs attention", async () => {
    await show();

    await userEvent.clear(screen.getByLabelText(/Introduction/, { selector: "#slot-heroLead" }));
    await userEvent.clear(screen.getByLabelText(/Main title/));
    await userEvent.click(screen.getByRole("button", { name: "Save draft" }));

    await waitFor(() => expect(screen.getByLabelText(/Main title/)).toHaveFocus());
  });

  it("treats a box with only spaces as empty", async () => {
    await show();

    const title = screen.getByLabelText(/Main title/);
    await userEvent.clear(title);
    await userEvent.type(title, "   ");
    await userEvent.click(screen.getByRole("button", { name: "Save draft" }));

    expect(screen.getAllByText(/Main title cannot be empty/).length).toBeGreaterThan(0);
  });

  it("asks for a service name and a description", async () => {
    const fetchMock = await show();

    await userEvent.click(screen.getByRole("button", { name: "Add a service" }));
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));

    const count = defaultPageContent("services").services!.length + 1;

    expect(screen.getAllByText(`Service ${count}: the name cannot be empty.`).length).toBeGreaterThan(0);
    expect(screen.getAllByText(`Service ${count}: the description cannot be empty.`).length).toBeGreaterThan(0);
    expect(saves(fetchMock)).toHaveLength(0);
  });
});

describe("AdminContentPages: saving and publishing", () => {
  it("publishes the page and says it is live", async () => {
    const fetchMock = await show();

    await userEvent.click(screen.getByRole("button", { name: "Publish" }));

    expect(await screen.findByText("Published. The updated wording is now live on the public website.")).toBeInTheDocument();

    const [url, init] = saves(fetchMock)[0];
    expect(String(url)).toMatch(/\/api\/admin\/content\/services$/);
    const sent = JSON.parse(String(init?.body));
    expect(sent.publish).toBe(true);
    expect(sent.title).toBe("Services");
    expect(JSON.parse(sent.body).fields.heroTitle).toBeTruthy();
  });

  it("saves a draft without publishing", async () => {
    const fetchMock = await show();

    await userEvent.click(screen.getByRole("button", { name: "Save draft" }));

    expect(await screen.findByText("Draft saved. Visitors still see the previously published wording.")).toBeInTheDocument();
    expect(JSON.parse(String(saves(fetchMock)[0][1]?.body)).publish).toBe(false);
  });

  it("sends the text without stray spaces", async () => {
    const fetchMock = await show();

    const title = screen.getByLabelText(/Main title/);
    await userEvent.clear(title);
    await userEvent.type(title, "  Spaced out  ");
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));

    await screen.findByText(/Published\./);

    const sent = JSON.parse(JSON.parse(String(saves(fetchMock)[0][1]?.body)).body);
    expect(sent.fields.heroTitle).toBe("Spaced out");
  });

  it("sends the services list for the services page only", async () => {
    const fetchMock = await show();

    await userEvent.click(screen.getByRole("button", { name: "Publish" }));
    await screen.findByText(/Published\./);
    expect(JSON.parse(JSON.parse(String(saves(fetchMock)[0][1]?.body)).body).services.length).toBeGreaterThan(0);

    await userEvent.click(screen.getByRole("button", { name: /About Casey/ }));
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));
    await waitFor(() => expect(saves(fetchMock)).toHaveLength(2));
    expect(JSON.parse(JSON.parse(String(saves(fetchMock)[1][1]?.body)).body).services).toBeUndefined();
  });

  it("updates the page list label after publishing", async () => {
    await show();

    await userEvent.click(screen.getByRole("button", { name: "Publish" }));
    await screen.findByText(/Published\./);

    expect(
      within(screen.getByRole("navigation", { name: "Editable pages" })).getByRole("button", { name: /Services/ })
    ).toHaveTextContent("Published");
  });

  it("shows the server message and keeps the typed text when saving fails", async () => {
    await show({ fail: { "PUT /api/admin/content": () => jsonResponse({ message: "The page could not be saved." }, false, 409) } });

    const title = screen.getByLabelText(/Main title/);
    await userEvent.clear(title);
    await userEvent.type(title, "My new title");
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("The page could not be saved.");
    expect(screen.getByLabelText(/Main title/)).toHaveValue("My new title");
  });

  it("keeps the typed text for after sign-in when the session expired while saving", async () => {
    await show({ fail: { "PUT /api/admin/content": () => jsonResponse("", false, 401) } });

    const title = screen.getByLabelText(/Main title/);
    await userEvent.clear(title);
    await userEvent.type(title, "Do not lose me");
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));

    expect(await screen.findByText(/Landed on \/login/)).toBeInTheDocument();
    expect(sessionStorage.getItem("essential-butterfly:draft:page:services")).toContain("Do not lose me");
  });
});

describe("AdminContentPages: unsaved wording", () => {
  it("keeps unsaved changes while the page is open and clears them when nothing differs", async () => {
    await show();

    const title = screen.getByLabelText(/Main title/);
    await userEvent.type(title, "!");

    await waitFor(() => expect(sessionStorage.getItem("essential-butterfly:draft:page:services")).not.toBeNull());

    await userEvent.type(title, "{Backspace}");

    await waitFor(() => expect(sessionStorage.getItem("essential-butterfly:draft:page:services")).toBeNull());
  });

  it("restores wording that was kept from before a sign-out and says so", async () => {
    const kept = defaultPageContent("services");
    kept.fields.heroTitle = "Typed before sign-out";
    sessionStorage.setItem("essential-butterfly:draft:page:services", JSON.stringify(kept));

    await show();

    expect(screen.getByLabelText(/Main title/)).toHaveValue("Typed before sign-out");
    expect(
      screen.getByText("We restored the changes you had before you were signed out. Review them, then save or publish.")
    ).toBeInTheDocument();
  });

  it("clears the kept wording after a successful save", async () => {
    await show();

    await userEvent.type(screen.getByLabelText(/Main title/), "!");
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));
    await screen.findByText(/Published\./);

    expect(sessionStorage.getItem("essential-butterfly:draft:page:services")).toBeNull();
  });
});

describe("AdminContentPages: preview", () => {
  it("shows the real page with the typed wording and hides it again", async () => {
    await show();

    const title = screen.getByLabelText(/Main title/);
    await userEvent.clear(title);
    await userEvent.type(title, "Preview this title");

    const toggle = screen.getByRole("button", { name: "Preview" });
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    await userEvent.click(toggle);

    const region = screen.getByRole("region", { name: "Preview of the page" });
    expect(within(region).getByText(/Nothing is saved yet\./)).toBeInTheDocument();
    expect(within(region).getByText("Preview this title")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Hide preview" }));
    expect(screen.queryByRole("region", { name: "Preview of the page" })).not.toBeInTheDocument();
  });

  it("does not save anything while previewing", async () => {
    const fetchMock = await show();

    await userEvent.click(screen.getByRole("button", { name: "Preview" }));

    expect(saves(fetchMock)).toHaveLength(0);
  });
});
