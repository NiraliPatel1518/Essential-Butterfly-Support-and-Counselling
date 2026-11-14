import { describe, it, expect, beforeEach } from "vitest";
import { screen, within } from "@testing-library/react";
import About from "./About";
import Services from "./Services";
import { Privacy } from "./InformationPages";
import { DEFAULT_SERVICES, PAGE_LAYOUTS } from "../content/pageLayouts";
import { mockFetch, mockFetchNetworkError, mockFetchSequence, renderPage } from "../test/helpers";

function published(pageKey: string, body: object) {
  return JSON.stringify({
    pageKey,
    title: pageKey,
    body: JSON.stringify(body),
    publishedAt: "2026-01-01T00:00:00",
  });
}

function original(pageKey: "about" | "privacy" | "services", key: string): string {
  return PAGE_LAYOUTS[pageKey].slots.find((slot) => slot.key === key)!.default;
}

beforeEach(() => {
  sessionStorage.clear();
});

describe("About page", () => {
  it("shows the original wording when nothing is published", async () => {
    mockFetchSequence({ ok: true, status: 204, body: "" });
    renderPage("/about", <About />);

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(original("about", "heroTitle"));
    expect(screen.getByRole("link", { name: original("about", "heroButton") })).toHaveAttribute("href", "/contact");
  });

  it("shows published wording where an administrator changed it, and the original elsewhere", async () => {
    mockFetch(true, published("about", { fields: { heroTitle: "A warm welcome" } }));
    renderPage("/about", <About />);

    expect(await screen.findByRole("heading", { level: 1, name: "A warm welcome" })).toBeInTheDocument();
    expect(screen.getByText(original("about", "storyTitle"))).toBeInTheDocument();
  });

  it("keeps the original page when the server cannot be reached", async () => {
    mockFetchNetworkError();
    renderPage("/about", <About />);

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(original("about", "heroTitle"));
  });

  it("keeps the photo and its description whatever the wording", async () => {
    mockFetch(true, published("about", { fields: { heroTitle: "A warm welcome" } }));
    renderPage("/about", <About />);

    await screen.findByText("A warm welcome");

    expect(screen.getByRole("img", { name: /Casey, founder/ })).toBeInTheDocument();
  });

  it("shows unsaved preview wording inside a div instead of the page's main area", () => {
    const { container } = renderPage(
      "/about",
      <About preview={{ fields: { heroTitle: "Draft title", heroButton: "Draft button" } }} />
    );

    expect(screen.getByRole("heading", { level: 1, name: "Draft title" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Draft button" })).toBeInTheDocument();
    expect(container.querySelector("main")).toBeNull();
  });

  it("is a main area when it is the real page", () => {
    mockFetchSequence({ ok: true, status: 204, body: "" });
    const { container } = renderPage("/about", <About />);

    expect(container.querySelector("main")).not.toBeNull();
  });
});

describe("Services page", () => {
  it("shows the built-in services when nothing is published", () => {
    mockFetchSequence({ ok: true, status: 204, body: "" });
    renderPage("/services", <Services />);

    for (const service of DEFAULT_SERVICES) {
      expect(screen.getByRole("heading", { level: 3, name: service.title })).toBeInTheDocument();
    }
  });

  it("numbers the services in order", () => {
    mockFetchSequence({ ok: true, status: 204, body: "" });
    const { container } = renderPage("/services", <Services />);

    const numbers = Array.from(container.querySelectorAll(".service-number")).map((node) => node.textContent);

    expect(numbers[0]).toBe("01");
    expect(numbers[DEFAULT_SERVICES.length - 1]).toBe(String(DEFAULT_SERVICES.length).padStart(2, "0"));
  });

  it("replaces the services with the published list", async () => {
    mockFetch(
      true,
      published("services", {
        fields: {},
        services: [
          { id: "coaching", title: "Weekly coaching", text: "Practical support." },
          { id: "dbt", title: "New group", text: "Starting soon.", comingSoon: true },
        ],
      })
    );
    renderPage("/services", <Services />);

    expect(await screen.findByRole("heading", { level: 3, name: "Weekly coaching" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 3, name: "New group" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { level: 3, name: "Counselling" })).not.toBeInTheDocument();
  });

  it("links a normal service to the contact page and marks an upcoming one as in development", async () => {
    mockFetch(
      true,
      published("services", {
        fields: {},
        services: [
          { id: "coaching", title: "Weekly coaching", text: "Practical support." },
          { id: "dbt", title: "New group", text: "Starting soon.", comingSoon: true },
        ],
      })
    );
    renderPage("/services", <Services />);

    await screen.findByText("Weekly coaching");

    expect(screen.getByRole("link", { name: "Ask about Weekly coaching" })).toHaveAttribute("href", "/contact");
    expect(screen.queryByRole("link", { name: "Ask about New group" })).not.toBeInTheDocument();
    expect(screen.getAllByText("In development")).toHaveLength(1);
  });

  it("shows published wording for the page's headings", async () => {
    mockFetch(true, published("services", { fields: { heroTitle: "Support that fits" } }));
    renderPage("/services", <Services />);

    expect(await screen.findByRole("heading", { level: 1, name: "Support that fits" })).toBeInTheDocument();
  });

  it("keeps the built-in services when the published list is damaged", async () => {
    mockFetch(true, published("services", { fields: {}, services: [{ id: "x", title: "", text: "" }] }));
    renderPage("/services", <Services />);

    expect(await screen.findByRole("heading", { level: 3, name: DEFAULT_SERVICES[0].title })).toBeInTheDocument();
  });

  it("sends the primary button to secure intake and the other to contact", () => {
    mockFetchSequence({ ok: true, status: 204, body: "" });
    renderPage("/services", <Services />);

    expect(screen.getByRole("link", { name: original("services", "primaryButton") })).toHaveAttribute("href", "/intake");
    expect(screen.getByRole("link", { name: original("services", "secondaryButton") })).toHaveAttribute("href", "/contact");
  });

  it("shows unsaved preview services without asking the server", () => {
    const fetchMock = mockFetch(true, "{}");

    renderPage(
      "/services",
      <Services
        preview={{
          fields: { heroTitle: "Draft" },
          services: [{ id: "a", title: "Draft service", text: "Draft text" }],
        }}
      />
    );

    const list = screen.getByRole("heading", { level: 3, name: "Draft service" }).closest("article")!;
    expect(within(list).getByText("Draft text")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("Privacy page", () => {
  it("shows the original wording when nothing is published", () => {
    mockFetchSequence({ ok: true, status: 204, body: "" });
    renderPage("/privacy", <Privacy />);

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(original("privacy", "title"));
    expect(screen.getByText(original("privacy", "p1"))).toBeInTheDocument();
    expect(screen.getByRole("link", { name: original("privacy", "linkText") })).toHaveAttribute("href", "/contact");
  });

  it("shows the published policy text", async () => {
    mockFetch(
      true,
      published("privacy", { fields: { title: "Our privacy promise", p1: "We keep your details safe." } })
    );
    renderPage("/privacy", <Privacy />);

    expect(await screen.findByRole("heading", { level: 1, name: "Our privacy promise" })).toBeInTheDocument();
    expect(screen.getByText("We keep your details safe.")).toBeInTheDocument();
    expect(screen.getByText(original("privacy", "p2"))).toBeInTheDocument();
  });

  it("shows unsaved preview wording", () => {
    mockFetch(true, "{}");
    renderPage("/privacy", <Privacy preview={{ fields: { title: "Draft", p1: "One", p2: "Two", linkText: "Ask" } }} />);

    expect(screen.getByRole("heading", { level: 1, name: "Draft" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ask" })).toBeInTheDocument();
  });
});
