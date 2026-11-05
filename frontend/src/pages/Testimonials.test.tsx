import { describe, it, expect, vi, afterEach } from "vitest";
import { screen } from "@testing-library/react";

import Testimonials from "./Testimonials";
import { renderApp } from "../test/render";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("Testimonials", () => {
  it("loads public testimonials", async () => {
    const fetchMock = vi.fn().mockImplementation((url: string) => {
      if (String(url).includes("/api/features")) {
        return Promise.resolve({
          ok: true,
          json: async () => [
            {
              featureKey: "TESTIMONIALS",
              label: "Testimonials",
              enabled: true,
              displayOrder: 1,
            },
          ],
        });
      }

      return Promise.resolve({
        ok: true,
        json: async () => [
          {
            id: 1,
            displayName: "Community member",
            content: "A helpful experience",
            photos: [],
          },
        ],
      });
    });

    vi.stubGlobal("fetch", fetchMock);

    renderApp("/testimonials", <Testimonials />);

    expect(
      await screen.findByText("Community member")
    ).toBeInTheDocument();

    expect(
      screen.getByText("A helpful experience")
    ).toBeInTheDocument();
  });

  it("shows empty state when no testimonials are published", async () => {
    const fetchMock = vi.fn().mockImplementation((url: string) => {
      if (String(url).includes("/api/features")) {
        return Promise.resolve({
          ok: true,
          json: async () => [
            {
              featureKey: "TESTIMONIALS",
              label: "Testimonials",
              enabled: true,
              displayOrder: 1,
            },
          ],
        });
      }

      return Promise.resolve({
        ok: true,
        json: async () => [],
      });
    });

    vi.stubGlobal("fetch", fetchMock);

    renderApp("/testimonials", <Testimonials />);

    expect(
      await screen.findByText(/no published testimonials yet/i)
    ).toBeInTheDocument();
  });
});