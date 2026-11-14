import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { screen, waitFor, within, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AdminResources from "./AdminResources";
import { renderApp, jsonResponse } from "../test/render";
import type { AdminResource, UploadRules } from "../api/contentApi";

const RULES: UploadRules = {
  maxFileSizeBytes: 1000,
  maxFileSizeLabel: "1 KB",
  allowedExtensions: ["pdf", "docx", "png", "jpg", "jpeg"],
  acceptedFormats: "PDF, DOCX, PNG or JPEG",
};

function resource(id: number, overrides: Partial<AdminResource> = {}): AdminResource {
  return {
    id,
    title: `Resource ${id}`,
    description: `Description ${id}`,
    category: "Guides",
    resourceType: "LINK",
    linkUrl: `https://example.org/${id}`,
    links: [`https://example.org/${id}`],
    fileName: null,
    fileContentType: null,
    fileSize: null,
    altText: null,
    publishedAt: null,
    status: "PUBLISHED",
    createdAt: "2026-11-01T10:00:00",
    updatedAt: "2026-11-01T10:00:00",
    ...overrides,
  };
}

type World = {
  resources: AdminResource[];
  rules: UploadRules | null;
  fail: Record<string, () => Promise<unknown>>;
};

function stubApi(world: World) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    const path = String(url);
    const method = init?.method ?? "GET";

    for (const [key, reply] of Object.entries(world.fail)) {
      const [m, part] = key.split(" ");
      if (m === method && path.includes(part)) return reply();
    }

    if (path.includes("upload-rules")) {
      return world.rules ? jsonResponse(world.rules) : jsonResponse({ message: "no rules" }, false, 500);
    }

    if (path.includes("/api/admin/resources") && (method === "POST" || method === "PUT")) {
      const data = init?.body as FormData;
      const saved = resource(method === "POST" ? 99 : Number(path.split("/").pop()), {
        title: String(data.get("title")),
        status: data.get("publish") === "true" ? "PUBLISHED" : "DRAFT",
      });
      return jsonResponse(saved);
    }

    if (path.includes("/api/admin/resources") && method === "DELETE") {
      return Promise.resolve({ ok: true, status: 204, json: async () => ({}), text: async () => "" });
    }

    if (path.includes("/api/admin/resources")) return jsonResponse(world.resources);

    return jsonResponse([]);
  });

  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

async function show(world: Partial<World> = {}) {
  const full: World = { resources: [], rules: RULES, fail: {}, ...world };
  const fetchMock = stubApi(full);
  renderApp("/admin", <AdminResources />);
  await waitFor(() => expect(screen.queryByText("Loading resources...")).not.toBeInTheDocument());
  return fetchMock;
}

function sends(fetchMock: ReturnType<typeof stubApi>) {
  return fetchMock.mock.calls.filter(([, init]) => init?.method === "POST" || init?.method === "PUT");
}

function sent(fetchMock: ReturnType<typeof stubApi>, index = 0) {
  return sends(fetchMock)[index][1]?.body as FormData;
}

async function fill(title = "New guide", description = "A helpful guide", category = "Guides", links = "https://example.org/guide") {
  await userEvent.type(screen.getByLabelText(/Resource title/), title);
  await userEvent.type(screen.getByLabelText(/Short description/), description);
  await userEvent.type(screen.getByLabelText(/^Category/), category);
  if (links) await userEvent.type(screen.getByLabelText(/^Links/), links);
}

function choose(file: File) {
  fireEvent.change(screen.getByLabelText(/Photo or document/), { target: { files: [file] } });
}

const pdf = () => new File(["abc"], "guide.pdf", { type: "application/pdf" });
const png = () => new File(["abc"], "photo.png", { type: "image/png" });

beforeEach(() => {
  localStorage.setItem("adminToken", "admin-token");
  sessionStorage.clear();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  localStorage.clear();
  sessionStorage.clear();
});

