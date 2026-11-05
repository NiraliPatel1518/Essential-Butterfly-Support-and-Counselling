import { describe, it, expect, vi, afterEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { PublicFeatureProvider } from "../context/PublicFeatureContext";
import PublicFeatureRoute from "./PublicFeatureRoute";

afterEach(() => vi.unstubAllGlobals());
function setup(fetchMock: ReturnType<typeof vi.fn>) { vi.stubGlobal("fetch", fetchMock); return render(<MemoryRouter><PublicFeatureProvider><PublicFeatureRoute feature="TESTIMONIALS"><h1>Testimonials content</h1></PublicFeatureRoute></PublicFeatureProvider></MemoryRouter>); }
describe("PublicFeatureRoute", () => {
  it("shows a loading state while checking features", () => { setup(vi.fn(() => new Promise(() => {}))); expect(screen.getByRole("heading", { name: /loading website feature/i })).toBeInTheDocument(); });
  it("renders children when the feature is enabled", async () => { setup(vi.fn().mockResolvedValue({ ok: true, json: async () => [{ featureKey: "TESTIMONIALS", enabled: true }] })); expect(await screen.findByText("Testimonials content")).toBeInTheDocument(); });
  it("shows unavailable state when disabled", async () => { setup(vi.fn().mockResolvedValue({ ok: true, json: async () => [{ featureKey: "TESTIMONIALS", enabled: false }] })); expect(await screen.findByRole("heading", { name: "Feature unavailable" })).toBeInTheDocument(); });
  it("shows retry when feature loading fails", async () => { const f = vi.fn().mockRejectedValueOnce(new Error("Offline")).mockResolvedValueOnce({ ok: true, json: async () => [{ featureKey: "TESTIMONIALS", enabled: true }] }); setup(f); expect(await screen.findByRole("heading", { name: /unable to check feature availability/i })).toBeInTheDocument(); await userEvent.click(screen.getByRole("button", { name: "Retry" })); expect(await screen.findByText("Testimonials content")).toBeInTheDocument(); });
});
