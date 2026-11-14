import { describe, it, expect } from "vitest";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Resources from "./Resources";
import { mockFetch, mockFetchNetworkError, mockFetchSequence, renderPage } from "../test/helpers";
import type { PublicResource } from "../api/contentApi";

function resource(overrides: Partial<PublicResource> = {}): PublicResource {
  return {
    id: 1,
    title: "Coping guide",
    description: "A short guide for hard days.",
    category: "Guides",
    resourceType: "LINK",
    linkUrl: "https://example.org/coping",
    links: ["https://example.org/coping"],
    fileName: null,
    fileContentType: null,
    fileSize: null,
    altText: null,
    publishedAt: "2026-01-01T00:00:00",
    ...overrides,
  };
}

const guide = resource();
const pdf = resource({
  id: 2,
  title: "Intake checklist",
  description: "Everything to bring to your first visit.",
  category: "Forms",
  resourceType: "FILE",
  linkUrl: null,
  links: [],
  fileName: "checklist.pdf",
  fileContentType: "application/pdf",
  fileSize: 2 * 1024 * 1024,
});
const poster = resource({
  id: 3,
  title: "Calm poster",
  description: "A poster for the waiting room.",
  category: "Posters",
  resourceType: "FILE",
  linkUrl: null,
  links: [],
  fileName: "poster.png",
  fileContentType: "image/png",
  fileSize: 300 * 1024,
  altText: "A butterfly on a leaf",
});

function renderLibrary(list: PublicResource[]) {
  mockFetch(true, JSON.stringify(list));
  return renderPage("/resources", <Resources />);
}

