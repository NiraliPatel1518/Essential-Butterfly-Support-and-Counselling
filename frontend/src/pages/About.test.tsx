import { describe, it, expect, vi, afterEach } from "vitest";
import { screen } from "@testing-library/react";
import About from "./About";
import { renderApp, jsonResponse } from "../test/render";
import { PAGE_LAYOUTS, defaultPageContent, serializePageContent } from "../content/pageLayouts";

function stub(published: unknown | null) {
  vi.stubGlobal(
    "fetch",
    vi.fn((url: string) => {
      if (String(url).includes("/api/content/about")) {
        return published ? jsonResponse(published) : jsonResponse({ message: "none" }, false, 404);
      }
      return jsonResponse([]);
    })
  );
}

const original = (key: string) => PAGE_LAYOUTS.about.slots.find((s) => s.key === key)!.default;

afterEach(() => {
  vi.unstubAllGlobals();
  sessionStorage.clear();
});

describe("About page", () => {
  it("shows the original wording when nothing has been published", async () => {
    stub(null);
    renderApp("/about", <About />);

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(original("heroTitle"));
    expect(screen.getByText(original("heroLead"))).toBeInTheDocument();
  });

  it("is a main landmark", () => {
    stub(null);
    renderApp("/about", <About />);

    expect(screen.getByRole("main")).toBeInTheDocument();
  });

  it("shows Casey's photo with a description", () => {
    stub(null);
    renderApp("/about", <About />);

    expect(
      screen.getByRole("img", { name: "Casey, founder of Essential Butterfly Support and Counselling" })
    ).toHaveAttribute("src", "/images/casey-bio.jpg");
  });

  it("has four values and a section heading for each part of the page", () => {
    stub(null);
    renderApp("/about", <About />);

    expect(screen.getAllByRole("article")).toHaveLength(4);
    expect(screen.getAllByRole("heading", { level: 2 })).toHaveLength(4);
  });

  it("links the buttons to the contact page", () => {
    stub(null);
    renderApp("/about", <About />);

    const links = screen.getAllByRole("link");
    expect(links).toHaveLength(2);
    links.forEach((link) => expect(link).toHaveAttribute("href", "/contact"));
  });

  it("shows wording that an administrator has published", async () => {
    const content = defaultPageContent("about");
    content.fields.heroTitle = "Published about title";
    stub({ pageKey: "about", title: "About Casey", body: serializePageContent(content), publishedAt: "2026-11-01T10:00:00" });
    renderApp("/about", <About />);

    expect(await screen.findByRole("heading", { level: 1, name: "Published about title" })).toBeInTheDocument();
  });

  it("keeps the original wording when the server cannot be reached", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.reject(new Error("offline"))));
    renderApp("/about", <About />);

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(original("heroTitle"));
  });

  it("shows unsaved wording in a preview, inside a div and without asking the server", () => {
    const fetchMock = vi.fn((_url: string) => jsonResponse([]));
    vi.stubGlobal("fetch", fetchMock);
    const content = defaultPageContent("about");
    content.fields.heroTitle = "Preview title";

    renderApp("/about", <About preview={content} />);

    expect(screen.getByRole("heading", { level: 1, name: "Preview title" })).toBeInTheDocument();
    expect(screen.queryByRole("main")).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes("/api/content/"))).toBe(false);
  });
});
