import { describe, it, expect, beforeEach } from "vitest";
import {
  ApiError,
  FRIENDLY_SERVER_ERROR,
  SESSION_EXPIRED_MESSAGE,
  fetchAdminPages,
  fetchAdminResources,
  fetchAuditLog,
  fetchPublicResources,
  fetchPublishedContent,
  fetchUploadRules,
  removeResource,
  requestJson,
  resourceDownloadUrl,
  saveAdminPage,
  saveResource,
  send,
  toApiError,
  type ResourceFields,
} from "./contentApi";
import { mockFetch, mockFetchNetworkError, mockFetchSequence } from "../test/helpers";

const fields: ResourceFields = {
  title: "Coping guide",
  description: "A short guide",
  category: "Guides",
  altText: "",
  linkUrls: "https://example.org/a\nhttps://example.org/b",
  removeFile: false,
  publish: true,
};

function lastCall(fetchMock: ReturnType<typeof mockFetch>) {
  const [url, init] = fetchMock.mock.calls[fetchMock.mock.calls.length - 1];
  return { url: String(url), init: init as RequestInit };
}

beforeEach(() => {
  localStorage.setItem("adminToken", "admin-token");
});

describe("fetchPublishedContent", () => {
  const page = { pageKey: "about", title: "About", body: "{}", publishedAt: "2026-01-01T00:00:00" };

  it("returns the published page", async () => {
    const fetchMock = mockFetch(true, JSON.stringify(page));

    await expect(fetchPublishedContent("about")).resolves.toEqual(page);
    expect(lastCall(fetchMock).url).toMatch(/\/api\/content\/about$/);
  });

  it("returns null when nothing is published (204)", async () => {
    mockFetchSequence({ ok: true, status: 204, body: "" });

    await expect(fetchPublishedContent("about")).resolves.toBeNull();
  });

  it("returns null when the server answers with an error", async () => {
    mockFetch(false, "oops");

    await expect(fetchPublishedContent("about")).resolves.toBeNull();
  });

  it("returns null for a page without text", async () => {
    mockFetch(true, JSON.stringify({ pageKey: "about", title: 5 }));
    await expect(fetchPublishedContent("about")).resolves.toBeNull();

    mockFetch(true, "null");
    await expect(fetchPublishedContent("about")).resolves.toBeNull();
  });

  it("rejects with the friendly message when the server cannot be reached", async () => {
    mockFetchNetworkError();

    await expect(fetchPublishedContent("about")).rejects.toMatchObject({
      message: FRIENDLY_SERVER_ERROR,
      status: 0,
    });
  });
});

describe("toApiError", () => {
  function response(status: number, body: string) {
    return { status, ok: false, text: () => Promise.resolve(body) } as Response;
  }

  it("says the session has expired for 401", async () => {
    const error = await toApiError(response(401, ""));

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(401);
    expect(error.message).toBe(SESSION_EXPIRED_MESSAGE);
  });

  it("uses a friendly sentence for server problems", async () => {
    const error = await toApiError(response(503, '{"message":"Database down"}'));

    expect(error.status).toBe(503);
    expect(error.message).toBe(FRIENDLY_SERVER_ERROR);
  });

  it("shows the server's own explanation for a mistake the admin can fix", async () => {
    const error = await toApiError(response(400, '{"message":"The title is required."}'));

    expect(error.message).toBe("The title is required.");
    expect(error.status).toBe(400);
  });

  it("reads the error field when there is no message", async () => {
    const error = await toApiError(response(409, '{"error":"Already exists"}'));

    expect(error.message).toBe("Already exists");
  });

  it("falls back to the friendly sentence for empty or unreadable bodies", async () => {
    expect((await toApiError(response(400, ""))).message).toBe(FRIENDLY_SERVER_ERROR);
    expect((await toApiError(response(400, "<html>bad</html>"))).message).toBe(FRIENDLY_SERVER_ERROR);
    expect((await toApiError(response(404, "{}"))).message).toBe(FRIENDLY_SERVER_ERROR);
  });
});

describe("send and requestJson", () => {
  it("send turns a network failure into an ApiError", async () => {
    mockFetchNetworkError();

    await expect(send("/api/x")).rejects.toBeInstanceOf(ApiError);
  });

  it("requestJson returns the parsed body", async () => {
    mockFetch(true, JSON.stringify({ a: 1 }));

    await expect(requestJson<{ a: number }>("/api/x")).resolves.toEqual({ a: 1 });
  });

  it("requestJson throws the readable error for a failed request", async () => {
    mockFetch(false, '{"message":"Nope"}');

    await expect(requestJson("/api/x")).rejects.toMatchObject({ message: "Nope", status: 400 });
  });
});

describe("public resources", () => {
  it("loads the public list without a token", async () => {
    const fetchMock = mockFetch(true, "[]");

    await expect(fetchPublicResources()).resolves.toEqual([]);

    const { url, init } = lastCall(fetchMock);
    expect(url).toMatch(/\/api\/resources$/);
    expect(init).toBeUndefined();
  });

  it("builds the download address for a resource", () => {
    expect(resourceDownloadUrl(7)).toMatch(/\/api\/resources\/7\/download$/);
  });
});

