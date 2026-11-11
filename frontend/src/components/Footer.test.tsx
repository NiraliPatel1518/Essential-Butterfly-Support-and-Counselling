import { describe, it, expect, vi, afterEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { PublicFeatureProvider } from "../context/PublicFeatureContext";
import Footer from "./Footer";
afterEach(() => vi.unstubAllGlobals());
describe("Footer", () => {
  it("renders contact details and legal navigation", async () => { vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true, json: async () => [] })); render(<MemoryRouter><PublicFeatureProvider><Footer /></PublicFeatureProvider></MemoryRouter>); expect(await screen.findByRole("link", { name: /privacy policy/i })).toHaveAttribute("href", "/privacy"); expect(screen.getByRole("link", { name: /accessibility/i })).toHaveAttribute("href", "/accessibility"); expect(screen.getByRole("link", { name: /site map/i })).toHaveAttribute("href", "/sitemap"); expect(screen.getByText(/essentialbutterflyy@gmail.com/i)).toBeInTheDocument(); });
});