describe("Resources page", () => {
  it("shows a loading message and then the resources", async () => {
    renderLibrary([guide, pdf]);

    expect(screen.getByText("Loading resources...")).toBeInTheDocument();

    expect(await screen.findByText("Coping guide")).toBeInTheDocument();
    expect(screen.getByText("Intake checklist")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Resource Library" })).toBeInTheDocument();
  });

  it("loads the public list without a token", async () => {
    const fetchMock = mockFetch(true, "[]");
    renderPage("/resources", <Resources />);
    await screen.findByText(/No resources have been published yet/);

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toMatch(/\/api\/resources$/);
    expect(init).toBeUndefined();
  });

  it("counts the resources found", async () => {
    renderLibrary([guide, pdf]);
    expect(await screen.findByText("2 resources found.")).toBeInTheDocument();
  });

  it("uses the singular for one resource", async () => {
    renderLibrary([guide]);
    expect(await screen.findByText("1 resource found.")).toBeInTheDocument();
  });

  it("says so when nothing has been published yet", async () => {
    renderLibrary([]);

    expect(
      await screen.findByText("No resources have been published yet. Please check back soon.")
    ).toBeInTheDocument();
    expect(screen.getByText("0 resources found.")).toBeInTheDocument();
  });

  it("shows a long description as a short excerpt", async () => {
    const longText = "word ".repeat(60).trim();
    renderLibrary([resource({ description: longText })]);

    const excerpt = await screen.findByText(/^word word/);

    expect(excerpt.textContent!.endsWith("…")).toBe(true);
    expect(excerpt.textContent!.length).toBeLessThan(longText.length);
  });

  it("shows a short description in full", async () => {
    renderLibrary([guide]);
    expect(await screen.findByText("A short guide for hard days.")).toBeInTheDocument();
  });

  describe("search and category filter", () => {
    it("lists every category once, in alphabetical order", async () => {
      renderLibrary([pdf, guide, resource({ id: 4, title: "Another guide", category: "Guides" })]);
      await screen.findByText("Coping guide");

      const options = within(screen.getByLabelText("Category")).getAllByRole("option");

      expect(options.map((option) => option.textContent)).toEqual(["All categories", "Forms", "Guides"]);
    });

    it("filters by search word in the title", async () => {
      const user = userEvent.setup();
      renderLibrary([guide, pdf]);
      await screen.findByText("Coping guide");

      await user.type(screen.getByLabelText("Search resources"), "checklist");

      expect(screen.getByText("Intake checklist")).toBeInTheDocument();
      expect(screen.queryByText("Coping guide")).not.toBeInTheDocument();
      expect(screen.getByText("1 resource found.")).toBeInTheDocument();
    });

    it("also searches the description and the category, ignoring capitals", async () => {
      const user = userEvent.setup();
      renderLibrary([guide, pdf]);
      await screen.findByText("Coping guide");

      await user.type(screen.getByLabelText("Search resources"), "FIRST VISIT");
      expect(screen.getByText("Intake checklist")).toBeInTheDocument();
      expect(screen.queryByText("Coping guide")).not.toBeInTheDocument();

      await user.clear(screen.getByLabelText("Search resources"));
      await user.type(screen.getByLabelText("Search resources"), "forms");
      expect(screen.getByText("Intake checklist")).toBeInTheDocument();
    });

    it("filters by category", async () => {
      const user = userEvent.setup();
      renderLibrary([guide, pdf]);
      await screen.findByText("Coping guide");

      await user.selectOptions(screen.getByLabelText("Category"), "Forms");

      expect(screen.getByText("Intake checklist")).toBeInTheDocument();
      expect(screen.queryByText("Coping guide")).not.toBeInTheDocument();
    });

    it("combines the search word and the category", async () => {
      const user = userEvent.setup();
      renderLibrary([guide, pdf]);
      await screen.findByText("Coping guide");

      await user.selectOptions(screen.getByLabelText("Category"), "Forms");
      await user.type(screen.getByLabelText("Search resources"), "coping");

      expect(
        screen.getByText(/No resources match your search/)
      ).toBeInTheDocument();
      expect(screen.getByText("0 resources found.")).toBeInTheDocument();
    });

    it("shows everything again after choosing All categories", async () => {
      const user = userEvent.setup();
      renderLibrary([guide, pdf]);
      await screen.findByText("Coping guide");

      await user.selectOptions(screen.getByLabelText("Category"), "Forms");
      await user.selectOptions(screen.getByLabelText("Category"), "");

      expect(screen.getByText("2 resources found.")).toBeInTheDocument();
    });

    it("does not reload the page when the search form is submitted", async () => {
      const user = userEvent.setup();
      const fetchMock = mockFetch(true, JSON.stringify([guide]));
      renderPage("/resources", <Resources />);
      await screen.findByText("Coping guide");

      await user.type(screen.getByLabelText("Search resources"), "coping{Enter}");

      expect(fetchMock).toHaveBeenCalledTimes(1);
      expect(screen.getByText("Coping guide")).toBeInTheDocument();
    });
  });

  describe("images", () => {
    it("shows a thumbnail with the alternative text for an image resource", async () => {
      renderLibrary([poster]);

      const thumb = await screen.findByRole("img", { name: "A butterfly on a leaf" });

      expect(thumb).toHaveAttribute("src", expect.stringMatching(/\/api\/resources\/3\/download$/));
    });

    it("shows no thumbnail for documents and links", async () => {
      renderLibrary([guide, pdf]);
      await screen.findByText("Coping guide");

      expect(screen.queryByRole("img")).not.toBeInTheDocument();
    });
  });

  describe("Read more window", () => {
    it("opens the details of a link resource", async () => {
      const user = userEvent.setup();
      renderLibrary([guide]);

      await user.click(await screen.findByRole("button", { name: /read more/i }));

      const dialog = document.querySelector("dialog")!;
      expect(dialog).toHaveAttribute("open");
      expect(within(dialog).getByRole("heading", { name: "Coping guide" })).toBeInTheDocument();
      expect(within(dialog).getByRole("heading", { name: "Link" })).toBeInTheDocument();

      const link = within(dialog).getByRole("link", { name: /example\.org\/coping/ });
      expect(link).toHaveAttribute("href", "https://example.org/coping");
      expect(link).toHaveAttribute("target", "_blank");
      expect(link).toHaveAttribute("rel", "noopener noreferrer");
    });

    it("uses the plural heading for several links", async () => {
      const user = userEvent.setup();
      renderLibrary([
        resource({ links: ["https://example.org/a", "https://example.org/b"] }),
      ]);

      await user.click(await screen.findByRole("button", { name: /read more/i }));

      const dialog = document.querySelector("dialog")!;
      expect(within(dialog).getByRole("heading", { name: "Links" })).toBeInTheDocument();
      expect(within(dialog).getAllByRole("link")).toHaveLength(2);
    });

    it("only turns web addresses into links", async () => {
      const user = userEvent.setup();
      renderLibrary([
        resource({ links: ["javascript:alert(1)", "https://example.org/safe", "ftp://example.org/x"] }),
      ]);

      await user.click(await screen.findByRole("button", { name: /read more/i }));

      const dialog = document.querySelector("dialog")!;
      const links = within(dialog).getAllByRole("link");
      expect(links).toHaveLength(1);
      expect(links[0]).toHaveAttribute("href", "https://example.org/safe");
    });

    it("offers Open and Download for a document, with its name, type and size", async () => {
      const user = userEvent.setup();
      renderLibrary([pdf]);

      await user.click(await screen.findByRole("button", { name: /read more/i }));

      const dialog = document.querySelector("dialog")!;
      expect(within(dialog).getByRole("heading", { name: "PDF document" })).toBeInTheDocument();
      expect(within(dialog).getByText("checklist.pdf · 2.0 MB")).toBeInTheDocument();

      const open = within(dialog).getByRole("link", { name: /^Open/ });
      expect(open).toHaveAttribute("href", expect.stringMatching(/\/api\/resources\/2\/download$/));
      expect(open).toHaveAttribute("target", "_blank");

      const download = within(dialog).getByRole("link", { name: /^Download/ });
      expect(download).toHaveAttribute("download", "checklist.pdf");
    });

    it("names other kinds of file correctly", async () => {
      const user = userEvent.setup();
      renderLibrary([
        resource({
          fileName: "letter.docx",
          fileContentType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          fileSize: 1024,
          links: [],
        }),
      ]);

      await user.click(await screen.findByRole("button", { name: /read more/i }));

      expect(
        within(document.querySelector("dialog")!).getByRole("heading", { name: "Word document" })
      ).toBeInTheDocument();
    });

    it("shows the picture with its alternative text for an image", async () => {
      const user = userEvent.setup();
      renderLibrary([poster]);

      await user.click(await screen.findByRole("button", { name: /read more/i }));

      const dialog = document.querySelector("dialog")!;
      expect(within(dialog).getByRole("heading", { name: "Image" })).toBeInTheDocument();
      expect(within(dialog).getByRole("img", { name: "A butterfly on a leaf" })).toBeInTheDocument();
    });

    it("closes with the Close button", async () => {
      const user = userEvent.setup();
      renderLibrary([guide]);

      await user.click(await screen.findByRole("button", { name: /read more/i }));
      await user.click(within(document.querySelector("dialog")!).getByRole("button", { name: /close/i }));

      expect(document.querySelector("dialog")).toBeNull();
    });

    it("closes when the dimmed area around it is clicked", async () => {
      const user = userEvent.setup();
      renderLibrary([guide]);

      await user.click(await screen.findByRole("button", { name: /read more/i }));
      await user.click(document.querySelector("dialog")!);

      expect(document.querySelector("dialog")).toBeNull();
    });

    it("stays open when the text inside it is clicked", async () => {
      const user = userEvent.setup();
      renderLibrary([guide]);

      await user.click(await screen.findByRole("button", { name: /read more/i }));
      await user.click(within(document.querySelector("dialog")!).getByRole("heading", { name: "Coping guide" }));

      expect(document.querySelector("dialog")).not.toBeNull();
    });
  });

  describe("when loading fails", () => {
    it("shows an error with a retry button", async () => {
      mockFetchSequence({ ok: false, status: 500, body: "" });
      renderPage("/resources", <Resources />);

      const alert = await screen.findByRole("alert");

      expect(alert).toHaveTextContent("The Resource Library could not be loaded right now.");
      expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument();
    });

    it("shows the same error when the server cannot be reached", async () => {
      mockFetchNetworkError();
      renderPage("/resources", <Resources />);

      expect(await screen.findByRole("alert")).toHaveTextContent("could not be loaded");
    });

    it("loads the list when Try again is pressed", async () => {
      const user = userEvent.setup();
      mockFetchSequence(
        { ok: false, status: 500, body: "" },
        { ok: true, body: JSON.stringify([guide]) }
      );
      renderPage("/resources", <Resources />);

      await user.click(await screen.findByRole("button", { name: "Try again" }));

      expect(await screen.findByText("Coping guide")).toBeInTheDocument();
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });
  });
});
