import { describe, it, expect, vi, afterEach } from "vitest";
import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MemoryWall from "./MemoryWall";
import { renderPage } from "../test/helpers";
import { jsonResponse } from "../test/render";
import type { WallMonth } from "../api/memoryWallApi";

const OCTOBER: WallMonth = {
  key: "2026-10",
  label: "October 2026",
  photos: [
    { id: 1, altText: "Children at the park", uploadedAt: "2026-10-12T10:00:00", uploadedOn: "October 12, 2026" },
    { id: 2, altText: "A birthday cake", uploadedAt: "2026-10-05T10:00:00", uploadedOn: "October 5, 2026" },
  ],
};

const SEPTEMBER: WallMonth = {
  key: "2026-09",
  label: "September 2026",
  photos: [{ id: 3, altText: "A butterfly on a flower", uploadedAt: "2026-09-20T10:00:00", uploadedOn: "September 20, 2026" }],
};

const SLIDE_MS = 7000;

function stubWall(reply: () => Promise<unknown>) {
  const fetchMock = vi.fn(() => reply());
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

async function show(months: WallMonth[] = [OCTOBER, SEPTEMBER]) {
  const fetchMock = stubWall(() => jsonResponse(months));
  renderPage("/memory-wall", <MemoryWall />);
  await waitFor(() => expect(screen.queryByText("Loading memories…")).not.toBeInTheDocument());
  return fetchMock;
}

// Catches the slideshow timer (the 7 second interval) so a test can fire it by hand.
function catchSlideTimer() {
  const realSetInterval = window.setInterval.bind(window);
  const callbacks: Array<() => void> = [];
  vi.spyOn(window, "setInterval").mockImplementation(((callback: () => void, delay?: number) => {
    if (delay === SLIDE_MS) {
      callbacks.push(callback);
      return callbacks.length;
    }
    return realSetInterval(callback, delay);
  }) as typeof window.setInterval);
  return {
    count: () => callbacks.length,
    fire: () => act(() => callbacks[callbacks.length - 1]()),
  };
}

function preferReducedMotion() {
  Object.defineProperty(window, "matchMedia", {
    configurable: true,
    writable: true,
    value: vi.fn(() => ({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  });
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  Reflect.deleteProperty(window, "matchMedia");
});

describe("MemoryWall page: the wall", () => {
  it("shows the heading and a loading message first", async () => {
    stubWall(() => jsonResponse([OCTOBER]));
    renderPage("/memory-wall", <MemoryWall />);

    expect(screen.getByRole("heading", { name: "Memory Wall", level: 1 })).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("Loading memories…");

    await screen.findByRole("region", { name: "Community memories" });
    expect(screen.queryByText("Loading memories…")).not.toBeInTheDocument();
  });

  it("loads the public wall without a sign-in token", async () => {
    const fetchMock = await show();

    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit | undefined];
    expect(String(url)).toMatch(/\/api\/memory-wall$/);
    expect(init?.headers).toBeUndefined();
  });

  it("shows the first photo with its description as the alternative text", async () => {
    await show();

    const image = screen.getByAltText("Children at the park");

    expect(image).toHaveAttribute("src", expect.stringMatching(/\/api\/memory-wall\/photos\/1\/image$/));
    expect(screen.getByRole("heading", { name: "Children at the park", level: 2 })).toBeInTheDocument();
    expect(screen.getByText(/Uploaded October 12, 2026/)).toBeInTheDocument();
    expect(screen.getByText(/Community memory · 1 of 3/)).toBeInTheDocument();
  });

  it("puts the photos of every month in one slideshow", async () => {
    await show();

    const dots = within(screen.getByRole("group", { name: "Choose a photo" })).getAllByRole("button");

    expect(dots).toHaveLength(3);
    expect(screen.queryByRole("heading", { name: /Happy moments/ })).not.toBeInTheDocument();
  });

  it("shows a friendly message when the wall is empty", async () => {
    await show([]);

    expect(screen.getByText("No photos have been published yet. Please check back soon.")).toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "Community memories" })).not.toBeInTheDocument();
  });

  it("shows the problem and a Try again button when loading fails", async () => {
    stubWall(() => jsonResponse({ message: "The Memory Wall is not available right now." }, false, 404));
    renderPage("/memory-wall", <MemoryWall />);

    const alert = await screen.findByRole("alert");

    expect(alert).toHaveTextContent("We couldn’t load the photos right now.");
    expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument();
    expect(screen.queryByText(/No photos have been published yet/)).not.toBeInTheDocument();
  });

  it("loads the wall again when Try again is pressed", async () => {
    let attempts = 0;
    stubWall(() => {
      attempts += 1;
      return attempts === 1 ? jsonResponse("", false, 500) : jsonResponse([OCTOBER]);
    });
    renderPage("/memory-wall", <MemoryWall />);

    await userEvent.click(await screen.findByRole("button", { name: "Try again" }));

    expect(await screen.findByRole("region", { name: "Community memories" })).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(attempts).toBe(2);
  });
});