describe("AdminResources: loading and the list", () => {
  it("shows a loading message first", async () => {
    stubApi({ resources: [], rules: RULES, fail: {} });
    renderApp("/admin", <AdminResources />);

    expect(screen.getByText("Loading resources...")).toBeInTheDocument();
    await screen.findByText("No resources have been added yet.");
  });

  it("says when there are no resources", async () => {
    await show();

    expect(screen.getByText("No resources have been added yet.")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Existing resources (0)" })).toBeInTheDocument();
  });

  it("lists each resource with its badge, category and link count", async () => {
    await show({
      resources: [
        resource(1),
        resource(2, { status: "DRAFT", links: ["https://a.org", "https://b.org"] }),
        resource(3, { links: [], linkUrl: null, resourceType: "FILE", fileName: "guide.pdf", fileContentType: "application/pdf", fileSize: 2048 }),
      ],
    });

    expect(screen.getByRole("heading", { name: "Existing resources (3)" })).toBeInTheDocument();
    expect(screen.getAllByText("Published")).toHaveLength(2);
    expect(screen.getByText("Draft")).toBeInTheDocument();
    expect(screen.getByText(/Guides · 1 link$/)).toBeInTheDocument();
    expect(screen.getByText(/Guides · 2 links/)).toBeInTheDocument();
    expect(screen.getByText(/No links · File: guide\.pdf/)).toBeInTheDocument();
  });

  it("asks for resources and rules with the admin token", async () => {
    const fetchMock = await show();

    const urls = fetchMock.mock.calls.map(([url]) => String(url));
    expect(urls.some((u) => u.endsWith("/api/admin/resources"))).toBe(true);
    expect(urls.some((u) => u.includes("/api/admin/resources/upload-rules"))).toBe(true);
    expect((fetchMock.mock.calls[0][1]?.headers as Record<string, string>).Authorization).toBe("Bearer admin-token");
  });

  it("still shows the form and an empty list when the resources cannot be loaded", async () => {
    await show({ fail: { "GET /api/admin/resources": () => jsonResponse({ message: "Library is down." }, false, 409) } });

    expect(screen.getByRole("heading", { name: "Add Resource" })).toBeInTheDocument();
    expect(screen.getByText("No resources have been added yet.")).toBeInTheDocument();
  });

  it("sends the administrator to the login page when the session expired", async () => {
    stubApi({ resources: [], rules: RULES, fail: { "GET /api/admin/resources": () => jsonResponse("", false, 401) } });
    renderApp("/admin", <AdminResources />);

    expect(await screen.findByText(/Landed on \/login/)).toBeInTheDocument();
    expect(localStorage.getItem("adminToken")).toBeNull();
  });

  it("suggests the categories that already exist, once each", async () => {
    await show({ resources: [resource(1), resource(2), resource(3, { category: "Apps" })] });

    const options = Array.from(document.querySelectorAll("#resource-category-options option")).map((o) => (o as HTMLOptionElement).value);
    expect(options).toEqual(["Apps", "Guides"]);
  });
});

describe("AdminResources: the form", () => {
  it("starts as an empty Add Resource form", async () => {
    await show();

    expect(screen.getByRole("heading", { name: "Add Resource" })).toBeInTheDocument();
    expect(screen.getByLabelText(/Resource title/)).toHaveValue("");
    expect(screen.queryByRole("button", { name: "Cancel editing" })).not.toBeInTheDocument();
  });

  it("shows character counters that follow the typing", async () => {
    await show();

    expect(screen.getByText("0 of 80 characters")).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText(/Resource title/), "Hello");

    expect(screen.getByText("5 of 80 characters")).toBeInTheDocument();
    expect(screen.getByLabelText(/Resource title/)).toHaveAttribute("maxlength", "80");
    expect(screen.getByLabelText(/Short description/)).toHaveAttribute("maxlength", "300");
    expect(screen.getByLabelText(/^Category/)).toHaveAttribute("maxlength", "40");
  });

  it("counts the links and ignores blank lines and repeats", async () => {
    await show();

    await userEvent.type(screen.getByLabelText(/^Links/), "https://a.org{Enter}{Enter}https://a.org{Enter}https://b.org");

    expect(screen.getByText(/2 of 5\s+links added/)).toBeInTheDocument();
  });

  it("explains the accepted files using the server rules", async () => {
    await show();

    expect(screen.getByText(/Accepted: PDF, DOCX, PNG or JPEG\. Maximum size 1 KB\./)).toBeInTheDocument();
    expect(screen.getByLabelText(/Photo or document/)).toHaveAttribute("accept", ".pdf,.docx,.png,.jpg,.jpeg");
  });

  it("still works with a general file hint when the rules cannot be loaded", async () => {
    await show({ rules: null });

    expect(screen.getByText(/Choose a PDF, DOCX, PNG or JPEG file\./)).toBeInTheDocument();
    expect(screen.getByLabelText(/Photo or document/)).not.toHaveAttribute("accept");
  });
});

