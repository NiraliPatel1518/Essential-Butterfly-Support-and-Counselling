import { describe, it, expect, vi, afterEach } from "vitest";
import { screen } from "@testing-library/react";
import Updates from "./Updates";
import { renderApp } from "../test/render";
afterEach(() => vi.unstubAllGlobals());
describe("Updates", () => {
  it("renders public updates list", async () => { vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true, json: async () => [{ id: 1, title: "Spring update", preview: "News from Casey", createdAt: "2026-01-01", updatedAt: "2026-01-01" }] })); renderApp("/updates", <Updates />); expect(await screen.findByRole("heading", { name: /updates from essential butterfly/i })).toBeInTheDocument(); expect(await screen.findByText("Spring update")).toBeInTheDocument(); });
  it("shows an error when updates cannot be loaded", async () => { vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("Offline"))); renderApp("/updates", <Updates />); expect(await screen.findByRole("heading", { name: /unable to load updates/i })).toBeInTheDocument(); });
});
