import { describe, it, expect, vi, afterEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { PublicFeatureProvider } from "../context/PublicFeatureContext";
import NewsletterSignup from "./NewsletterSignup";

afterEach(() => {
  vi.unstubAllGlobals();
});

function mount(fetchMock: ReturnType<typeof vi.fn>) {
  vi.stubGlobal("fetch", fetchMock);

  return render(
    <MemoryRouter>
      <PublicFeatureProvider>
        <NewsletterSignup />
      </PublicFeatureProvider>
    </MemoryRouter>
  );
}

describe("NewsletterSignup", () => {
  it("renders when newsletter is enabled by default", async () => {
    mount(
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => [],
      })
    );

    expect(
      await screen.findByRole("button", { name: /subscribe/i })
    ).toBeInTheDocument();
  });

  it("hides when newsletter is disabled", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => [
        {
          featureKey: "NEWSLETTER",
          enabled: false,
        },
      ],
    });

    mount(fetchMock);

    await waitFor(() => {
      expect(
        screen.queryByRole("button", { name: /subscribe/i })
      ).not.toBeInTheDocument();
    });
  });

  it("submits an email with consent", async () => {
    const user = userEvent.setup();

    const fetchMock = vi.fn().mockImplementation((url: string) => {
      if (String(url).includes("/api/features")) {
        return Promise.resolve({
          ok: true,
          json: async () => [],
        });
      }

      return Promise.resolve({
        ok: true,
        json: async () => ({
          message: "Subscription received.",
        }),
      });
    });

    mount(fetchMock);

    const email = await screen.findByLabelText("Email address");

    await user.type(email, "reader@example.com");
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: /subscribe/i }));

    await waitFor(() => {
      expect(
        fetchMock.mock.calls.some(([url]) =>
          String(url).includes("/api/newsletter/subscribe")
        )
      ).toBe(true);
    });
  });
});