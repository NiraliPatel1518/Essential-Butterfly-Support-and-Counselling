import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { Accessibility, Privacy, Sitemap } from "./InformationPages";
describe("Information pages", () => {
  it("renders privacy page", () => { render(<MemoryRouter><Privacy /></MemoryRouter>); expect(screen.getByRole("heading", { name: /privacy/i })).toBeInTheDocument(); });
  it("renders accessibility page", () => { render(<MemoryRouter><Accessibility /></MemoryRouter>); expect(screen.getByRole("heading", { name: /accessibility/i })).toBeInTheDocument(); });
  it("renders sitemap navigation", () => { render(<MemoryRouter><Sitemap /></MemoryRouter>); expect(screen.getByRole("heading", { name: /site map/i })).toBeInTheDocument(); expect(screen.getByRole("navigation", { name: /site map/i })).toBeInTheDocument(); });
});
