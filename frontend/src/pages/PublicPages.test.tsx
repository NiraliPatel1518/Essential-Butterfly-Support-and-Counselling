import { describe, it, expect, vi, afterEach } from "vitest";
import { render } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { PublicFeatureProvider } from "../context/PublicFeatureContext";
import Home from "./Home";
import About from "./About";
import Services from "./Services";

afterEach(() => {
  vi.unstubAllGlobals();
});

function renderPage(element: React.ReactNode) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: true,
      json: async () => [],
    })
  );

  return render(
    <MemoryRouter>
      <PublicFeatureProvider>{element}</PublicFeatureProvider>
    </MemoryRouter>
  );
}

describe("Public information pages", () => {
  it("renders homepage content", () => {
    const { container } = renderPage(<Home />);
    expect(container.querySelector("main")).toBeInTheDocument();
  });

  it("renders about page content", () => {
    const { container } = renderPage(<About />);
    expect(container.querySelector("main")).toBeInTheDocument();
  });

  it("renders services page content", () => {
    const { container } = renderPage(<Services />);
    expect(container.querySelector("main")).toBeInTheDocument();
  });
});