describe("AdminResources: validation", () => {
  it("asks for the title, description, category and a link or file", async () => {
    const fetchMock = await show();

    await userEvent.click(screen.getByRole("button", { name: "Publish" }));

    const box = screen.getByText("Please fix the following before saving:").closest("[role=alert]")!;
    expect(box).toHaveTextContent("Resource title is required.");
    expect(box).toHaveTextContent("Short description is required.");
    expect(box).toHaveTextContent("Category is required.");
    expect(box).toHaveTextContent("Add at least one link, or attach a document or image.");
    expect(sends(fetchMock)).toHaveLength(0);
  });

  it("moves keyboard focus to the first field that needs attention", async () => {
    await show();

    await userEvent.click(screen.getByRole("button", { name: "Save as draft" }));

    expect(screen.getByLabelText(/Resource title/)).toHaveFocus();
    expect(screen.getByLabelText(/Resource title/)).toHaveAttribute("aria-invalid", "true");
  });

  it("treats a title with only spaces as missing", async () => {
    await show();

    await fill("   ");
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));

    expect(screen.getAllByText("Resource title is required.").length).toBeGreaterThan(0);
  });

  it("rejects a link that is not a web address", async () => {
    const fetchMock = await show();

    await fill("T", "D", "C", "ftp://example.org/file");
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));

    expect(screen.getAllByText(/Every link must be a web address/).length).toBeGreaterThan(0);
    expect(sends(fetchMock)).toHaveLength(0);
  });

  it("allows at most five links", async () => {
    await show();

    await fill("T", "D", "C", "");
    await userEvent.type(
      screen.getByLabelText(/^Links/),
      [1, 2, 3, 4, 5, 6].map((n) => `https://example.org/${n}`).join("{Enter}")
    );
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));

    expect(screen.getAllByText("You can add up to 5 links.").length).toBeGreaterThan(0);
  });

  it("does not need a link when a file is attached", async () => {
    const fetchMock = await show();

    await fill("T", "D", "C", "");
    choose(pdf());
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));

    await waitFor(() => expect(sends(fetchMock)).toHaveLength(1));
  });

  it("asks for alternative text when the file is an image", async () => {
    const fetchMock = await show();

    await fill("T", "D", "C", "");
    choose(png());
    expect(screen.getByLabelText(/Alternative text/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));

    expect(screen.getAllByText(/Alternative text is required for images/).length).toBeGreaterThan(0);
    expect(screen.getByLabelText(/Alternative text/)).toHaveFocus();
    expect(sends(fetchMock)).toHaveLength(0);
  });

  it("treats alternative text as optional for a document", async () => {
    await show();

    choose(pdf());

    expect(screen.getByLabelText(/Alternative text/)).toBeInTheDocument();
    expect(screen.getByText("(optional)")).toBeInTheDocument();
  });

  it("hides the alternative text box when there is no file", async () => {
    await show();

    expect(screen.queryByLabelText(/Alternative text/)).not.toBeInTheDocument();
  });
});

