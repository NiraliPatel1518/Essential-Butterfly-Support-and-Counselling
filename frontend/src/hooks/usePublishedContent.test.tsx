import { describe, it, expect, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { usePublishedContent } from "./usePublishedContent";
import { mockFetch, mockFetchNetworkError, mockFetchSequence } from "../test/helpers";

const CACHE_KEY = "essential-butterfly:published:about";

const page = {
  pageKey: "about",
  title: "About us",
  body: '{"fields":{}}',
  publishedAt: "2026-01-01T00:00:00",
};

beforeEach(() => {
  sessionStorage.clear();
});

describe("usePublishedContent", () => {
  it("starts with nothing, then shows the published page", async () => {
    mockFetch(true, JSON.stringify(page));

    const { result } = renderHook(() => usePublishedContent("about"));

    expect(result.current).toBeNull();
    await waitFor(() => expect(result.current).toEqual(page));
  });

  it("remembers the published page for the rest of the visit", async () => {
    mockFetch(true, JSON.stringify(page));

    const { result } = renderHook(() => usePublishedContent("about"));
    await waitFor(() => expect(result.current).not.toBeNull());

    expect(JSON.parse(sessionStorage.getItem(CACHE_KEY)!)).toEqual(page);
  });

  it("shows the remembered page at once, before the server answers", async () => {
    sessionStorage.setItem(CACHE_KEY, JSON.stringify(page));
    mockFetch(true, JSON.stringify({ ...page, title: "Newer title" }));

    const { result } = renderHook(() => usePublishedContent("about"));

    expect(result.current?.title).toBe("About us");
    await waitFor(() => expect(result.current?.title).toBe("Newer title"));
  });

  it("goes back to the built-in page when the page is unpublished (204)", async () => {
    sessionStorage.setItem(CACHE_KEY, JSON.stringify(page));
    mockFetchSequence({ ok: true, status: 204, body: "" });

    const { result } = renderHook(() => usePublishedContent("about"));
    expect(result.current).not.toBeNull();

    await waitFor(() => expect(result.current).toBeNull());
    expect(sessionStorage.getItem(CACHE_KEY)).toBeNull();
  });

  it("keeps what is on screen when the server cannot be reached", async () => {
    sessionStorage.setItem(CACHE_KEY, JSON.stringify(page));
    const fetchMock = mockFetchNetworkError();

    const { result } = renderHook(() => usePublishedContent("about"));
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());

    expect(result.current).toEqual(page);
  });

  it("ignores a damaged remembered page", async () => {
    sessionStorage.setItem(CACHE_KEY, "{not json");
    mockFetchSequence({ ok: true, status: 204, body: "" });

    const { result } = renderHook(() => usePublishedContent("about"));

    expect(result.current).toBeNull();
  });

  it("does nothing without a page key (editor preview)", async () => {
    const fetchMock = mockFetch(true, JSON.stringify(page));

    const { result } = renderHook(() => usePublishedContent(null));

    expect(result.current).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
