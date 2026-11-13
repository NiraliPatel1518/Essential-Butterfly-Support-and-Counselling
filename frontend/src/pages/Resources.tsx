import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  fetchPublicResources,
  resourceDownloadUrl,
  type PublicResource,
} from "../api/contentApi";
import { formatFileSize } from "../utils/contentFormat";
import "./Resources.css";

type LoadState = "loading" | "ready" | "error";

const EXCERPT_LENGTH = 110;

// Links come from administrators and are checked by the server; this is a
// second guard so only web addresses are ever turned into a clickable link.
function isWebLink(value: string): boolean {
  return /^https?:\/\//i.test(value);
}

function isImage(resource: PublicResource): boolean {
  return (
    resource.fileName !== null &&
    (resource.fileContentType ?? "").startsWith("image/")
  );
}

// Just a little of the description; the rest is behind "Read more".
function excerpt(text: string): string {
  if (text.length <= EXCERPT_LENGTH) return text;

  const cut = text.slice(0, EXCERPT_LENGTH);
  const lastSpace = cut.lastIndexOf(" ");

  return `${(lastSpace > 60 ? cut.slice(0, lastSpace) : cut).trimEnd()}…`;
}

function fileKind(resource: PublicResource): string {
  const type = resource.fileContentType ?? "";

  if (type === "application/pdf") return "PDF document";
  if (type.startsWith("image/")) return "Image";
  if (type.includes("wordprocessingml")) return "Word document";

  return "Document";
}

/*
 * The "Read more" popup. It uses the browser's own modal dialog, so focus
 * stays inside it, Escape closes it, and focus returns to the button that
 * opened it.
 */