describe("AdminResources: choosing a file", () => {
  it("rejects an empty file", async () => {
    await show();

    choose(new File([], "empty.pdf"));

    expect((await screen.findAllByText("The selected file is empty. Please choose a different file.")).length).toBeGreaterThan(0);
    expect(screen.queryByLabelText(/Alternative text/)).not.toBeInTheDocument();
  });

  it("rejects a file type that is not allowed", async () => {
    await show();

    choose(new File(["x"], "virus.exe"));

    expect((await screen.findAllByText("This file type is not supported. Please upload a PDF, DOCX, PNG or JPEG file.")).length).toBeGreaterThan(0);
  });

  it("rejects a file that is too large", async () => {
    await show();

    choose(new File(["x".repeat(2000)], "big.pdf"));

    expect((await screen.findAllByText("This file is too large. The maximum size is 1 KB.")).length).toBeGreaterThan(0);
  });

  it("accepts a good file and clears an earlier file error", async () => {
    await show();

    choose(new File(["x"], "virus.exe"));
    await screen.findAllByText(/not supported/);
    choose(pdf());

    await waitFor(() => expect(screen.queryByText(/not supported/)).not.toBeInTheDocument());
    expect(screen.getByLabelText(/Alternative text/)).toBeInTheDocument();
  });

  it("only checks that a file is not empty when the rules are not available", async () => {
    await show({ rules: null });

    choose(new File(["x"], "anything.exe"));

    await waitFor(() => expect(screen.getByLabelText(/Alternative text/)).toBeInTheDocument());
  });
});

describe("AdminResources: saving", () => {
  it("publishes a new resource and says so", async () => {
    const fetchMock = await show();

    await fill();
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));

    expect(await screen.findByText('"New guide" is published in the Resource Library.')).toBeInTheDocument();

    const [url, init] = sends(fetchMock)[0];
    expect(String(url)).toMatch(/\/api\/admin\/resources$/);
    expect(init?.method).toBe("POST");

    const data = sent(fetchMock);
    expect(data.get("title")).toBe("New guide");
    expect(data.get("category")).toBe("Guides");
    expect(data.get("publish")).toBe("true");
    expect(data.get("linkUrls")).toBe("https://example.org/guide");
    expect(data.get("removeFile")).toBe("false");
  });

  it("saves a draft without publishing", async () => {
    const fetchMock = await show();

    await fill();
    await userEvent.click(screen.getByRole("button", { name: "Save as draft" }));

    expect(await screen.findByText('"New guide" was saved as a draft. Visitors cannot see it yet.')).toBeInTheDocument();
    expect(sent(fetchMock).get("publish")).toBe("false");
  });

  it("sends the text without stray spaces and the links one per line", async () => {
    const fetchMock = await show();

    await fill("  Spaced  ", " Desc ", " Cat ", "");
    await userEvent.type(screen.getByLabelText(/^Links/), "https://a.org{Enter}{Enter}https://a.org{Enter}  https://b.org  ");
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));

    await waitFor(() => expect(sends(fetchMock)).toHaveLength(1));
    const data = sent(fetchMock);
    expect(data.get("title")).toBe("Spaced");
    expect(data.get("description")).toBe("Desc");
    expect(data.get("category")).toBe("Cat");
    expect(data.get("linkUrls")).toBe("https://a.org\nhttps://b.org");
  });

  it("sends the file and its alternative text for an image", async () => {
    const fetchMock = await show();

    await fill("Photo", "D", "C", "");
    choose(png());
    await userEvent.type(screen.getByLabelText(/Alternative text/), "A butterfly");
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));

    await waitFor(() => expect(sends(fetchMock)).toHaveLength(1));
    const data = sent(fetchMock);
    expect((data.get("file") as File).name).toBe("photo.png");
    expect(data.get("altText")).toBe("A butterfly");
  });

  it("does not send alternative text for a document", async () => {
    const fetchMock = await show();

    await fill("Doc", "D", "C", "");
    choose(pdf());
    await userEvent.type(screen.getByLabelText(/Alternative text/), "ignored");
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));

    await waitFor(() => expect(sends(fetchMock)).toHaveLength(1));
    expect(sent(fetchMock).get("altText")).toBe("");
  });

  it("clears the form and reloads the list after saving", async () => {
    const fetchMock = await show();

    await fill();
    const before = fetchMock.mock.calls.length;
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));
    await screen.findByText(/is published in the Resource Library/);

    expect(screen.getByLabelText(/Resource title/)).toHaveValue("");
    expect(fetchMock.mock.calls.length).toBeGreaterThan(before + 1);
  });

  it("shows the server message and keeps the typed details when saving fails", async () => {
    await show({ fail: { "POST /api/admin/resources": () => jsonResponse({ message: "Could not save it." }, false, 400) } });

    await fill();
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Could not save it.");
    expect(screen.getByLabelText(/Resource title/)).toHaveValue("New guide");
  });

  it("keeps the details for after sign-in when the session expired while saving", async () => {
    await show({ fail: { "POST /api/admin/resources": () => jsonResponse("", false, 401) } });

    await fill();
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));

    expect(await screen.findByText(/Landed on \/login/)).toBeInTheDocument();
    expect(sessionStorage.getItem("essential-butterfly:draft:resource:new")).toContain("New guide");
  });
});

