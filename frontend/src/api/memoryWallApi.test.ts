import { describe, it, expect, vi, beforeEach } from "vitest";
import {
  ApiError,
  deletePhoto,
  fetchAdminImage,
  fetchOverview,
  fetchWall,
  publicImageUrl,
  savePhotoDescription,
  saveSelection,
  uploadPhoto,
} from "./memoryWallApi";
import { FRIENDLY_SERVER_ERROR, SESSION_EXPIRED_MESSAGE } from "./contentApi";
import { mockFetch, mockFetchNetworkError, mockFetchSequence } from "../test/helpers";

function lastCall(fetchMock: ReturnType<typeof mockFetch>) {
  const [url, init] = fetchMock.mock.calls[fetchMock.mock.calls.length - 1];
  return { url: String(url), init: (init ?? {}) as RequestInit };
}

function headersOf(init: RequestInit) {
  return init.headers as Record<string, string>;
}

beforeEach(() => {
  localStorage.setItem("adminToken", "admin-token");
});

describe("publicImageUrl", () => {
  it("points at the public picture of one photo", () => {
    expect(publicImageUrl(12)).toMatch(/\/api\/memory-wall\/photos\/12\/image$/);
  });

  it("never points at the admin address", () => {
    expect(publicImageUrl(12)).not.toContain("/admin/");
  });
});

describe("fetchWall", () => {
  const wall = [
    {
      key: "2026-10",
      label: "October 2026",
      photos: [{ id: 1, altText: "Children at the park", uploadedAt: "2026-10-12T10:00:00", uploadedOn: "October 12, 2026" }],
    },
  ];

  it("returns the months without sending a sign-in token", async () => {
    const fetchMock = mockFetch(true, JSON.stringify(wall));

    await expect(fetchWall()).resolves.toEqual(wall);

    const { url, init } = lastCall(fetchMock);
    expect(url).toMatch(/\/api\/memory-wall$/);
    expect(init.headers).toBeUndefined();
  });

  it("throws a friendly error when the server fails", async () => {
    mockFetchSequence({ ok: false, status: 500, body: "boom" });

    await expect(fetchWall()).rejects.toMatchObject({ message: FRIENDLY_SERVER_ERROR, status: 500 });
  });

  it("throws a friendly error when the server cannot be reached", async () => {
    mockFetchNetworkError();

    await expect(fetchWall()).rejects.toMatchObject({ message: FRIENDLY_SERVER_ERROR, status: 0 });
  });
});

describe("fetchOverview", () => {
  it("reads the admin overview with the admin token", async () => {
    const overview = { photos: [], summary: { totalPhotos: 0 } };
    const fetchMock = mockFetch(true, JSON.stringify(overview));

    await expect(fetchOverview()).resolves.toEqual(overview);

    const { url, init } = lastCall(fetchMock);
    expect(url).toMatch(/\/api\/admin\/memory-wall$/);
    expect(headersOf(init).Authorization).toBe("Bearer admin-token");
  });

  it("says the session expired on a 401", async () => {
    mockFetchSequence({ ok: false, status: 401, body: "" });

    await expect(fetchOverview()).rejects.toMatchObject({ message: SESSION_EXPIRED_MESSAGE, status: 401 });
  });
});

describe("fetchAdminImage", () => {
  it("fetches the picture with the token and returns a temporary address", async () => {
    const blob = new Blob(["x"], { type: "image/png" });
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 200, blob: () => Promise.resolve(blob) });
    vi.stubGlobal("fetch", fetchMock);
    const createObjectURL = vi.fn().mockReturnValue("blob:photo-1");
    vi.stubGlobal("URL", Object.assign(URL, { createObjectURL }));

    await expect(fetchAdminImage(5)).resolves.toBe("blob:photo-1");

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toMatch(/\/api\/admin\/memory-wall\/photos\/5\/image$/);
    expect(headersOf(init as RequestInit).Authorization).toBe("Bearer admin-token");
    expect(createObjectURL).toHaveBeenCalledWith(blob);
  });

  it("throws the server message when the picture cannot be read", async () => {
    mockFetchSequence({ ok: false, status: 404, body: { message: "Photo not found." } });

    await expect(fetchAdminImage(5)).rejects.toMatchObject({ message: "Photo not found.", status: 404 });
  });
});

