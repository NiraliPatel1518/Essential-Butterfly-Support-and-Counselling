import { describe, it, expect, vi, afterEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import AppErrorBoundary from "./AppErrorBoundary";

/* AppErrorBoundary catches a crash on any page and shows a calm message instead of a blank screen. */

function Crash(): never {
  throw new Error("Page crashed");
}

function show(child = <p>All good</p>) {
  return render(
    <MemoryRouter>
      <AppErrorBoundary>{child}</AppErrorBoundary>
    </MemoryRouter>
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("AppErrorBoundary", () => {
  it("shows the page normally when nothing goes wrong", () => {
    show();

    expect(screen.getByText("All good")).toBeInTheDocument();
    expect(screen.queryByText("This page needs a moment")).not.toBeInTheDocument();
  });

  it("shows a friendly message when a page crashes", () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    show(<Crash />);

    expect(screen.getByRole("heading", { name: "This page needs a moment" })).toBeInTheDocument();
    expect(screen.getByText(/your information has not been submitted again/i)).toBeInTheDocument();
    expect(screen.getByRole("main")).toHaveAttribute("id", "main-content");
  });

  it("writes the crash to the console for the developers", () => {
    const spy = vi.spyOn(console, "error").mockImplementation(() => {});
    show(<Crash />);

    expect(spy.mock.calls.some(([first]) => first === "Unexpected page error")).toBe(true);
  });

  it("offers Refresh page and Return home", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    const reload = vi.fn();
    vi.stubGlobal("location", { ...window.location, reload });
    show(<Crash />);

    expect(screen.getByRole("link", { name: "Return home" })).toHaveAttribute("href", "/");
    await userEvent.click(screen.getByRole("button", { name: "Refresh page" }));

    expect(reload).toHaveBeenCalledTimes(1);
    vi.unstubAllGlobals();
  });
});