describe("AdminResources: duplicates", () => {
  it("warns before saving a resource with the same title in any capitalisation", async () => {
    const fetchMock = await show({ resources: [resource(1, { title: "Coping Guide" })] });

    await fill("coping guide");
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));

    expect(await screen.findByText('A resource with the same title or link already exists: "Coping Guide".')).toBeInTheDocument();
    expect(sends(fetchMock)).toHaveLength(0);
  });

  it("warns when the first link is already used", async () => {
    await show({ resources: [resource(1)] });

    await fill("Other title", "D", "C", "https://example.org/1");
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));

    expect(await screen.findByText(/already exists: "Resource 1"/)).toBeInTheDocument();
  });

  it("moves focus to the warning", async () => {
    await show({ resources: [resource(1)] });

    await fill("Resource 1");
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));

    const warning = (await screen.findByText(/already exists/)).closest("[role=alert]")!;
    await waitFor(() => expect(warning).toHaveFocus());
  });

  it("Cancel closes the warning and keeps the typing", async () => {
    await show({ resources: [resource(1)] });

    await fill("Resource 1");
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));
    const warning = (await screen.findByText(/already exists/)).closest("[role=alert]") as HTMLElement;
    await userEvent.click(within(warning).getByRole("button", { name: "Cancel" }));

    expect(screen.queryByText(/already exists/)).not.toBeInTheDocument();
    expect(screen.getByLabelText(/Resource title/)).toHaveValue("Resource 1");
  });

  it("offers to edit the existing resource instead", async () => {
    await show({ resources: [resource(1, { description: "The old one" })] });

    await fill("Resource 1");
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));
    await userEvent.click(await screen.findByRole("button", { name: "Edit the existing resource" }));

    expect(screen.getByRole("heading", { name: "Edit resource: Resource 1" })).toBeInTheDocument();
    expect(screen.getByLabelText(/Short description/)).toHaveValue("The old one");
  });

  it("does not count the resource being edited as its own duplicate", async () => {
    const fetchMock = await show({ resources: [resource(1)] });

    await userEvent.click(screen.getByRole("button", { name: /Edit.*Resource 1/ }));
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));

    await waitFor(() => expect(sends(fetchMock)).toHaveLength(1));
  });

  it("shows a warning when the server says the resource already exists", async () => {
    await show({ fail: { "POST /api/admin/resources": () => jsonResponse({ message: "That link is already used." }, false, 409) } });

    await fill();
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));

    expect(await screen.findByText("That link is already used.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Cancel" })).toBeInTheDocument();
  });
});

