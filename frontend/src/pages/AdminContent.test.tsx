import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AdminContent from "./AdminContent";
import { renderApp, jsonResponse } from "../test/render";

beforeEach(() => {
  localStorage.setItem("adminToken", "admin-token");
  vi.stubGlobal(
    "fetch",
    vi.fn((url: string) => {
      const path = String(url);
      if (path.includes("upload-rules"))
        return jsonResponse({ maxFileSizeBytes: 1000, maxFileSizeLabel: "1 KB", allowedExtensions: [], acceptedFormats: "" });
      return jsonResponse([]);
    })
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("AdminContent", () => {
  it("shows the heading and the three tabs", async () => {
    renderApp("/admin", <AdminContent />);

    expect(screen.getByRole("heading", { name: "Manage Website Content" })).toBeInTheDocument();
    const nav = screen.getByRole("navigation", { name: "Website content sections" });
    expect(nav).toHaveTextContent("Website pages");
    expect(nav).toHaveTextContent("Resource Library");
    expect(nav).toHaveTextContent("Recent changes");
    await screen.findByText("No editable pages were found.");
  });

  it("opens on the website pages tab", async () => {
    renderApp("/admin", <AdminContent />);

    expect(screen.getByRole("button", { name: "Website pages" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("button", { name: "Resource Library" })).not.toHaveAttribute("aria-current");
    await screen.findByText("No editable pages were found.");
  });

  it("switches to the recent changes tab", async () => {
    renderApp("/admin", <AdminContent />);
    await screen.findByText("No editable pages were found.");

    await userEvent.click(screen.getByRole("button", { name: "Recent changes" }));

    expect(await screen.findByText("No changes have been recorded yet.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Recent changes" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("button", { name: "Website pages" })).not.toHaveAttribute("aria-current");
    expect(screen.queryByText("No editable pages were found.")).not.toBeInTheDocument();
  });

  it("switches to the resource library tab", async () => {
    renderApp("/admin", <AdminContent />);
    await screen.findByText("No editable pages were found.");

    await userEvent.click(screen.getByRole("button", { name: "Resource Library" }));

    expect(screen.getByRole("button", { name: "Resource Library" })).toHaveAttribute("aria-current", "page");
    expect(screen.queryByText("No editable pages were found.")).not.toBeInTheDocument();
  });

  it("can go back to the website pages tab", async () => {
    renderApp("/admin", <AdminContent />);
    await screen.findByText("No editable pages were found.");

    await userEvent.click(screen.getByRole("button", { name: "Recent changes" }));
    await screen.findByText("No changes have been recorded yet.");
    await userEvent.click(screen.getByRole("button", { name: "Website pages" }));

    expect(await screen.findByText("No editable pages were found.")).toBeInTheDocument();
  });
});
