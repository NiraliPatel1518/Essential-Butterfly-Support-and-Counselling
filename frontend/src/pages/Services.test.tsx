import { describe, it, expect, vi, afterEach } from "vitest";
import { screen, within } from "@testing-library/react";
import Services from "./Services";
import { renderApp, jsonResponse } from "../test/render";
import { PAGE_LAYOUTS, defaultPageContent, serializePageContent } from "../content/pageLayouts";

function stub(published: unknown | null) {
  vi.stubGlobal(
    "fetch",
    vi.fn((url: string) => {
      if (String(url).includes("/api/content/services")) {
        return published ? jsonResponse(published) : jsonResponse({ message: "none" }, false, 404);
      }
      return jsonResponse([]);
    })
  );
}

const original = (key: string) => PAGE_LAYOUTS.services.slots.find((s) => s.key === key)!.default;

afterEach(() => {
  vi.unstubAllGlobals();
  sessionStorage.clear();
});

describe("Services page", () => {
  it("shows the original wording when nothing has been published", () => {
    stub(null);
    renderApp("/services", <Services />);

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(original("heroTitle"));
    expect(screen.getByRole("main")).toBeInTheDocument();
  });

  it("shows the hero photo with a description", () => {
    stub(null);
    renderApp("/services", <Services />);

    expect(
      screen.getByRole("img", { name: "A support professional having a calm conversation with a client" })
    ).toBeInTheDocument();
  });

  it("shows every service as a numbered card", () => {
    stub(null);
    renderApp("/services", <Services />);

    const services = defaultPageContent("services").services!;
    const cards = screen.getAllByRole("article");

    expect(cards).toHaveLength(services.length);
    expect(within(cards[0]).getByText("01")).toBeInTheDocument();
    expect(within(cards[0]).getByRole("heading", { level: 3, name: services[0].title })).toBeInTheDocument();
  });

  it("gives a service that is ready a link to ask about it", () => {
    stub(null);
    renderApp("/services", <Services />);

    const services = defaultPageContent("services").services!;
    const ready = services.find((s) => !s.comingSoon)!;

    expect(screen.getByRole("link", { name: `Ask about ${ready.title}` })).toHaveAttribute("href", "/contact");
  });

  it("shows a service in development without a link", async () => {
    const content = defaultPageContent("services");
    content.services = [
      { id: "a", title: "Ready service", text: "Text A", comingSoon: false },
      { id: "b", title: "Later service", text: "Text B", comingSoon: true },
    ];
    stub({ pageKey: "services", title: "Services", body: serializePageContent(content), publishedAt: "2026-11-01T10:00:00" });
    renderApp("/services", <Services />);

    const later = (await screen.findByRole("heading", { name: "Later service" })).closest("article")!;

    expect(within(later).getByText("In development")).toBeInTheDocument();
    expect(within(later).queryByRole("link")).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Ask about Later service" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ask about Ready service" })).toBeInTheDocument();
  });

  it("links the two bottom buttons to the intake and contact pages", () => {
    stub(null);
    renderApp("/services", <Services />);

    expect(screen.getByRole("link", { name: original("primaryButton") })).toHaveAttribute("href", "/intake");
    expect(screen.getByRole("link", { name: original("secondaryButton") })).toHaveAttribute("href", "/contact");
  });

  it("shows the three steps in order", () => {
    stub(null);
    renderApp("/services", <Services />);

    expect(screen.getAllByRole("listitem")).toHaveLength(3);
  });

  it("shows wording that an administrator has published", async () => {
    const content = defaultPageContent("services");
    content.fields.heroTitle = "Published services title";
    stub({ pageKey: "services", title: "Services", body: serializePageContent(content), publishedAt: "2026-11-01T10:00:00" });
    renderApp("/services", <Services />);

    expect(await screen.findByRole("heading", { level: 1, name: "Published services title" })).toBeInTheDocument();
  });

  it("shows unsaved wording in a preview, inside a div and without asking the server", () => {
    const fetchMock = vi.fn((_url: string) => jsonResponse([]));
    vi.stubGlobal("fetch", fetchMock);
    const content = defaultPageContent("services");
    content.fields.heroTitle = "Preview title";

    renderApp("/services", <Services preview={content} />);

    expect(screen.getByRole("heading", { level: 1, name: "Preview title" })).toBeInTheDocument();
    expect(screen.queryByRole("main")).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes("/api/content/"))).toBe(false);
  });
});