describe("AdminResources: editing", () => {
  it("fills the form with the chosen resource", async () => {
    await show({ resources: [resource(2, { links: ["https://a.org", "https://b.org"], altText: null })] });

    await userEvent.click(screen.getByRole("button", { name: /Edit.*Resource 2/ }));

    expect(screen.getByRole("heading", { name: "Edit resource: Resource 2" })).toBeInTheDocument();
    expect(screen.getByLabelText(/Resource title/)).toHaveValue("Resource 2");
    expect(screen.getByLabelText(/^Links/)).toHaveValue("https://a.org\nhttps://b.org");
    expect(screen.getByLabelText(/Resource title/)).toHaveFocus();
  });

  it("updates with PUT to the resource address", async () => {
    const fetchMock = await show({ resources: [resource(2)] });

    await userEvent.click(screen.getByRole("button", { name: /Edit.*Resource 2/ }));
    const title = screen.getByLabelText(/Resource title/);
    await userEvent.clear(title);
    await userEvent.type(title, "Renamed");
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));

    expect(await screen.findByText('"Renamed" is published in the Resource Library.')).toBeInTheDocument();
    const [url, init] = sends(fetchMock)[0];
    expect(String(url)).toMatch(/\/api\/admin\/resources\/2$/);
    expect(init?.method).toBe("PUT");
  });

  it("warns that saving a published resource as a draft hides it", async () => {
    await show({ resources: [resource(1), resource(2, { status: "DRAFT" })] });

    await userEvent.click(screen.getByRole("button", { name: /Edit.*Resource 2/ }));
    expect(screen.queryByText(/This resource is published\./)).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /Edit.*Resource 1/ }));
    expect(screen.getByText(/This resource is published\./)).toBeInTheDocument();
  });

  it("Cancel editing goes back to an empty form", async () => {
    await show({ resources: [resource(1)] });

    await userEvent.click(screen.getByRole("button", { name: /Edit.*Resource 1/ }));
    await userEvent.click(screen.getByRole("button", { name: "Cancel editing" }));

    expect(screen.getByRole("heading", { name: "Add Resource" })).toBeInTheDocument();
    expect(screen.getByLabelText(/Resource title/)).toHaveValue("");
  });

  it("shows the current file and lets it be removed", async () => {
    const fetchMock = await show({
      resources: [resource(3, { fileName: "guide.pdf", fileContentType: "application/pdf", fileSize: 2048 })],
    });

    await userEvent.click(screen.getByRole("button", { name: /Edit.*Resource 3/ }));

    expect(screen.getByText(/Current file: guide\.pdf \(2 KB\)/)).toBeInTheDocument();

    await userEvent.click(screen.getByLabelText("Remove the current file from this resource"));
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));

    await waitFor(() => expect(sends(fetchMock)).toHaveLength(1));
    expect(sent(fetchMock).get("removeFile")).toBe("true");
  });

  it("hides the remove-file tick once a replacement file is chosen", async () => {
    await show({ resources: [resource(3, { fileName: "guide.pdf", fileContentType: "application/pdf", fileSize: 2048 })] });

    await userEvent.click(screen.getByRole("button", { name: /Edit.*Resource 3/ }));
    choose(pdf());

    expect(screen.queryByLabelText("Remove the current file from this resource")).not.toBeInTheDocument();
    expect(screen.queryByText(/Current file:/)).not.toBeInTheDocument();
  });

  it("asks for alternative text when an existing image is kept without it", async () => {
    await show({
      resources: [resource(4, { fileName: "photo.png", fileContentType: "image/png", fileSize: 500, links: [], linkUrl: null, resourceType: "FILE" })],
    });

    await userEvent.click(screen.getByRole("button", { name: /Edit.*Resource 4/ }));
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));

    expect(screen.getAllByText(/Alternative text is required for images/).length).toBeGreaterThan(0);
  });

  it("needs a link again after the only file is removed", async () => {
    await show({
      resources: [resource(4, { fileName: "doc.pdf", fileContentType: "application/pdf", fileSize: 500, links: [], linkUrl: null, resourceType: "FILE" })],
    });

    await userEvent.click(screen.getByRole("button", { name: /Edit.*Resource 4/ }));
    await userEvent.click(screen.getByLabelText("Remove the current file from this resource"));
    await userEvent.click(screen.getByRole("button", { name: "Publish" }));

    expect(screen.getAllByText("Add at least one link, or attach a document or image.").length).toBeGreaterThan(0);
  });
});

