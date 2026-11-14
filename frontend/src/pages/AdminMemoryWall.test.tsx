import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AdminMemoryWall from "./AdminMemoryWall";
import { renderPage } from "../test/helpers";
import { jsonResponse } from "../test/render";
import type { AdminOverview, AdminPhoto, WallSummary } from "../api/memoryWallApi";

function photo(overrides: Partial<AdminPhoto> = {}): AdminPhoto {
  return {
    id: 1,
    status: "PENDING_REVIEW",
    source: "ADMIN_UPLOAD",
    uploadedBy: "Casey",
    uploadedAt: "2026-10-12T10:00:00",
    publishedAt: null,
    fileName: "park.png",
    contentType: "image/png",
    sizeBytes: 300 * 1024,
    description: null,
    altText: "Children at the park",
    testimonialStatus: null,
    canPublish: true,
    publishBlockedReason: null,
    ...overrides,
  };
}

function summary(overrides: Partial<WallSummary> = {}): WallSummary {
  return {
    totalPhotos: 3,
    maxPhotos: 150,
    remainingUploads: 147,
    publishedPhotos: 1,
    maxPublishedPhotos: 120,
    storageUsedBytes: 2 * 1024 * 1024,
    maxFileSizeBytes: 5 * 1024 * 1024,
    maxFileSizeLabel: "5 MB",
    allowedExtensions: ["jpg", "jpeg", "png", "webp"],
    acceptedFormats: "JPEG, PNG or WEBP",
    ...overrides,
  };
}

// Newest first, like the server sends them.
const NEWEST = photo({ id: 1, status: "PUBLISHED", altText: "A birthday cake", uploadedAt: "2026-10-12T10:00:00", description: "A birthday cake" });
const MIDDLE = photo({ id: 2, status: "PENDING_REVIEW", altText: "Children at the park", uploadedAt: "2026-10-05T10:00:00" });
const OLDEST = photo({ id: 3, status: "UNPUBLISHED", altText: "A butterfly", uploadedAt: "2026-09-20T10:00:00" });

type State = {
  overview: AdminOverview;
  // Replies that override the normal ones, keyed "METHOD path-part".
  fail: Record<string, () => Promise<unknown>>;
};

function state(photos: AdminPhoto[] = [NEWEST, MIDDLE, OLDEST], extra: Partial<WallSummary> = {}): State {
  return { overview: { photos, summary: summary({ totalPhotos: photos.length, ...extra }) }, fail: {} };
}

const blobReply = () =>
  Promise.resolve({ ok: true, status: 200, blob: () => Promise.resolve(new Blob(["x"], { type: "image/png" })) });

function stubApi(world: State) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    const path = String(url);
    const method = init?.method ?? "GET";

    for (const [key, reply] of Object.entries(world.fail)) {
      const [m, part] = key.split(" ");
      if (m === method && path.includes(part)) return reply();
    }

    if (path.includes("/image")) return blobReply();

    if (path.endsWith("/selection") && method === "PUT") {
      const ids: number[] = JSON.parse(String(init?.body)).photoIds;
      const photos = world.overview.photos.map((p) => ({
        ...p,
        status: ids.includes(p.id) ? ("PUBLISHED" as const) : p.status === "PUBLISHED" ? ("UNPUBLISHED" as const) : p.status,
      }));
      world.overview = { photos, summary: { ...world.overview.summary, publishedPhotos: ids.length } };
      return jsonResponse(world.overview);
    }

    if (method === "POST") return jsonResponse(photo({ id: 99 }), true, 201);
    if (method === "PUT") return jsonResponse(photo({ id: 1 }));
    if (method === "DELETE") {
      return Promise.resolve({ ok: true, status: 204, json: () => Promise.resolve(null), text: () => Promise.resolve("") });
    }

    return jsonResponse(world.overview);
  });

  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function callsTo(fetchMock: ReturnType<typeof stubApi>, method: string, part: string) {
  return fetchMock.mock.calls.filter(
    ([url, init]) => String(url).includes(part) && (init?.method ?? "GET") === method
  );
}

async function show(world: State = state()) {
  const fetchMock = stubApi(world);
  renderPage("/admin-memory-wall", <AdminMemoryWall />);
  await waitFor(() => expect(screen.queryByText("Refreshing…")).not.toBeInTheDocument());
  return fetchMock;
}

