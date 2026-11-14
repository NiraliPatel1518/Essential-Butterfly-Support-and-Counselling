import { describe, it, expect, vi, afterEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import Home from "./Home";
import { renderApp, jsonResponse } from "../test/render";

type Feature = { featureKey: string; label: string; enabled: boolean; displayOrder: number };

const feature = (featureKey: string, enabled: boolean): Feature => ({ featureKey, label: featureKey, enabled, displayOrder: 1 });

type Testimonial = { id: number; displayName: string; content: string; photos?: string[] };

function stub(features: Feature[], testimonials: Testimonial[] | "fail" | "not-array" = []) {
  const fetchMock = vi.fn((url: string) => {
    const path = String(url);

    if (path.includes("/api/features")) return jsonResponse(features);

    if (path.includes("/api/testimonials/featured")) {
      if (testimonials === "fail") return Promise.reject(new Error("offline"));
      if (testimonials === "not-array") return jsonResponse({ oops: true });
      return jsonResponse(testimonials);
    }

    return jsonResponse([]);
  });

  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

async function show(features: Feature[], testimonials: Testimonial[] | "fail" | "not-array" = []) {
  const fetchMock = stub(features, testimonials);
  renderApp("/", <Home />);
  await waitFor(() => expect(fetchMock).toHaveBeenCalled());
  return fetchMock;
}

const ALL_ON = [feature("SERVICES", true), feature("CONTACT", true), feature("TESTIMONIALS", true)];

afterEach(() => vi.unstubAllGlobals());

describe("Home page: the fixed content", () => {
  it("is the main landmark with the headline", async () => {
    await show(ALL_ON);

    expect(screen.getByRole("main")).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(/Support to help you\s*work smarter,\s*not harder\./);
  });

  it("shows the three hero strengths", async () => {
    await show(ALL_ON);

    const strengths = Array.from(document.querySelectorAll(".hero-feature")).map((el) => el.textContent);
    expect(strengths).toEqual(["♧IndividualizedSupport", "✥PracticalStrategies", "♡Inclusive& Affirming Space"]);
  });

  it("shows the logo and the images with descriptions", async () => {
    await show(ALL_ON);

    expect(screen.getByRole("img", { name: "Essential Butterfly Support and Counselling" })).toHaveAttribute("src", "/images/essential-butterfly-logo.png");
    expect(screen.getByRole("img", { name: /Peaceful mountain landscape/ })).toBeInTheDocument();
    expect(screen.getByRole("img", { name: /Casey, founder of Essential Butterfly/ })).toBeInTheDocument();
  });

  it("lists the six services and each one links to the services page", async () => {
    await show(ALL_ON);

    const titles = [
      "Respite",
      "Animal-Based DBT Skills Coaching",
      "Tutoring and Assistive Tech / Tech Coaching",
      "Counselling",
      "ADHD Coaching and Executive Function Coaching",
      "Social Skills Support",
    ];

    for (const title of titles) {
      const link = screen.getByRole("link", { name: new RegExp(title.replace(/[/]/g, "\\/")) });
      expect(link).toHaveAttribute("href", "/services");
    }
  });

  it("introduces Casey with a link to the About page", async () => {
    await show(ALL_ON);

    expect(screen.getByRole("heading", { name: "Meet Casey" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /More about Casey/ })).toHaveAttribute("href", "/about");
  });

  it("explains how booking works", async () => {
    await show(ALL_ON);

    for (const heading of ["Book a time", "For new or existing clients", "Easy and convenient", "Clear communication"]) {
      expect(screen.getByRole("heading", { name: heading })).toBeInTheDocument();
    }
  });

  it("ends with a Book a Time button to the intake page", async () => {
    await show(ALL_ON);

    expect(screen.getByRole("link", { name: /Book a Time/ })).toHaveAttribute("href", "/intake");
  });
});

describe("Home page: website switches", () => {
  it("shows both hero buttons when Services and Contact are on", async () => {
    await show(ALL_ON);

    expect(await screen.findByRole("link", { name: /Explore Services/ })).toHaveAttribute("href", "/services");
    expect(screen.getByRole("link", { name: "Get in Touch" })).toHaveAttribute("href", "/contact");
  });

  it("hides the Services button when Services is off", async () => {
    await show([feature("SERVICES", false), feature("CONTACT", true)]);

    await screen.findByRole("link", { name: "Get in Touch" });
    expect(screen.queryByRole("link", { name: /Explore Services/ })).not.toBeInTheDocument();
  });

  it("hides the Contact button when Contact is off", async () => {
    await show([feature("SERVICES", true), feature("CONTACT", false)]);

    await screen.findByRole("link", { name: /Explore Services/ });
    expect(screen.queryByRole("link", { name: "Get in Touch" })).not.toBeInTheDocument();
  });

  it("still shows the core buttons when the switches cannot be loaded", async () => {
    vi.stubGlobal("fetch", vi.fn(() => jsonResponse({ message: "down" }, false, 500)));
    renderApp("/", <Home />);

    expect(await screen.findByRole("link", { name: /Explore Services/ })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Get in Touch" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Stories from our community" })).not.toBeInTheDocument();
  });
});

