import { describe, it, expect, vi, afterEach } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { PublicFeatureProvider } from "../context/PublicFeatureContext";
import Home from "./Home";
import About from "./About";
import Services from "./Services";
import Contact from "./Contact";

/*
 * Public pages after Casey's feedback.
 *
 * These tests check the content Casey asked for: her real contact details,
 * the new service names and descriptions, her story, availability and
 * service area, and the new logo and photos with clear alternative text.
 */

afterEach(() => {
  vi.unstubAllGlobals();
});

function renderPage(element: React.ReactNode) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({ ok: true, json: async () => [] })
  );

  return render(
    <MemoryRouter>
      <PublicFeatureProvider>{element}</PublicFeatureProvider>
    </MemoryRouter>
  );
}

describe("Contact page details", () => {
  it("shows Casey's email, phone and service area as links", () => {
    renderPage(<Contact />);

    expect(
      screen.getByRole("link", { name: /essentialbutterflyy@gmail\.com/i })
    ).toHaveAttribute("href", "mailto:essentialbutterflyy@gmail.com");

    expect(
      screen.getByRole("link", { name: /289-880-2387/ })
    ).toHaveAttribute("href", "tel:+12898802387");

    const location = screen.getByRole("link", {
      name: /oakville, burlington and waterdown/i,
    });
    expect(location.getAttribute("href")).toContain("Oakville%2C%20Burlington");
    expect(location).toHaveAttribute("target", "_blank");
  });

  it("shows the brand logo with alternative text", () => {
    renderPage(<Contact />);

    expect(
      screen.getByAltText("Essential Butterfly Support and Counselling")
    ).toHaveAttribute("src", "/images/essential-butterfly-logo.png");
  });

  it("no longer shows the old placeholder details", () => {
    renderPage(<Contact />);

    expect(screen.queryByText(/casey@essentialbutterfly\.com/i)).toBeNull();
    expect(screen.queryByText("(209) 258-0202")).toBeNull();
  });
});

describe("Home page", () => {
  it("shows the brand logo and Casey's photo with alternative text", () => {
    renderPage(<Home />);

    expect(
      screen.getByAltText("Essential Butterfly Support and Counselling")
    ).toHaveAttribute("src", "/images/essential-butterfly-logo.png");
    expect(
      screen.getByAltText(
        "Casey, founder of Essential Butterfly Support and Counselling"
      )
    ).toHaveAttribute("src", "/images/casey-bio.jpg");
  });

  it("lists the updated service names", () => {
    renderPage(<Home />);

    expect(
      screen.getByText("Tutoring and Assistive Tech / Tech Coaching")
    ).toBeInTheDocument();
    expect(
      screen.getByText("ADHD Coaching and Executive Function Coaching")
    ).toBeInTheDocument();
    expect(screen.getByText("Respite")).toBeInTheDocument();
    expect(screen.getByText("Counselling")).toBeInTheDocument();
  });
});

describe("About page", () => {
  it("introduces Casey as a social worker with 16 years of experience", () => {
    renderPage(<About />);

    expect(
      screen.getByRole("heading", { level: 1, name: /lived experience/i })
    ).toBeInTheDocument();
    expect(screen.getByText(/BSW social worker/i)).toHaveTextContent(
      /more than 16 years/i
    );
  });

  it("tells Casey's story and professional experience", () => {
    renderPage(<About />);

    expect(
      screen.getByRole("heading", {
        name: "Finding the tools that help us grow",
      })
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", {
        name: "Support across ages, needs and settings",
      })
    ).toBeInTheDocument();
    expect(screen.getByText(/diagnosed with a learning disability/i))
      .toBeInTheDocument();
  });

  it("shows Casey's photo with alternative text and a link to contact her", () => {
    renderPage(<About />);

    expect(
      screen.getByAltText(
        "Casey, founder of Essential Butterfly Support and Counselling"
      )
    ).toHaveAttribute("src", "/images/casey-bio.jpg");
    expect(
      screen.getAllByRole("link", { name: /start a conversation/i })[0]
    ).toHaveAttribute("href", "/contact");
  });
});

describe("Services page", () => {
  it("lists all six services", () => {
    renderPage(<Services />);

    const list = screen.getByRole("heading", { name: "What I offer" })
      .closest("section") as HTMLElement;
    const titles = within(list)
      .getAllByRole("heading", { level: 3 })
      .map((heading) => heading.textContent);

    expect(titles).toEqual([
      "Respite",
      "Social Skills and Life Skills Coaching",
      "Tutoring and Assistive Tech / Tech Coaching",
      "Counselling",
      "ADHD Coaching and Executive Function Coaching",
      "Animal-Based DBT (Dialectical Behaviour Therapy)",
    ]);
  });

  it("marks only the Animal-Based DBT service as in development", () => {
    renderPage(<Services />);

    const marks = screen.getAllByText("In development");
    expect(marks).toHaveLength(1);
    expect(
      marks[0].closest("article") as HTMLElement
    ).toHaveTextContent(/Animal-Based DBT/);
  });

  it("shows availability and the service area", () => {
    renderPage(<Services />);

    expect(
      screen.getByRole("heading", {
        name: "Flexible support outside regular daytime hours",
      })
    ).toBeInTheDocument();
    expect(screen.getByText(/Monday to Friday from 5:00 p\.m\. to 9:00 p\.m\./i))
      .toBeInTheDocument();
    expect(screen.getByText(/Oakville, Burlington and Waterdown/i))
      .toBeInTheDocument();
  });

  it("shows the hero photo with alternative text", () => {
    renderPage(<Services />);

    expect(
      screen.getByAltText(
        "A support professional having a calm conversation with a client"
      )
    ).toHaveAttribute("src", "/images/services-hero.jpg");
  });
});
