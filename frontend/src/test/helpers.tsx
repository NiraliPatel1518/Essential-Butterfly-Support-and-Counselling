import { render } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { vi } from "vitest";
import type { ReactElement } from "react";

/**
 * Replaces the browser's fetch with a fake one.
 * Example: mockFetch(true, "token-123") -> backend answers 200 with "token-123"
 */
export function mockFetch(ok: boolean, body = "") {
  const fetchMock = vi.fn().mockResolvedValue({
    ok,
    status: ok ? 200 : 400,
    text: () => Promise.resolve(body),
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

/** Fake fetch that fails like when the backend is not running. */
export function mockFetchNetworkError() {
  const fetchMock = vi.fn().mockRejectedValue(new TypeError("Failed to fetch"));
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
  return fetchMock;
}

/**
 * Renders a page inside a router. Every other route shows a simple
 * "Landed on /path" text, so tests can check where the user was sent.
 */
export function renderPage(
  path: string,
  page: ReactElement,
  initialUrl: string = path
) {
  return render(
    <MemoryRouter initialEntries={[initialUrl]}>
      <Routes>
        <Route path={path} element={page} />
        <Route path="*" element={<LocationProbe />} />
      </Routes>
    </MemoryRouter>
  );
}

function LocationProbe() {
  const location = useLocation();
  return <p>Landed on {location.pathname}</p>;
}