describe("Home page: testimonials", () => {
  const two: Testimonial[] = [
    { id: 1, displayName: "Alex", content: "Very helpful.", photos: ["/photos/a.jpg"] },
    { id: 2, displayName: "Sam", content: "Calm and kind." },
  ];

  it("shows the featured testimonials with a link to read each one", async () => {
    await show(ALL_ON, two);

    expect(await screen.findByRole("heading", { name: "Stories from our community" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 3, name: "Alex" })).toBeInTheDocument();
    expect(screen.getByText("Calm and kind.")).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "Read full testimonial →" })[0]).toHaveAttribute("href", "/testimonials?id=1");
    expect(screen.getByRole("link", { name: /Explore Testimonials/ })).toHaveAttribute("href", "/testimonials");
  });

  it("shows the photo only for a testimonial that has one", async () => {
    await show(ALL_ON, two);

    const alex = (await screen.findByRole("heading", { name: "Alex" })).closest("article")!;
    const sam = screen.getByRole("heading", { name: "Sam" }).closest("article")!;

    expect(alex.querySelector("img")).toHaveAttribute("src", "/photos/a.jpg");
    expect(sam.querySelector("img")).toBeNull();
  });

  it("shows at most two", async () => {
    await show(ALL_ON, [...two, { id: 3, displayName: "Third", content: "Also good." }]);

    await screen.findByRole("heading", { name: "Alex" });
    expect(screen.queryByRole("heading", { name: "Third" })).not.toBeInTheDocument();
    expect(within(screen.getByRole("main")).getAllByText(/Read full testimonial/)).toHaveLength(2);
  });

  it("shows nothing when there are no testimonials", async () => {
    await show(ALL_ON, []);

    await screen.findByRole("link", { name: /Explore Services/ });
    expect(screen.queryByRole("heading", { name: "Stories from our community" })).not.toBeInTheDocument();
  });

  it("does not ask for testimonials when the Testimonials switch is off", async () => {
    const fetchMock = await show([feature("SERVICES", true), feature("TESTIMONIALS", false)], two);

    await screen.findByRole("link", { name: /Explore Services/ });
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes("/api/testimonials/featured"))).toBe(false);
    expect(screen.queryByRole("heading", { name: "Stories from our community" })).not.toBeInTheDocument();
  });

  it("shows nothing when the testimonials cannot be loaded", async () => {
    await show(ALL_ON, "fail");

    await screen.findByRole("link", { name: /Explore Services/ });
    expect(screen.queryByRole("heading", { name: "Stories from our community" })).not.toBeInTheDocument();
  });

  it("shows nothing when the server sends something that is not a list", async () => {
    await show(ALL_ON, "not-array");

    await screen.findByRole("link", { name: /Explore Services/ });
    expect(screen.queryByRole("heading", { name: "Stories from our community" })).not.toBeInTheDocument();
  });
});
