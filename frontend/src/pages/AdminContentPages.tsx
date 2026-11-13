import { useCallback, useEffect, useMemo, useState } from "react";
import {
  ApiError,
  fetchAdminPages,
  saveAdminPage,
  type ContentPageAdmin,
  type ContentPageKey,
} from "../api/contentApi";
import {
  PAGE_KEYS,
  PAGE_LAYOUTS,
  SERVICES_MAX,
  SERVICE_TEXT_MAX,
  SERVICE_TITLE_MAX,
  defaultPageContent,
  newServiceId,
  resolvePageContent,
  serializePageContent,
  validatePageContent,
  type PageContent,
  type SlotSpec,
} from "../content/pageLayouts";
import { useAdminSession } from "../hooks/useAdminSession";
import { clearDraft, loadDraft, saveDraft } from "../utils/draftStorage";
import About from "./About";
import Services from "./Services";
import { Privacy } from "./InformationPages";
import "./AdminContent.css";

type Errors = Record<string, string>;

const draftKey = (key: ContentPageKey) => `page:${key}`;

function formatDate(value: string | null): string {
  if (!value) return "";

  const date = new Date(value);

  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

// What the editor shows first: the draft, else the live page, else the
// website's original wording.
function startingContent(page: ContentPageAdmin): {
  content: PageContent;
  builtIn: boolean;
} {
  if (page.draftBody !== null) {
    return {
      content: resolvePageContent(page.pageKey, page.draftBody),
      builtIn: false,
    };
  }

  if (page.publishedBody !== null) {
    return {
      content: resolvePageContent(page.pageKey, page.publishedBody),
      builtIn: false,
    };
  }

  return { content: defaultPageContent(page.pageKey), builtIn: true };
}

function statusText(page: ContentPageAdmin): string {
  if (!page.published) {
    return "Not edited yet. The website is showing its original wording.";
  }

  return page.hasUnpublishedChanges
    ? `Live on the website since ${formatDate(page.publishedAt)}. You have changes that are not published yet.`
    : `Live on the website since ${formatDate(page.publishedAt)}.`;
}

// Removes stray spaces before saving. The design is never touched.
function tidy(content: PageContent): PageContent {
  const fields: Record<string, string> = {};

  for (const [key, value] of Object.entries(content.fields)) {
    fields[key] = value.trim();
  }

  return {
    fields,
    services: content.services?.map((service) => ({
      ...service,
      title: service.title.trim(),
      text: service.text.trim(),
    })),
  };
}

function PagePreview({
  pageKey,
  content,
}: {
  pageKey: ContentPageKey;
  content: PageContent;
}) {
  if (pageKey === "services") return <Services preview={content} />;
  if (pageKey === "about") return <About preview={content} />;

  return <Privacy preview={content} />;
}

function AdminContentPages() {
  const handleSessionExpired = useAdminSession();

  const [pages, setPages] = useState<ContentPageAdmin[]>([]);
  const [selectedKey, setSelectedKey] = useState<ContentPageKey>("services");
  const [content, setContent] = useState<PageContent>(() =>
    defaultPageContent("services")
  );
  const [baseline, setBaseline] = useState<PageContent>(() =>
    defaultPageContent("services")
  );
  const [errors, setErrors] = useState<Errors>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [previewing, setPreviewing] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [builtIn, setBuiltIn] = useState(false);
  const [focusId, setFocusId] = useState("");

  const layout = PAGE_LAYOUTS[selectedKey];
  const selectedPage = pages.find((page) => page.pageKey === selectedKey);

  const dirty = useMemo(
    () => JSON.stringify(content) !== JSON.stringify(baseline),
    [content, baseline]
  );

  const groups = useMemo(() => {
    const found: { name: string; slots: SlotSpec[] }[] = [];

    for (const item of layout.slots) {
      const last = found[found.length - 1];

      if (last && last.name === item.group) {
        last.slots.push(item);
      } else {
        found.push({ name: item.group, slots: [item] });
      }
    }

    return found;
  }, [layout]);

  // Opens a page in the editor, restoring wording that was kept when a
  // session ended.
  const openPage = useCallback((page: ContentPageAdmin) => {
    const start = startingContent(page);
    const kept = loadDraft<PageContent>(draftKey(page.pageKey));

    setSelectedKey(page.pageKey);
    setBaseline(start.content);
    setBuiltIn(start.builtIn);
    setErrors({});
    setError("");
    setPreviewing(false);

    if (
      kept &&
      kept.fields &&
      JSON.stringify(kept) !== JSON.stringify(start.content)
    ) {
      setContent(kept);
      setNotice(
        "We restored the changes you had before you were signed out. Review them, then save or publish."
      );
    } else {
      setContent(start.content);
      setNotice("");
    }
  }, []);

  const loadPages = useCallback(async () => {
    setLoading(true);

    try {
      const loaded = await fetchAdminPages();
      const ordered = PAGE_KEYS.map((key) =>
        loaded.find((page) => page.pageKey === key)
      ).filter((page): page is ContentPageAdmin => Boolean(page));

      setPages(ordered);

      if (ordered.length > 0) {
        openPage(ordered[0]);
      }
    } catch (loadError) {
      if (!handleSessionExpired(loadError)) {
        setError(
          loadError instanceof Error
            ? loadError.message
            : "The website pages could not be loaded."
        );
      }
    } finally {
      setLoading(false);
    }
  }, [handleSessionExpired, openPage]);

  useEffect(() => {
    void loadPages();
  }, [loadPages]);

  // Keep what has been typed for a short time, in case the sign-in expires.
  useEffect(() => {
    if (loading) return;

    if (dirty) {
      saveDraft(draftKey(selectedKey), content);
    } else {
      clearDraft(draftKey(selectedKey));
    }
  }, [dirty, content, loading, selectedKey]);

  // Moves keyboard focus once the element it points to exists.
  useEffect(() => {
    if (!focusId) return;

    document.getElementById(focusId)?.focus();
    setFocusId("");
  }, [focusId, content]);

  const choosePage = (key: ContentPageKey) => {
    const page = pages.find((item) => item.pageKey === key);

    if (page && key !== selectedKey) {
      openPage(page);
    }
  };

  const setField = (key: string, value: string) => {
    setContent((current) => ({
      ...current,
      fields: { ...current.fields, [key]: value },
    }));
  };

  const setService = (
    index: number,
    changes: Partial<{ title: string; text: string; comingSoon: boolean }>
  ) => {
    setContent((current) => ({
      ...current,
      services: (current.services ?? []).map((service, position) =>
        position === index ? { ...service, ...changes } : service
      ),
    }));
  };

  const addService = () => {
    const current = content.services ?? [];

    if (current.length >= SERVICES_MAX) return;

    setContent({
      ...content,
      services: [
        ...current,
        { id: newServiceId(current), title: "", text: "", comingSoon: false },
      ],
    });
    setNotice("");
    setFocusId(`service-title-${current.length}`);
  };

  const removeService = (index: number) => {
    const current = content.services ?? [];
    const removed = current[index];

    setContent({
      ...content,
      services: current.filter((_, position) => position !== index),
    });
    setErrors({});
    setNotice(
      `"${removed?.title || "The service"}" was removed from your draft. It disappears from the public website when you publish.`
    );
    setFocusId("service-add");
  };

  const restoreOriginal = () => {
    setContent(defaultPageContent(selectedKey));
    setErrors({});
    setNotice(
      "The original wording is back in the editor. Save or publish to keep it."
    );
  };

  const submit = async (publish: boolean) => {
    setNotice("");
    setError("");

    const found = validatePageContent(selectedKey, content);
    setErrors(found);

    // Keyboard focus moves to the first field that needs attention.
    const firstId = Object.keys(found)[0];

    if (firstId) {
      setFocusId(firstId);
      return;
    }

    setSaving(true);

    try {
      const saved = await saveAdminPage(selectedKey, {
        title: layout.label,
        body: serializePageContent(tidy(content)),
        publish,
      });

      setPages((current) =>
        current.map((page) => (page.pageKey === saved.pageKey ? saved : page))
      );

      const next = startingContent(saved).content;
      setContent(next);
      setBaseline(next);
      setBuiltIn(false);
      clearDraft(draftKey(selectedKey));

      setNotice(
        publish
          ? "Published. The updated wording is now live on the public website."
          : "Draft saved. Visitors still see the previously published wording."
      );
    } catch (saveError) {
      // The typed text stays on screen and is kept for after the sign-in.
      if (saveError instanceof ApiError && saveError.status === 401) {
        saveDraft(draftKey(selectedKey), content);
      }

      if (!handleSessionExpired(saveError)) {
        setError(
          saveError instanceof Error
            ? saveError.message
            : "We could not save your changes. Please try again."
        );
      }
    } finally {
      setSaving(false);
    }
  };

  const errorList = Object.values(errors);

  const renderSlot = (item: SlotSpec) => {
    const id = `slot-${item.key}`;
    const value = content.fields[item.key] ?? "";
    const invalid = Boolean(errors[id]);
    const describedBy = `${id}-count${invalid ? ` ${id}-error` : ""}`;

    return (
      <div className="admin-content-field" key={item.key}>
        <label htmlFor={id}>
          {item.label} <span aria-hidden="true">*</span>
        </label>
        {item.multiline ? (
          <textarea
            id={id}
            value={value}
            rows={Math.min(6, Math.max(2, Math.ceil(item.max / 90)))}
            maxLength={item.max}
            aria-required="true"
            aria-invalid={invalid ? "true" : undefined}
            aria-describedby={describedBy}
            onChange={(event) => setField(item.key, event.target.value)}
          />
        ) : (
          <input
            id={id}
            value={value}
            maxLength={item.max}
            aria-required="true"
            aria-invalid={invalid ? "true" : undefined}
            aria-describedby={describedBy}
            onChange={(event) => setField(item.key, event.target.value)}
          />
        )}
        <small id={`${id}-count`} className="admin-content-counter">
          {value.length} of {item.max} characters
        </small>
        {invalid && (
          <p id={`${id}-error`} className="admin-content-field-error">
            {errors[id]}
          </p>
        )}
      </div>
    );
  };

  const renderServices = () => {
    const services = content.services ?? [];

    return (
      <fieldset className="admin-content-fieldset">
        <legend>Services list</legend>
        <p className="admin-content-hint">
          Each service appears as a card on the public Services page. You can
          change the wording, add up to {SERVICES_MAX} services or remove one.
          The card design stays the same.
        </p>

        {services.map((service, index) => {
          const titleId = `service-title-${index}`;
          const textId = `service-text-${index}`;
          const soonId = `service-soon-${index}`;

          return (
            <div className="admin-content-service" key={service.id}>
              <h5>Service {index + 1}</h5>

              <div className="admin-content-field">
                <label htmlFor={titleId}>
                  Service name <span aria-hidden="true">*</span>
                </label>
                <input
                  id={titleId}
                  value={service.title}
                  maxLength={SERVICE_TITLE_MAX}
                  aria-required="true"
                  aria-invalid={errors[titleId] ? "true" : undefined}
                  aria-describedby={`${titleId}-count${errors[titleId] ? ` ${titleId}-error` : ""}`}
                  onChange={(event) =>
                    setService(index, { title: event.target.value })
                  }
                />
                <small id={`${titleId}-count`} className="admin-content-counter">
                  {service.title.length} of {SERVICE_TITLE_MAX} characters
                </small>
                {errors[titleId] && (
                  <p id={`${titleId}-error`} className="admin-content-field-error">
                    {errors[titleId]}
                  </p>
                )}
              </div>

              <div className="admin-content-field">
                <label htmlFor={textId}>
                  Description <span aria-hidden="true">*</span>
                </label>
                <textarea
                  id={textId}
                  value={service.text}
                  rows={4}
                  maxLength={SERVICE_TEXT_MAX}
                  aria-required="true"
                  aria-invalid={errors[textId] ? "true" : undefined}
                  aria-describedby={`${textId}-count${errors[textId] ? ` ${textId}-error` : ""}`}
                  onChange={(event) =>
                    setService(index, { text: event.target.value })
                  }
                />
                <small id={`${textId}-count`} className="admin-content-counter">
                  {service.text.length} of {SERVICE_TEXT_MAX} characters
                </small>
                {errors[textId] && (
                  <p id={`${textId}-error`} className="admin-content-field-error">
                    {errors[textId]}
                  </p>
                )}
              </div>

              <label className="admin-content-radio" htmlFor={soonId}>
                <input
                  id={soonId}
                  type="checkbox"
                  checked={service.comingSoon === true}
                  onChange={(event) =>
                    setService(index, { comingSoon: event.target.checked })
                  }
                />
                Show &quot;In development&quot; instead of the &quot;Ask about
                this service&quot; link
              </label>

              <div className="admin-content-actions">
                <button
                  type="button"
                  className="danger"
                  disabled={services.length <= 1}
                  onClick={() => removeService(index)}
                >
                  Remove service
                  <span className="sr-only">
                    {" "}
                    {index + 1}: {service.title || "untitled"}
                  </span>
                </button>
              </div>
            </div>
          );
        })}

        {errors["service-add"] && (
          <p className="admin-content-field-error">{errors["service-add"]}</p>
        )}

        <div className="admin-content-actions">
          <button
            id="service-add"
            type="button"
            className="secondary"
            disabled={services.length >= SERVICES_MAX}
            onClick={addService}
          >
            Add a service
          </button>
        </div>
      </fieldset>
    );
  };

  return (
    <section className="admin-content-panel" aria-labelledby="content-pages-title">
      <h3 id="content-pages-title">Website pages</h3>
      <p className="admin-content-hint">
        Choose a page and change its wording. The design, photos and layout of
        each page stay exactly as they are, and every text box has a character
        limit. Visitors only see what has been published.
      </p>

      <div className="admin-content-messages">
        {error && (
          <div className="admin-content-alert error" role="alert">
            {error}
          </div>
        )}

        {errorList.length > 0 && (
          <div className="admin-content-alert error" role="alert">
            <p>Please fix the following before saving:</p>
            <ul>
              {errorList.map((message) => (
                <li key={message}>{message}</li>
              ))}
            </ul>
          </div>
        )}

        <div role="status" aria-live="polite" aria-atomic="true">
          {notice && (
            <div className="admin-content-alert success">{notice}</div>
          )}
        </div>
      </div>

      {loading ? (
        <p role="status">Loading website pages...</p>
      ) : pages.length === 0 ? (
        <p>No editable pages were found.</p>
      ) : (
        <div className="admin-content-editor-layout">
          <nav className="admin-content-page-list" aria-label="Editable pages">
            {pages.map((page) => (
              <button
                key={page.pageKey}
                type="button"
                className={page.pageKey === selectedKey ? "active" : ""}
                aria-current={page.pageKey === selectedKey ? "page" : undefined}
                onClick={() => choosePage(page.pageKey)}
              >
                {PAGE_LAYOUTS[page.pageKey].label}
                <small>
                  {!page.published
                    ? "Original wording"
                    : page.hasUnpublishedChanges
                      ? "Unpublished changes"
                      : "Published"}
                </small>
              </button>
            ))}
          </nav>

          <form
            className="admin-content-form"
            noValidate
            onSubmit={(event) => {
              event.preventDefault();
              void submit(true);
            }}
          >
            <h4>{layout.label}</h4>

            {selectedPage && (
              <p className="admin-content-status-line">
                {statusText(selectedPage)}{" "}
                <a href={layout.path} target="_blank" rel="noopener noreferrer">
                  View public page
                  <span className="sr-only"> (opens in a new tab)</span>
                </a>
              </p>
            )}

            {builtIn && (
              <p className="admin-content-hint">
                This page has not been edited before. Its current wording is
                shown below so you can change it.
              </p>
            )}

            {groups.map((group) => (
              <fieldset className="admin-content-fieldset" key={group.name}>
                <legend>{group.name}</legend>
                {group.slots.map(renderSlot)}
              </fieldset>
            ))}

            {layout.hasServices && renderServices()}

            {previewing && (
              <div
                className="admin-content-preview"
                role="region"
                aria-label="Preview of the page"
              >
                <p className="admin-content-preview-label">
                  Preview. This is the real page with your wording. Nothing is
                  saved yet.
                </p>
                <div className="admin-content-preview-frame">
                  <PagePreview pageKey={selectedKey} content={content} />
                </div>
              </div>
            )}

            <div className="admin-content-actions">
              <button
                type="button"
                className="secondary"
                aria-expanded={previewing}
                onClick={() => setPreviewing((open) => !open)}
              >
                {previewing ? "Hide preview" : "Preview"}
              </button>
              <button
                type="button"
                className="secondary"
                disabled={saving}
                onClick={restoreOriginal}
              >
                Restore original wording
              </button>
              <button
                type="button"
                className="secondary"
                disabled={saving}
                onClick={() => void submit(false)}
              >
                Save draft
              </button>
              <button type="submit" className="primary" disabled={saving}>
                {saving ? "Saving..." : "Publish"}
              </button>
            </div>
          </form>
        </div>
      )}
    </section>
  );
}

export default AdminContentPages;