const cardOf = (altText: string) =>
  screen.getByRole("button", { name: `Preview and enlarge photo: ${altText}` }).closest("li")!;

const checkboxOf = (altText: string) =>
  within(cardOf(altText)).getByRole("checkbox") as HTMLInputElement;

beforeEach(() => {
  localStorage.setItem("adminToken", "admin-token");

  URL.createObjectURL = vi.fn(() => "blob:photo");
  URL.revokeObjectURL = vi.fn();

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
  localStorage.clear();
});

describe("AdminMemoryWall: the page", () => {
  it("shows the heading and a link back to the admin dashboard", async () => {
    await show();

    expect(screen.getByRole("heading", { name: "Manage Memory Wall" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /back to the admin dashboard/i })).toHaveAttribute(
      "href",
      "/admin-dashboard"
    );
  });

  it("asks for the overview with the admin token", async () => {
    const fetchMock = await show();

    const call = fetchMock.mock.calls.find(([url]) => String(url).endsWith("/api/admin/memory-wall"))!;
    expect((call[1]?.headers as Record<string, string>).Authorization).toBe("Bearer admin-token");
  });

  it("shows the photo and space numbers", async () => {
    await show();

    const stats = screen.getByRole("heading", { name: "Photos and space" }).closest("section")!;

    expect(within(stats).getByText("Photos uploaded").nextSibling).toHaveTextContent("3 of 150");
    expect(within(stats).getByText("Space left for new photos").nextSibling).toHaveTextContent("147");
    expect(within(stats).getByText("Ticked to show on the website").nextSibling).toHaveTextContent("1 of 120");
    expect(within(stats).getByText("Showing on the website now").nextSibling).toHaveTextContent("1");
    expect(within(stats).getByText("Storage used").nextSibling).toHaveTextContent("2.0 MB");
  });

  it("shows small storage in KB", async () => {
    await show(state([NEWEST], { storageUsedBytes: 300 * 1024 }));

    expect(screen.getByText("Storage used").nextSibling).toHaveTextContent("300 KB");
  });

  it("explains the accepted formats and the size limit", async () => {
    await show();

    expect(screen.getByText(/JPEG, PNG or WEBP photos, up to 5 MB each\./)).toBeInTheDocument();
  });

  it("shows the problem when loading fails", async () => {
    const world = state();
    world.fail["GET /api/admin/memory-wall"] = () => jsonResponse({ message: "The wall is unavailable." }, false, 409);
    await show(world);

    expect(await screen.findByRole("alert")).toHaveTextContent("The wall is unavailable.");
  });

  it("sends the administrator to the login page when the session expired", async () => {
    const world = state();
    world.fail["GET /api/admin/memory-wall"] = () => jsonResponse("", false, 401);
    stubApi(world);
    renderPage("/admin-memory-wall", <AdminMemoryWall />);

    expect(await screen.findByText("Landed on /login")).toBeInTheDocument();
    expect(localStorage.getItem("adminToken")).toBeNull();
  });

  it("Refresh loads the overview again", async () => {
    const fetchMock = await show();

    await userEvent.click(screen.getByRole("button", { name: "Refresh" }));

    await waitFor(() =>
      expect(fetchMock.mock.calls.filter(([url]) => String(url).endsWith("/api/admin/memory-wall"))).toHaveLength(2)
    );
  });
});