describe("MemoryWall page: choosing a photo", () => {
  it("gives every photo button a clear spoken name and marks the one on show", async () => {
    await show();

    const first = screen.getByRole("button", { name: "Show photo 1: Children at the park" });

    expect(first).toHaveAttribute("aria-current", "true");
    expect(screen.getByRole("button", { name: "Show photo 2: A birthday cake" })).not.toHaveAttribute("aria-current");
    expect(screen.getByRole("button", { name: "Show photo 3: A butterfly on a flower" })).toBeInTheDocument();
  });

  it("shows the chosen photo with its caption, date and position", async () => {
    await show();

    await userEvent.click(screen.getByRole("button", { name: "Show photo 2: A birthday cake" }));

    expect(screen.getByAltText("A birthday cake")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "A birthday cake", level: 2 })).toBeInTheDocument();
    expect(screen.getByText(/Uploaded October 5, 2026/)).toBeInTheDocument();
    expect(screen.getByText(/Community memory · 2 of 3/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Show photo 2: A birthday cake" })).toHaveAttribute("aria-current", "true");
  });

  it("tells screen reader users which photo they chose", async () => {
    await show();

    await userEvent.click(screen.getByRole("button", { name: "Show photo 3: A butterfly on a flower" }));

    const announcement = screen.getAllByRole("status").find((element) => element.textContent?.startsWith("Photo 3 of 3"));
    expect(announcement).toHaveTextContent("Photo 3 of 3: A butterfly on a flower");
  });

  it("has no photo controls when there is only one photo", async () => {
    await show([SEPTEMBER]);

    expect(screen.getByAltText("A butterfly on a flower")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Pause slideshow|Play slideshow/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("group", { name: "Choose a photo" })).not.toBeInTheDocument();
  });
});

describe("MemoryWall page: the slideshow", () => {
  it("moves to the next photo by itself and goes back to the first after the last", async () => {
    const timer = catchSlideTimer();
    await show();
    expect(timer.count()).toBeGreaterThan(0);

    timer.fire();
    expect(screen.getByText(/Community memory · 2 of 3/)).toBeInTheDocument();

    timer.fire();
    expect(screen.getByText(/Community memory · 3 of 3/)).toBeInTheDocument();

    timer.fire();
    expect(screen.getByText(/Community memory · 1 of 3/)).toBeInTheDocument();
  });

  it("stops moving when Pause is pressed and moves again after Play", async () => {
    const timer = catchSlideTimer();
    await show();

    await userEvent.click(screen.getByRole("button", { name: "Pause slideshow" }));

    expect(screen.getByRole("button", { name: "Play slideshow" })).toBeInTheDocument();
    expect(screen.getByText(/Community memory · 1 of 3/)).toBeInTheDocument();

    const timersBefore = timer.count();
    await userEvent.click(screen.getByRole("button", { name: "Play slideshow" }));

    expect(screen.getByRole("button", { name: "Pause slideshow" })).toBeInTheDocument();

    // The mouse is still over the slideshow, which holds it. Moving away lets it run again.
    await userEvent.unhover(screen.getByRole("region", { name: "Community memories" }));
    expect(timer.count()).toBeGreaterThan(timersBefore);
  });

  it("holds the slideshow while the mouse is over it", async () => {
    const timer = catchSlideTimer();
    await show();
    const timersBefore = timer.count();

    await userEvent.hover(screen.getByRole("region", { name: "Community memories" }));
    await userEvent.unhover(screen.getByRole("region", { name: "Community memories" }));

    expect(timer.count()).toBeGreaterThan(timersBefore);
    expect(screen.getByRole("button", { name: "Pause slideshow" })).toBeInTheDocument();
  });

  it("stops the slideshow when the visitor chooses a photo", async () => {
    await show();

    await userEvent.click(screen.getByRole("button", { name: "Show photo 2: A birthday cake" }));

    expect(screen.getByRole("button", { name: "Play slideshow" })).toBeInTheDocument();
  });

  it("does not start by itself for visitors who prefer reduced motion", async () => {
    preferReducedMotion();
    const timer = catchSlideTimer();
    await show();

    expect(screen.getByRole("button", { name: "Play slideshow" })).toBeInTheDocument();
    expect(timer.count()).toBe(0);
    expect(screen.getByText(/Community memory · 1 of 3/)).toBeInTheDocument();
  });

  it("does not start a timer when there is only one photo", async () => {
    const timer = catchSlideTimer();
    await show([SEPTEMBER]);

    expect(timer.count()).toBe(0);
  });
});