describe("uploadPhoto", () => {
  it("posts the file as form data with the admin token", async () => {
    const fetchMock = mockFetch(true, JSON.stringify({ id: 9 }));
    const file = new File(["abc"], "party.png", { type: "image/png" });

    await expect(uploadPhoto(file)).resolves.toEqual({ id: 9 });

    const { url, init } = lastCall(fetchMock);
    expect(url).toMatch(/\/api\/admin\/memory-wall\/photos$/);
    expect(init.method).toBe("POST");
    expect(headersOf(init).Authorization).toBe("Bearer admin-token");
    // The browser sets the form boundary itself, so no Content-Type is given.
    expect(headersOf(init)["Content-Type"]).toBeUndefined();
    expect(init.body).toBeInstanceOf(FormData);
    expect((init.body as FormData).get("file")).toBe(file);
  });

  it("shows the server message for an unsafe or oversized file", async () => {
    mockFetchSequence({ ok: false, status: 400, body: { message: "That file is not a real picture." } });

    const error = await uploadPhoto(new File(["x"], "bad.png")).catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.message).toBe("That file is not a real picture.");
    expect(error.status).toBe(400);
  });

  it("hides a server crash behind the friendly message", async () => {
    mockFetchSequence({ ok: false, status: 500, body: { message: "IOException" } });

    await expect(uploadPhoto(new File(["x"], "a.png"))).rejects.toMatchObject({ message: FRIENDLY_SERVER_ERROR });
  });
});

describe("savePhotoDescription", () => {
  it("sends the description with PUT", async () => {
    const fetchMock = mockFetch(true, JSON.stringify({ id: 4 }));

    await savePhotoDescription(4, "Kids at the park");

    const { url, init } = lastCall(fetchMock);
    expect(url).toMatch(/\/api\/admin\/memory-wall\/photos\/4$/);
    expect(init.method).toBe("PUT");
    expect(headersOf(init)["Content-Type"]).toBe("application/json");
    expect(JSON.parse(String(init.body))).toEqual({ description: "Kids at the park" });
  });

  it("shows the server message when the description is too long", async () => {
    mockFetchSequence({ ok: false, status: 400, body: { message: "Please keep this under 200 characters." } });

    await expect(savePhotoDescription(4, "x")).rejects.toMatchObject({
      message: "Please keep this under 200 characters.",
    });
  });
});

describe("saveSelection", () => {
  it("sends the chosen photo ids with PUT", async () => {
    const overview = { photos: [], summary: {} };
    const fetchMock = mockFetch(true, JSON.stringify(overview));

    await expect(saveSelection([3, 1, 2])).resolves.toEqual(overview);

    const { url, init } = lastCall(fetchMock);
    expect(url).toMatch(/\/api\/admin\/memory-wall\/selection$/);
    expect(init.method).toBe("PUT");
    expect(JSON.parse(String(init.body))).toEqual({ photoIds: [3, 1, 2] });
  });

  it("can clear the wall with an empty list", async () => {
    const fetchMock = mockFetch(true, JSON.stringify({ photos: [], summary: {} }));

    await saveSelection([]);

    expect(JSON.parse(String(lastCall(fetchMock).init.body))).toEqual({ photoIds: [] });
  });

  it("shows the server message when a photo cannot be published", async () => {
    mockFetchSequence({ ok: false, status: 409, body: { message: "The wall can show at most 120 photos." } });

    await expect(saveSelection([1])).rejects.toMatchObject({
      message: "The wall can show at most 120 photos.",
      status: 409,
    });
  });
});

describe("deletePhoto", () => {
  it("sends DELETE and returns nothing", async () => {
    const fetchMock = mockFetchSequence({ ok: true, status: 204, body: "" });

    await expect(deletePhoto(8)).resolves.toBeUndefined();

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toMatch(/\/api\/admin\/memory-wall\/photos\/8$/);
    expect((init as RequestInit).method).toBe("DELETE");
    expect(headersOf(init as RequestInit).Authorization).toBe("Bearer admin-token");
  });

  it("throws when the server refuses", async () => {
    mockFetchSequence({ ok: false, status: 404, body: { message: "Photo not found." } });

    await expect(deletePhoto(8)).rejects.toMatchObject({ message: "Photo not found.", status: 404 });
  });
});