describe("AdminMemoryWall: the photo list", () => {
  it("lists every photo, newest first", async () => {
    await show();

    const names = screen.getAllByRole("button", { name: /Preview and enlarge photo/ }).map((b) => b.getAttribute("aria-label"));

    expect(names).toEqual([
      "Preview and enlarge photo: A birthday cake",
      "Preview and enlarge photo: Children at the park",
      "Preview and enlarge photo: A butterfly",
    ]);
  });

  it("loads each picture with the token and shows it", async () => {
    await show();

    await waitFor(() => expect(screen.getByAltText("A birthday cake")).toHaveAttribute("src", "blob:photo"));
  });

  it("shows a message when a picture cannot be loaded", async () => {
    const world = state([NEWEST]);
    world.fail["GET /image"] = () => jsonResponse({ message: "Gone" }, false, 404);
    await show(world);

    expect(await screen.findByText("Photo could not be loaded")).toBeInTheDocument();
  });

  it("shows a status word for each photo", async () => {
    await show();

    expect(within(cardOf("A birthday cake")).getByText("Published")).toBeInTheDocument();
    expect(within(cardOf("Children at the park")).getByText("Waiting for review")).toBeInTheDocument();
    expect(within(cardOf("A butterfly")).getByText("Unpublished")).toBeInTheDocument();
  });

  it("shows who uploaded each photo", async () => {
    await show();

    expect(within(cardOf("A birthday cake")).getByText(/By Casey/)).toBeInTheDocument();
  });

  it("ticks the published photos at the start", async () => {
    await show();

    expect(checkboxOf("A birthday cake").checked).toBe(true);
    expect(checkboxOf("Children at the park").checked).toBe(false);
    expect(checkboxOf("A butterfly").checked).toBe(false);
  });

  it("filters by status", async () => {
    await show();

    await userEvent.selectOptions(screen.getByLabelText("Show"), "PENDING_REVIEW");

    expect(screen.getAllByRole("button", { name: /Preview and enlarge photo/ })).toHaveLength(1);
    expect(screen.getByRole("button", { name: /Children at the park/ })).toBeInTheDocument();
  });

  it("says no photos match a filter", async () => {
    await show(state([NEWEST]));

    await userEvent.selectOptions(screen.getByLabelText("Show"), "UNPUBLISHED");

    expect(screen.getByText("No photos match this filter.")).toBeInTheDocument();
  });

  it("reverses the order to oldest first", async () => {
    await show();

    await userEvent.selectOptions(screen.getByLabelText("Order"), "old");

    const names = screen.getAllByRole("button", { name: /Preview and enlarge photo/ }).map((b) => b.getAttribute("aria-label"));
    expect(names[0]).toBe("Preview and enlarge photo: A butterfly");
    expect(names[2]).toBe("Preview and enlarge photo: A birthday cake");
  });

  it("invites the first upload when there are no photos", async () => {
    await show(state([]));

    expect(screen.getByText("No photos yet. Choose Add Photos to upload the first one.")).toBeInTheDocument();
  });

  it("keeps a photo that cannot be published unticked and explains why", async () => {
    const blocked = photo({
      id: 4,
      altText: "Testimonial photo",
      canPublish: false,
      publishBlockedReason: "The testimonial is not published yet.",
    });
    await show(state([blocked]));

    expect(checkboxOf("Testimonial photo")).toBeDisabled();
    expect(screen.getByText("The testimonial is not published yet.")).toBeInTheDocument();
  });
});

