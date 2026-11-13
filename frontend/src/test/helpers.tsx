
import { render } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { vi } from "vitest";
import type { ReactElement } from "react";

type MockResponse = {
  ok: boolean;
  status?: number;
  body?: unknown;
};

type FetchFunction = (
  input: RequestInfo | URL,
  init?: RequestInit
) => Promise<Response>;

function createResponse({
  ok,
  status = ok ? 200 : 400,
  body = "",
}: MockResponse): Response {
  const textBody =
    typeof body === "string" ? body : JSON.stringify(body);

  return {
    ok,
    status,
    text: () => Promise.resolve(textBody),
    json: () =>
      Promise.resolve(
        typeof body === "string" ? JSON.parse(body || '""') : body
      ),
  } as Response;
}

/**
 * Replaces fetch with one repeated fake response.
 * Maintains compatibility with existing tests.
 */
export function mockFetch(ok: boolean, body = "") {
  const fetchMock = vi
    .fn<FetchFunction>()
    .mockResolvedValue(createResponse({ ok, body }));

  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

/**
 * Replaces fetch with a sequence of responses.
 * Each fetch call receives the next configured response.
 */
export function mockFetchSequence(...responses: MockResponse[]) {
  const fetchMock = vi.fn<FetchFunction>();

  responses.forEach((response) => {
    fetchMock.mockResolvedValueOnce(createResponse(response));
  });

  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

/** Fake fetch that fails like when the backend is not running. */
export function mockFetchNetworkError() {
  const fetchMock = vi
    .fn<FetchFunction>()
    .mockRejectedValue(new TypeError("Failed to fetch"));

  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
  return fetchMock;
}

/**
 * Renders a page inside a router.
 * Every other route shows a simple "Landed on /path" text.
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