function ResourceDialog({
  resource,
  onClose,
}: {
  resource: PublicResource;
  onClose: () => void;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const titleId = `resource-dialog-title-${resource.id}`;
  const fileUrl = resourceDownloadUrl(resource.id);
  const links = resource.links.filter(isWebLink);
  const hasFile = resource.fileName !== null;

  useEffect(() => {
    const dialog = dialogRef.current;

    if (!dialog || dialog.open) return;

    if (typeof dialog.showModal === "function") {
      dialog.showModal();
    } else {
      dialog.setAttribute("open", "");
    }
  }, []);

  return (
    <dialog
      ref={dialogRef}
      className="resource-dialog"
      aria-labelledby={titleId}
      onClose={onClose}
      onCancel={onClose}
      onClick={(event) => {
        // A click on the dimmed area around the popup closes it.
        if (event.target === dialogRef.current) onClose();
      }}
    >
      <div className="resource-dialog-body">
        <button
          type="button"
          className="resource-dialog-close"
          onClick={onClose}
        >
          Close
          <span className="sr-only"> window</span>
        </button>

        <p className="resource-category">{resource.category}</p>
        <h2 id={titleId}>{resource.title}</h2>

        {hasFile && isImage(resource) && (
          <img
            className="resource-dialog-image"
            src={fileUrl}
            alt={resource.altText ?? ""}
          />
        )}

        <p className="resource-dialog-text">{resource.description}</p>

        {links.length > 0 && (
          <section aria-labelledby={`${titleId}-links`}>
            <h3 id={`${titleId}-links`}>
              {links.length === 1 ? "Link" : "Links"}
            </h3>
            <ul className="resource-link-list">
              {links.map((link) => (
                <li key={link}>
                  <a href={link} target="_blank" rel="noopener noreferrer">
                    {link}
                    <span className="sr-only"> (opens in a new tab)</span>
                  </a>
                </li>
              ))}
            </ul>
          </section>
        )}

        {hasFile && (
          <section aria-labelledby={`${titleId}-file`}>
            <h3 id={`${titleId}-file`}>{fileKind(resource)}</h3>
            <p className="resource-meta">
              {resource.fileName}
              {resource.fileSize !== null &&
                ` · ${formatFileSize(resource.fileSize)}`}
            </p>
            <div className="resource-actions">
              <a href={fileUrl} target="_blank" rel="noopener noreferrer">
                Open
                <span className="sr-only">
                  {" "}
                  {resource.title} (opens in a new tab)
                </span>
              </a>
              <a href={fileUrl} download={resource.fileName ?? undefined}>
                Download
                <span className="sr-only">
                  {" "}
                  {resource.title}, file {resource.fileName}
                </span>
              </a>
            </div>
          </section>
        )}
      </div>
    </dialog>
  );
}

function Resources() {
  const [resources, setResources] = useState<PublicResource[]>([]);
  const [state, setState] = useState<LoadState>("loading");
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("");
  const [openId, setOpenId] = useState<number | null>(null);

  const load = useCallback(async () => {
    setState("loading");

    try {
      setResources(await fetchPublicResources());
      setState("ready");
    } catch {
      setState("error");
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const categories = useMemo(
    () =>
      Array.from(new Set(resources.map((resource) => resource.category))).sort(
        (a, b) => a.localeCompare(b)
      ),
    [resources]
  );

  const visible = useMemo(() => {
    const term = search.trim().toLowerCase();

    return resources.filter((resource) => {
      if (
        category &&
        resource.category.toLowerCase() !== category.toLowerCase()
      ) {
        return false;
      }

      if (!term) return true;

      return [resource.title, resource.description, resource.category].some(
        (text) => text.toLowerCase().includes(term)
      );
    });
  }, [resources, search, category]);

  const opened = resources.find((resource) => resource.id === openId) ?? null;

  const resultText =
    visible.length === 1
      ? "1 resource found."
      : `${visible.length} resources found.`;

  return (
    <main className="resources-page">
      <header className="resources-header">
        <h1>Resource Library</h1>
        <p>
          Guides, documents and links chosen to support individuals and
          families. Search by keyword or choose a category.
        </p>
      </header>

      <form
        className="resources-filters"
        role="search"
        aria-label="Search the Resource Library"
        onSubmit={(event) => event.preventDefault()}
      >
        <div className="resources-field">
          <label htmlFor="resource-search">Search resources</label>
          <input
            id="resource-search"
            type="search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </div>

        <div className="resources-field">
          <label htmlFor="resource-category">Category</label>
          <select
            id="resource-category"
            value={category}
            onChange={(event) => setCategory(event.target.value)}
          >
            <option value="">All categories</option>
            {categories.map((name) => (
              <option key={name} value={name}>
                {name}
              </option>
            ))}
          </select>
        </div>
      </form>

      {state === "loading" && (
        <p className="resources-message" role="status">
          Loading resources...
        </p>
      )}

      {state === "error" && (
        <div className="resources-message resources-error" role="alert">
          <p>The Resource Library could not be loaded right now.</p>
          <button type="button" onClick={() => void load()}>
            Try again
          </button>
        </div>
      )}

      {state === "ready" && (
        <>
          <p className="resources-count" role="status" aria-live="polite">
            {resultText}
          </p>

          {resources.length === 0 ? (
            <p className="resources-message">
              No resources have been published yet. Please check back soon.
            </p>
          ) : visible.length === 0 ? (
            <p className="resources-message">
              No resources match your search. Try a different word or choose
              All categories.
            </p>
          ) : (
            <ul className="resource-list">
              {visible.map((resource) => {
                const titleId = `resource-title-${resource.id}`;

                return (
                  <li key={resource.id}>
                    <article
                      className="resource-row"
                      aria-labelledby={titleId}
                    >
                      {isImage(resource) && (
                        <img
                          className="resource-thumb"
                          src={resourceDownloadUrl(resource.id)}
                          alt={resource.altText ?? ""}
                          loading="lazy"
                        />
                      )}

                      <div className="resource-main">
                        <p className="resource-category">{resource.category}</p>
                        <h2 id={titleId}>{resource.title}</h2>
                        <p className="resource-excerpt">
                          {excerpt(resource.description)}
                        </p>
                      </div>

                      <button
                        type="button"
                        className="resource-more"
                        aria-haspopup="dialog"
                        onClick={() => setOpenId(resource.id)}
                      >
                        Read more
                        <span className="sr-only"> about {resource.title}</span>
                      </button>
                    </article>
                  </li>
                );
              })}
            </ul>
          )}
        </>
      )}

      {opened && (
        <ResourceDialog resource={opened} onClose={() => setOpenId(null)} />
      )}
    </main>
  );
}

export default Resources;