describe("AdminMemoryWall: choosing what is public", () => {
  it("shows no save bar until something changes", async () => {
    await show();

    expect(screen.queryByRole("region", { name: "Unsaved changes" })).not.toBeInTheDocument();
  });

  it("ticking a photo shows the save bar and says it will be added", async () => {
    await show();

    await userEvent.click(checkboxOf("Children at the park"));

    const bar = screen.getByRole("region", { name: "Unsaved changes" });
    expect(bar).toHaveTextContent("1 to add, 0 to remove.");
    expect(within(cardOf("Children at the park")).getByText("Will be added when you save.")).toBeInTheDocument();
  });

  it("unticking a published photo says it will be removed", async () => {
    await show();

    await userEvent.click(checkboxOf("A birthday cake"));

    expect(screen.getByRole("region", { name: "Unsaved changes" })).toHaveTextContent("0 to add, 1 to remove.");
    expect(within(cardOf("A birthday cake")).getByText("Will be removed when you save.")).toBeInTheDocument();
  });

  it("Discard changes puts every tick back", async () => {
    await show();

    await userEvent.click(checkboxOf("Children at the park"));
    await userEvent.click(checkboxOf("A birthday cake"));
    await userEvent.click(screen.getByRole("button", { name: "Discard changes" }));

    expect(checkboxOf("Children at the park").checked).toBe(false);
    expect(checkboxOf("A birthday cake").checked).toBe(true);
    expect(screen.queryByRole("region", { name: "Unsaved changes" })).not.toBeInTheDocument();
  });

  it("saves an addition straight away and sends every ticked id", async () => {
    const fetchMock = await show();

    await userEvent.click(checkboxOf("Children at the park"));
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));

    expect(
      await screen.findByText("Changes saved. The public Memory Wall now shows exactly the photos you ticked.")
    ).toBeInTheDocument();

    const [url, init] = callsTo(fetchMock, "PUT", "/selection")[0];
    expect(String(url)).toMatch(/\/api\/admin\/memory-wall\/selection$/);
    expect(JSON.parse(String(init?.body)).photoIds.sort()).toEqual([1, 2]);
    expect(screen.queryByRole("region", { name: "Unsaved changes" })).not.toBeInTheDocument();
  });

  it("asks before taking a photo off the public wall", async () => {
    const fetchMock = await show();

    await userEvent.click(checkboxOf("A birthday cake"));
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));

    const dialog = await screen.findByRole("dialog", { name: "Remove photos from the website?" });

    expect(dialog).toHaveTextContent("Saving will take 1 photo off the public Memory Wall.");
    expect(callsTo(fetchMock, "PUT", "/selection")).toHaveLength(0);

    await userEvent.click(within(dialog).getByRole("button", { name: "Yes, save changes" }));

    await screen.findByText(/Changes saved/);
    expect(JSON.parse(String(callsTo(fetchMock, "PUT", "/selection")[0][1]?.body))).toEqual({ photoIds: [] });
  });

  it("mentions the photos being added in the same confirmation", async () => {
    await show();

    await userEvent.click(checkboxOf("A birthday cake"));
    await userEvent.click(checkboxOf("Children at the park"));
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));

    const dialog = await screen.findByRole("dialog", { name: "Remove photos from the website?" });

    expect(dialog).toHaveTextContent("and add 1 new photo");
  });

  it("keeps everything when the confirmation is closed", async () => {
    const fetchMock = await show();

    await userEvent.click(checkboxOf("A birthday cake"));
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));
    const dialog = await screen.findByRole("dialog", { name: "Remove photos from the website?" });
    await userEvent.click(within(dialog).getByRole("button", { name: "Close" }));

    expect(callsTo(fetchMock, "PUT", "/selection")).toHaveLength(0);
    expect(screen.getByRole("region", { name: "Unsaved changes" })).toBeInTheDocument();
  });

  it("shows the server message when saving fails and keeps the ticks", async () => {
    const world = state();
    world.fail["PUT /selection"] = () => jsonResponse({ message: "The wall can show at most 120 photos." }, false, 409);
    await show(world);

    await userEvent.click(checkboxOf("Children at the park"));
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("The wall can show at most 120 photos.");
    expect(checkboxOf("Children at the park").checked).toBe(true);
  });

  it("stops more ticks at the published limit and warns", async () => {
    const photos = [
      photo({ id: 1, status: "PUBLISHED", altText: "One" }),
      photo({ id: 2, status: "PENDING_REVIEW", altText: "Two" }),
    ];
    await show(state(photos, { maxPublishedPhotos: 1, publishedPhotos: 1 }));

    expect(screen.getByRole("status")).toHaveTextContent("You have ticked the maximum of 1 photos.");
    expect(checkboxOf("Two")).toBeDisabled();
    expect(checkboxOf("One")).toBeEnabled();
  });
});

