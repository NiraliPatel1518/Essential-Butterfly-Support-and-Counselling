import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { fireEvent, screen, waitFor, within } from "@testing-library/react";
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

function stubWall(reply: () => Promise<unknown>) {
  const fetchMock = vi.fn(() => reply());
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

async function show(months: WallMonth[] = [OCTOBER, SEPTEMBER]) {
  const fetchMock = stubWall(() => jsonResponse(months));
  renderPage("/memory-wall", <MemoryWall />);
  await waitFor(() => expect(screen.queryByText("Loading the Memory Wall…")).not.toBeInTheDocument());
  return fetchMock;
}

async function openPhoto(name: RegExp) {
  await userEvent.click(screen.getByRole("button", { name }));
  return screen.findByRole("dialog", { name: "Memory Wall photo" });
}

beforeEach(() => {
  // jsdom has no real modal dialog, so a small stand-in opens and closes it.
  HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
    this.setAttribute("open", "");
  };
  HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
    this.removeAttribute("open");
    this.dispatchEvent(new Event("close"));
  };
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("MemoryWall page: the wall", () => {
  it("shows the heading and a loading message first", async () => {
    stubWall(() => jsonResponse([OCTOBER]));
    renderPage("/memory-wall", <MemoryWall />);

    expect(screen.getByRole("heading", { name: "Memory Wall" })).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("Loading the Memory Wall…");

    await screen.findByRole("heading", { name: /October 2026/ });
    expect(screen.queryByText("Loading the Memory Wall…")).not.toBeInTheDocument();
  });

  it("loads the public wall without a sign-in token", async () => {
    const fetchMock = await show();

    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit | undefined];
    expect(String(url)).toMatch(/\/api\/memory-wall$/);
    expect(init?.headers).toBeUndefined();
  });

  it("groups photos under a heading for each month", async () => {
    await show();

    expect(screen.getByRole("heading", { name: "Happy moments in October 2026" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Happy moments in September 2026" })).toBeInTheDocument();

    const october = screen.getByRole("heading", { name: /October/ }).closest("section")!;
    expect(within(october).getAllByRole("button")).toHaveLength(2);

    const september = screen.getByRole("heading", { name: /September/ }).closest("section")!;
    expect(within(september).getAllByRole("button")).toHaveLength(1);
  });

  it("shows each picture with its description as the alternative text", async () => {
    await show();

    const image = screen.getByAltText("Children at the park");

    expect(image).toHaveAttribute("src", expect.stringMatching(/\/api\/memory-wall\/photos\/1\/image$/));
    expect(image).toHaveAttribute("loading", "lazy");
    expect(screen.getByAltText("A birthday cake")).toBeInTheDocument();
    expect(screen.getByAltText("A butterfly on a flower")).toBeInTheDocument();
  });

  it("gives every photo button a clear spoken name", async () => {
    await show();

    expect(
      screen.getByRole("button", { name: "Enlarge photo: Children at the park. Uploaded October 12, 2026." })
    ).toBeInTheDocument();
  });

  it("shows a friendly message when the wall is empty", async () => {
    await show([]);

    expect(screen.getByText("New memories are on their way. Please check back soon.")).toBeInTheDocument();
  });

  it("shows the problem and a Try again button when loading fails", async () => {
    stubWall(() => jsonResponse({ message: "The Memory Wall is not available right now." }, false, 404));
    renderPage("/memory-wall", <MemoryWall />);

    const alert = await screen.findByRole("alert");

    expect(alert).toHaveTextContent("The Memory Wall is not available right now.");
    expect(within(alert).getByRole("button", { name: "Try again" })).toBeInTheDocument();
    expect(screen.queryByText(/New memories are on their way/)).not.toBeInTheDocument();
  });

  it("loads the wall again when Try again is pressed", async () => {
    let attempts = 0;
    stubWall(() => {
      attempts += 1;
      return attempts === 1 ? jsonResponse("", false, 500) : jsonResponse([OCTOBER]);
    });
    renderPage("/memory-wall", <MemoryWall />);

    await userEvent.click(await screen.findByRole("button", { name: "Try again" }));

    expect(await screen.findByRole("heading", { name: /October 2026/ })).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});

describe("MemoryWall page: the large photo", () => {
  it("opens the chosen photo with its caption, date and position", async () => {
    await show();

    const dialog = await openPhoto(/Enlarge photo: A birthday cake/);

    expect(within(dialog).getByRole("img", { name: "A birthday cake" })).toBeInTheDocument();
    expect(within(dialog).getByText(/A birthday cake/)).toBeInTheDocument();
    expect(within(dialog).getByText(/Uploaded October 5, 2026/)).toBeInTheDocument();
    expect(within(dialog).getByText("Photo 2 of 3")).toBeInTheDocument();
  });

  it("the Next button moves on and the last photo goes back to the first", async () => {
    await show();

    const dialog = await openPhoto(/Enlarge photo: Children at the park/);
    expect(within(dialog).getByText("Photo 1 of 3")).toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole("button", { name: /Next/ }));
    expect(within(dialog).getByText("Photo 2 of 3")).toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole("button", { name: /Next/ }));
    expect(within(dialog).getByText("Photo 3 of 3")).toBeInTheDocument();
    expect(within(dialog).getByRole("img", { name: "A butterfly on a flower" })).toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole("button", { name: /Next/ }));
    expect(within(dialog).getByText("Photo 1 of 3")).toBeInTheDocument();
  });

  it("the Previous button on the first photo goes to the last", async () => {
    await show();

    const dialog = await openPhoto(/Enlarge photo: Children at the park/);
    await userEvent.click(within(dialog).getByRole("button", { name: /Previous/ }));

    expect(within(dialog).getByText("Photo 3 of 3")).toBeInTheDocument();
  });

  it("the arrow keys move between photos", async () => {
    await show();

    const dialog = await openPhoto(/Enlarge photo: Children at the park/);

    fireEvent.keyDown(dialog, { key: "ArrowRight" });
    expect(within(dialog).getByText("Photo 2 of 3")).toBeInTheDocument();

    fireEvent.keyDown(dialog, { key: "ArrowLeft" });
    fireEvent.keyDown(dialog, { key: "ArrowLeft" });
    expect(within(dialog).getByText("Photo 3 of 3")).toBeInTheDocument();
  });

  it("Previous and Next are off when there is only one photo", async () => {
    await show([SEPTEMBER]);

    const dialog = await openPhoto(/Enlarge photo: A butterfly/);

    expect(within(dialog).getByText("Photo 1 of 1")).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: /Previous/ })).toBeDisabled();
    expect(within(dialog).getByRole("button", { name: /Next/ })).toBeDisabled();
  });

  it("closes with the Close button and gives focus back to the photo", async () => {
    await show();

    const opener = screen.getByRole("button", { name: /Enlarge photo: A birthday cake/ });
    const dialog = await openPhoto(/Enlarge photo: A birthday cake/);

    await userEvent.click(within(dialog).getByRole("button", { name: "Close" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(opener).toHaveFocus();
  });
});
