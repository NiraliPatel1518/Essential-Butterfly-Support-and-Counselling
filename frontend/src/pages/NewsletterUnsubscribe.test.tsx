import { describe, it, expect, vi, afterEach } from "vitest";
import { screen } from "@testing-library/react";
import NewsletterUnsubscribe from "./NewsletterUnsubscribe";
import { renderApp, jsonResponse } from "../test/render";

afterEach(() => vi.unstubAllGlobals());

// Helper: answers the unsubscribe call with the given reply,
// and answers every other call (like the feature list) with an empty list.
function stubFetch(unsubscribeReply: () => Promise<unknown>) {
  const fetchMock = vi.fn((url: string) =>
    url.includes("/api/newsletter/unsubscribe")
      ? unsubscribeReply()
      : jsonResponse([])
  );
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function unsubscribeCalls(fetchMock: ReturnType<typeof stubFetch>) {
  return fetchMock.mock.calls.filter(([url]) =>
    String(url).includes("/api/newsletter/unsubscribe")
  );
}

describe("NewsletterUnsubscribe", () => {
  it("shows an invalid link message when the token is missing", async () => {
    const fetchMock = stubFetch(() => jsonResponse({}));
    renderApp("/newsletter/unsubscribe", <NewsletterUnsubscribe />);

    expect(
      await screen.findByText("The unsubscribe link is invalid or incomplete.")
    ).toBeInTheDocument();
    expect(unsubscribeCalls(fetchMock)).toHaveLength(0);
  });

  it("sends the token to the server and shows the success message", async () => {
    const fetchMock = stubFetch(() =>
      jsonResponse({ message: "You have been unsubscribed." })
    );
    renderApp(
      "/newsletter/unsubscribe?token=abc%20123",
      <NewsletterUnsubscribe />
    );

    expect(
      await screen.findByText("You have been unsubscribed.")
    ).toBeInTheDocument();

    const calls = unsubscribeCalls(fetchMock);
    expect(calls).toHaveLength(1);
    expect(String(calls[0][0])).toContain(
      "/api/newsletter/unsubscribe?token=abc%20123"
    );
  });

  it("shows the server message when the link is not accepted", async () => {
    stubFetch(() => jsonResponse({ message: "Unsubscribe link not found." }, false, 404));
    renderApp("/newsletter/unsubscribe?token=bad", <NewsletterUnsubscribe />);

    expect(
      await screen.findByText("Unsubscribe link not found.")
    ).toBeInTheDocument();
  });

  it("shows an error message when the server cannot be reached", async () => {
    stubFetch(() => Promise.reject(new Error("Offline")));
    renderApp("/newsletter/unsubscribe?token=abc", <NewsletterUnsubscribe />);

    expect(await screen.findByText("Offline")).toBeInTheDocument();
  });

  it("always offers a link back to the home page", async () => {
    stubFetch(() => jsonResponse({ message: "Done." }));
    renderApp("/newsletter/unsubscribe?token=abc", <NewsletterUnsubscribe />);

    const link = await screen.findByRole("link", {
      name: /return to essential butterfly/i,
    });
    expect(link).toHaveAttribute("href", "/");
  });
});