describe("AdminMemoryWall: uploading", () => {
  const png = (name: string) => new File(["abc"], name, { type: "image/png" });

  it("uploads each file separately and says new photos are not public yet", async () => {
    const fetchMock = await show();

    await userEvent.upload(screen.getByLabelText("Add Photos"), [png("a.png"), png("b.png")]);

    expect(
      await screen.findByText(
        "2 photos added. New photos are not public until you tick them and choose Save changes."
      )
    ).toBeInTheDocument();
    expect(callsTo(fetchMock, "POST", "/photos")).toHaveLength(2);
  });

  it("says 1 photo for a single file", async () => {
    await show();

    await userEvent.upload(screen.getByLabelText("Add Photos"), png("a.png"));

    expect(await screen.findByText(/^1 photo added\./)).toBeInTheDocument();
  });

  it("reloads the list after uploading", async () => {
    const fetchMock = await show();

    await userEvent.upload(screen.getByLabelText("Add Photos"), png("a.png"));
    await screen.findByText(/photo added/);

    expect(fetchMock.mock.calls.filter(([url, init]) => String(url).endsWith("/api/admin/memory-wall") && !init?.method)).toHaveLength(2);
  });

  it("keeps the ticks that were made before uploading", async () => {
    await show();

    await userEvent.click(checkboxOf("Children at the park"));
    await userEvent.upload(screen.getByLabelText("Add Photos"), png("a.png"));
    await screen.findByText(/photo added/);

    expect(checkboxOf("Children at the park").checked).toBe(true);
  });

  it("names each file that was refused and why", async () => {
    const world = state();
    world.fail["POST /photos"] = () => jsonResponse({ message: "That file is not a real picture." }, false, 400);
    await show(world);

    await userEvent.upload(screen.getByLabelText("Add Photos"), png("fake.png"));

    const alert = await screen.findByText("Some photos were not added:");

    expect(alert.closest("[role=alert]")).toHaveTextContent("fake.png: That file is not a real picture.");
    expect(screen.queryByText(/photo added/)).not.toBeInTheDocument();
  });

  it("refuses to start when more files are chosen than there is space for", async () => {
    const fetchMock = await show(state([NEWEST], { remainingUploads: 1 }));

    await userEvent.upload(screen.getByLabelText("Add Photos"), [png("a.png"), png("b.png")]);

    expect(
      await screen.findByText(/You can add 1 more photo \(the limit is 150\), but you chose 2\. Choose fewer photos\./)
    ).toBeInTheDocument();
    expect(callsTo(fetchMock, "POST", "/photos")).toHaveLength(0);
  });

  it("turns uploading off and explains when the wall is full", async () => {
    await show(state([NEWEST], { remainingUploads: 0, totalPhotos: 150 }));

    expect(screen.getByLabelText("Add Photos")).toBeDisabled();
    expect(screen.getByText(/The wall is full: delete a photo below to add another\./)).toBeInTheDocument();
  });

  it("only offers the allowed picture types", async () => {
    await show();

    expect(screen.getByLabelText("Add Photos")).toHaveAttribute("accept", ".jpg,.jpeg,.png,.webp");
    expect(screen.getByLabelText("Add Photos")).toHaveAttribute("multiple");
  });

  it("sends the administrator to the login page when the session expires while uploading", async () => {
    const world = state();
    world.fail["POST /photos"] = () => jsonResponse("", false, 401);
    await show(world);

    await userEvent.upload(screen.getByLabelText("Add Photos"), png("a.png"));

    expect(await screen.findByText("Landed on /login")).toBeInTheDocument();
  });
});