describe("admin requests send the admin token", () => {
  it("loads the pages", async () => {
    const fetchMock = mockFetch(true, "[]");
    await fetchAdminPages();

    const { url, init } = lastCall(fetchMock);
    expect(url).toMatch(/\/api\/admin\/content$/);
    expect(JSON.stringify(init.headers)).toContain("Bearer admin-token");
  });

  it("loads the resources", async () => {
    const fetchMock = mockFetch(true, "[]");
    await fetchAdminResources();

    const { url, init } = lastCall(fetchMock);
    expect(url).toMatch(/\/api\/admin\/resources$/);
    expect(JSON.stringify(init.headers)).toContain("Bearer admin-token");
  });

  it("loads the upload rules", async () => {
    const fetchMock = mockFetch(true, JSON.stringify({ maxFileSizeBytes: 10 }));
    await expect(fetchUploadRules()).resolves.toEqual({ maxFileSizeBytes: 10 });

    expect(lastCall(fetchMock).url).toMatch(/\/api\/admin\/resources\/upload-rules$/);
  });

  it("loads the audit log with a limit, 50 by default", async () => {
    const fetchMock = mockFetch(true, "[]");

    await fetchAuditLog();
    expect(lastCall(fetchMock).url).toMatch(/\/api\/admin\/audit-log\?limit=50$/);

    await fetchAuditLog(5);
    expect(lastCall(fetchMock).url).toMatch(/\/api\/admin\/audit-log\?limit=5$/);
  });

  it("reports an expired sign-in as a 401 ApiError", async () => {
    mockFetchSequence({ ok: false, status: 401, body: "" });

    await expect(fetchAdminPages()).rejects.toMatchObject({
      status: 401,
      message: SESSION_EXPIRED_MESSAGE,
    });
  });
});

describe("saveAdminPage", () => {
  it("PUTs the page as JSON to its own address", async () => {
    const fetchMock = mockFetch(true, JSON.stringify({ pageKey: "about" }));

    await saveAdminPage("about", { title: "About", body: "{}", publish: false });

    const { url, init } = lastCall(fetchMock);
    expect(url).toMatch(/\/api\/admin\/content\/about$/);
    expect(init.method).toBe("PUT");
    expect(JSON.parse(String(init.body))).toEqual({ title: "About", body: "{}", publish: false });
    expect(JSON.stringify(init.headers)).toContain("application/json");
    expect(JSON.stringify(init.headers)).toContain("Bearer admin-token");
  });

  it("shows the server's explanation when the page is refused", async () => {
    mockFetchSequence({ ok: false, status: 400, body: '{"message":"A title is required."}' });

    await expect(
      saveAdminPage("about", { title: "", body: "{}", publish: true })
    ).rejects.toMatchObject({ message: "A title is required." });
  });
});

describe("saveResource", () => {
  it("POSTs a new resource as multipart form data", async () => {
    const fetchMock = mockFetch(true, JSON.stringify({ id: 1 }));

    await saveResource(null, fields, null);

    const { url, init } = lastCall(fetchMock);
    expect(url).toMatch(/\/api\/admin\/resources$/);
    expect(init.method).toBe("POST");

    const form = init.body as FormData;
    expect(form.get("title")).toBe("Coping guide");
    expect(form.get("description")).toBe("A short guide");
    expect(form.get("category")).toBe("Guides");
    expect(form.get("linkUrls")).toBe("https://example.org/a\nhttps://example.org/b");
    expect(form.get("publish")).toBe("true");
    expect(form.get("removeFile")).toBe("false");
    expect(form.has("file")).toBe(false);
  });

  it("lets the browser set the content type", async () => {
    const fetchMock = mockFetch(true, "{}");

    await saveResource(null, fields, null);

    const headers = lastCall(fetchMock).init.headers as Record<string, string>;
    expect(headers.Authorization).toBe("Bearer admin-token");
    expect(Object.keys(headers).map((key) => key.toLowerCase())).not.toContain("content-type");
  });

  it("PUTs an existing resource to its own address and attaches the file", async () => {
    const fetchMock = mockFetch(true, "{}");
    const file = new File(["%PDF-1.4"], "guide.pdf", { type: "application/pdf" });

    await saveResource(12, { ...fields, publish: false, removeFile: true }, file);

    const { url, init } = lastCall(fetchMock);
    expect(url).toMatch(/\/api\/admin\/resources\/12$/);
    expect(init.method).toBe("PUT");

    const form = init.body as FormData;
    expect((form.get("file") as File).name).toBe("guide.pdf");
    expect(form.get("publish")).toBe("false");
    expect(form.get("removeFile")).toBe("true");
  });

  it("shows the server's explanation for a duplicate", async () => {
    mockFetchSequence({ ok: false, status: 409, body: '{"message":"That title is already used."}' });

    await expect(saveResource(null, fields, null)).rejects.toMatchObject({
      message: "That title is already used.",
      status: 409,
    });
  });
});

describe("removeResource", () => {
  it("sends a DELETE with the admin token", async () => {
    const fetchMock = mockFetchSequence({ ok: true, status: 204, body: "" });

    await expect(removeResource(4)).resolves.toBeUndefined();

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toMatch(/\/api\/admin\/resources\/4$/);
    expect((init as RequestInit).method).toBe("DELETE");
    expect(JSON.stringify((init as RequestInit).headers)).toContain("Bearer admin-token");
  });

  it("throws a readable error when the removal fails", async () => {
    mockFetchSequence({ ok: false, status: 404, body: '{"message":"Resource not found."}' });

    await expect(removeResource(4)).rejects.toMatchObject({ message: "Resource not found." });
  });
});