describe("AdminResources: removing", () => {
  it("asks first and does nothing when the administrator says no", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(false);
    const fetchMock = await show({ resources: [resource(1)] });

    await userEvent.click(screen.getByRole("button", { name: /Remove.*Resource 1/ }));

    expect(window.confirm).toHaveBeenCalledWith('Remove "Resource 1" from the Resource Library?');
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === "DELETE")).toBe(false);
  });

  it("removes the resource when confirmed and reloads the list", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const fetchMock = await show({ resources: [resource(1)] });

    await userEvent.click(screen.getByRole("button", { name: /Remove.*Resource 1/ }));

    expect(await screen.findByText('"Resource 1" was removed.')).toBeInTheDocument();
    const call = fetchMock.mock.calls.find(([, init]) => init?.method === "DELETE")!;
    expect(String(call[0])).toMatch(/\/api\/admin\/resources\/1$/);
  });

  it("clears the form when the resource being edited is removed", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true);
    await show({ resources: [resource(1)] });

    await userEvent.click(screen.getByRole("button", { name: /Edit.*Resource 1/ }));
    await userEvent.click(screen.getByRole("button", { name: /Remove.*Resource 1/ }));

    await screen.findByText('"Resource 1" was removed.');
    expect(screen.getByRole("heading", { name: "Add Resource" })).toBeInTheDocument();
  });

  it("shows the server message when removing fails", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true);
    await show({
      resources: [resource(1)],
      fail: { "DELETE /api/admin/resources": () => jsonResponse({ message: "Cannot remove it." }, false, 409) },
    });

    await userEvent.click(screen.getByRole("button", { name: /Remove.*Resource 1/ }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Cannot remove it.");
  });

  it("sends the administrator to the login page when the session expired", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true);
    await show({
      resources: [resource(1)],
      fail: { "DELETE /api/admin/resources": () => jsonResponse("", false, 401) },
    });

    await userEvent.click(screen.getByRole("button", { name: /Remove.*Resource 1/ }));

    expect(await screen.findByText(/Landed on \/login/)).toBeInTheDocument();
  });
});

describe("AdminResources: unsaved details", () => {
  it("keeps typed details while open and drops them when nothing differs", async () => {
    await show();

    const title = screen.getByLabelText(/Resource title/);
    await userEvent.type(title, "A");
    await waitFor(() => expect(sessionStorage.getItem("essential-butterfly:draft:resource:new")).not.toBeNull());

    await userEvent.type(title, "{Backspace}");
    await waitFor(() => expect(sessionStorage.getItem("essential-butterfly:draft:resource:new")).toBeNull());
  });

  it("restores details kept from before a sign-out and says so", async () => {
    sessionStorage.setItem(
      "essential-butterfly:draft:resource:new",
      JSON.stringify({ title: "Kept title", description: "", category: "", linkUrls: "", altText: "", removeFile: false })
    );

    await show();

    expect(screen.getByLabelText(/Resource title/)).toHaveValue("Kept title");
    expect(screen.getByText(/We restored the details you had entered before you were signed out\./)).toBeInTheDocument();
  });

  it("Cancel editing throws away the kept details", async () => {
    await show({ resources: [resource(1)] });

    await userEvent.click(screen.getByRole("button", { name: /Edit.*Resource 1/ }));
    await userEvent.type(screen.getByLabelText(/Resource title/), "x");
    await waitFor(() => expect(sessionStorage.getItem("essential-butterfly:draft:resource:1")).not.toBeNull());
    await userEvent.click(screen.getByRole("button", { name: "Cancel editing" }));

    expect(sessionStorage.getItem("essential-butterfly:draft:resource:1")).toBeNull();
  });
});