describe("AdminMemoryWall: preview and description", () => {
  async function openPreview(altText: string) {
    await userEvent.click(screen.getByRole("button", { name: `Preview and enlarge photo: ${altText}` }));
    return screen.findByRole("dialog", { name: "Photo preview" });
  }

  it("opens a preview with the status, uploader and size", async () => {
    await show();

    const dialog = await openPreview("A birthday cake");

    expect(dialog).toHaveTextContent("Published");
    expect(dialog).toHaveTextContent("by Casey");
    expect(dialog).toHaveTextContent("300 KB");
  });

  it("shows the saved description to edit", async () => {
    await show();

    const dialog = await openPreview("A birthday cake");

    expect(within(dialog).getByLabelText(/Description \(read aloud by screen readers\)/)).toHaveValue("A birthday cake");
    expect(within(dialog).getByLabelText(/Description/)).toHaveAttribute("maxlength", "200");
  });

  it("saves a new description with PUT", async () => {
    const fetchMock = await show();

    const dialog = await openPreview("Children at the park");
    await userEvent.type(within(dialog).getByLabelText(/Description/), "Kids on the swings");
    await userEvent.click(within(dialog).getByRole("button", { name: "Save description" }));

    expect(await screen.findByText("Description saved.")).toBeInTheDocument();

    const [url, init] = callsTo(fetchMock, "PUT", "/photos/2")[0];
    expect(String(url)).toMatch(/\/api\/admin\/memory-wall\/photos\/2$/);
    expect(JSON.parse(String(init?.body))).toEqual({ description: "Kids on the swings" });
  });

  it("shows the server message when the description is refused", async () => {
    const world = state();
    world.fail["PUT /photos/2"] = () => jsonResponse({ message: "Please keep this under 200 characters." }, false, 400);
    await show(world);

    const dialog = await openPreview("Children at the park");
    await userEvent.type(within(dialog).getByLabelText(/Description/), "x");
    await userEvent.click(within(dialog).getByRole("button", { name: "Save description" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent("Please keep this under 200 characters.");
    expect(within(dialog).getByLabelText(/Description/)).toHaveAttribute("aria-invalid", "true");
  });

  it("moves to the next and previous photo and wraps around", async () => {
    await show();

    const dialog = await openPreview("A birthday cake");

    await userEvent.click(within(dialog).getByRole("button", { name: /Next/ }));
    expect(await within(dialog).findByRole("img", { name: "Children at the park" })).toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole("button", { name: /Previous/ }));
    await userEvent.click(within(dialog).getByRole("button", { name: /Previous/ }));
    expect(await within(dialog).findByRole("img", { name: "A butterfly" })).toBeInTheDocument();
  });

  it("loads the description of the photo that comes next", async () => {
    await show();

    const dialog = await openPreview("A birthday cake");
    await userEvent.click(within(dialog).getByRole("button", { name: /Next/ }));

    expect(within(dialog).getByLabelText(/Description/)).toHaveValue("");
  });

  it("the arrow keys move between photos, but not inside the description box", async () => {
    await show();

    const dialog = await openPreview("A birthday cake");

    fireEvent.keyDown(dialog, { key: "ArrowRight" });
    expect(await within(dialog).findByRole("img", { name: "Children at the park" })).toBeInTheDocument();

    fireEvent.keyDown(within(dialog).getByLabelText(/Description/), { key: "ArrowRight" });
    // Still on the same photo: the arrow only moved the text cursor.
    await waitFor(() =>
      expect(within(dialog).getByRole("img", { name: "Children at the park" })).toBeInTheDocument()
    );
  });

  it("closes and gives focus back to the photo that opened it", async () => {
    await show();

    const opener = screen.getByRole("button", { name: "Preview and enlarge photo: A birthday cake" });
    const dialog = await openPreview("A birthday cake");
    await userEvent.click(within(dialog).getByRole("button", { name: "Close" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(opener).toHaveFocus();
  });
});

describe("AdminMemoryWall: deleting", () => {
  async function askToDelete(altText: string) {
    await userEvent.click(within(cardOf(altText)).getByRole("button", { name: /^Delete/ }));
    return screen.findByRole("dialog", { name: "Delete this photo?" });
  }

  it("warns that an upload is deleted for good", async () => {
    await show();

    const dialog = await askToDelete("Children at the park");

    expect(dialog).toHaveTextContent("This photo will be permanently deleted. This cannot be undone.");
    expect(dialog).not.toHaveTextContent("public Memory Wall");
  });

  it("also warns that a published photo disappears from the public wall", async () => {
    await show();

    const dialog = await askToDelete("A birthday cake");

    expect(dialog).toHaveTextContent("It will also disappear from the public Memory Wall.");
  });

  it("explains that a testimonial photo stays with the testimonial", async () => {
    await show(state([photo({ id: 4, source: "TESTIMONIAL", altText: "From a testimonial" })]));

    const dialog = await askToDelete("From a testimonial");

    expect(dialog).toHaveTextContent("It stays with the client's testimonial.");
    expect(dialog).not.toHaveTextContent("permanently deleted");
  });

  it("deletes nothing until it is confirmed", async () => {
    const fetchMock = await show();

    const dialog = await askToDelete("Children at the park");
    await userEvent.click(within(dialog).getByRole("button", { name: "Close" }));

    expect(callsTo(fetchMock, "DELETE", "/photos")).toHaveLength(0);
  });

  it("deletes the photo and reloads the list", async () => {
    const fetchMock = await show();

    const dialog = await askToDelete("Children at the park");
    await userEvent.click(within(dialog).getByRole("button", { name: "Yes, delete" }));

    expect(await screen.findByText("Photo deleted. Your available space has been updated.")).toBeInTheDocument();
    expect(String(callsTo(fetchMock, "DELETE", "/photos")[0][0])).toMatch(/\/photos\/2$/);
  });

  it("shows the server message when deleting fails", async () => {
    const world = state();
    world.fail["DELETE /photos"] = () => jsonResponse({ message: "Photo not found." }, false, 404);
    await show(world);

    const dialog = await askToDelete("Children at the park");
    await userEvent.click(within(dialog).getByRole("button", { name: "Yes, delete" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Photo not found.");
  });
});
