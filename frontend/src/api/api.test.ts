import { afterEach, describe, expect, test, vi } from "vitest";

/*
 * api.ts decides where the website sends its requests.
 * The address is read once when the file loads, so each test loads a fresh copy.
 *
 * This file has 5 tests:
 *   1 - No setting -> the same address as the website is used (empty base)
 *   2 - A blank setting -> the same address as the website is used
 *   3 - A configured address is used
 *   4 - Slashes at the end of the address are removed
 *   5 - A path without a leading slash still works
 */

async function loadApi(baseUrl?: string) {
  vi.resetModules();
  vi.stubEnv("VITE_API_BASE_URL", baseUrl ?? "");
  return import("./api");
}

afterEach(() => {
  vi.unstubAllEnvs();
});

describe("api", () => {
  test("uses the same address as the website when no address is configured", async () => {
    const { API_BASE_URL, apiUrl } = await loadApi();

    expect(API_BASE_URL).toBe("");
    expect(apiUrl("/api/auth/login")).toBe("/api/auth/login");
  });

  test("uses the same address as the website when the address is only spaces", async () => {
    const { API_BASE_URL } = await loadApi("   ");

    expect(API_BASE_URL).toBe("");
  });

  test("uses the configured address", async () => {
    const { API_BASE_URL, apiUrl } = await loadApi("https://api.example.com");

    expect(API_BASE_URL).toBe("https://api.example.com");
    expect(apiUrl("/api/features")).toBe("https://api.example.com/api/features");
  });

  test("removes slashes at the end of the configured address", async () => {
    const { API_BASE_URL, apiUrl } = await loadApi("https://api.example.com///");

    expect(API_BASE_URL).toBe("https://api.example.com");
    expect(apiUrl("/api/features")).toBe("https://api.example.com/api/features");
  });

  test("adds the missing slash in front of a path", async () => {
    const { apiUrl } = await loadApi();

    expect(apiUrl("api/features")).toBe("/api/features");
  });
});