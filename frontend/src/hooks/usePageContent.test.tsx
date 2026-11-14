import { describe, it, expect, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { usePageContent } from "./usePageContent";
import { DEFAULT_SERVICES, PAGE_LAYOUTS } from "../content/pageLayouts";
import { mockFetch, mockFetchSequence } from "../test/helpers";

beforeEach(() => {
  sessionStorage.clear();
});

describe("usePageContent", () => {
  it("shows the page's original wording when nothing is published", async () => {
    const fetchMock = mockFetchSequence({ ok: true, status: 204, body: "" });

    const { result } = renderHook(() => usePageContent("privacy"));

    expect(result.current.text("title")).toBe("Privacy");
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    expect(result.current.text("title")).toBe("Privacy");
  });

  it("replaces a slot with the published wording once it arrives", async () => {
    mockFetch(
      true,
      JSON.stringify({
        pageKey: "privacy",
        title: "Privacy",
        body: JSON.stringify({ fields: { title: "Our privacy promise" } }),
        publishedAt: "2026-01-01T00:00:00",
      })
    );

    const { result } = renderHook(() => usePageContent("privacy"));

    await waitFor(() => expect(result.current.text("title")).toBe("Our privacy promise"));
    expect(result.current.text("p1")).toBe(PAGE_LAYOUTS.privacy.slots[1].default);
  });

  it("returns an empty string for an unknown slot", () => {
    mockFetchSequence({ ok: true, status: 204, body: "" });

    const { result } = renderHook(() => usePageContent("privacy"));

    expect(result.current.text("noSuchSlot")).toBe("");
  });

  it("gives the Services page its services, and other pages none", () => {
    mockFetchSequence({ ok: true, status: 204, body: "" }, { ok: true, status: 204, body: "" });

    const services = renderHook(() => usePageContent("services"));
    const about = renderHook(() => usePageContent("about"));

    expect(services.result.current.services).toEqual(DEFAULT_SERVICES);
    expect(about.result.current.services).toEqual([]);
  });

  it("shows unsaved preview wording without asking the server", () => {
    const fetchMock = mockFetch(true, "{}");

    const { result } = renderHook(() =>
      usePageContent("privacy", { fields: { title: "Draft title" } })
    );

    expect(result.current.text("title")).toBe("Draft title");
    expect(result.current.text("p1")).toBe("");
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
