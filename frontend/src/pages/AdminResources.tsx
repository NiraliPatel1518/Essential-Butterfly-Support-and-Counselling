import { useCallback, useEffect, useRef, useState } from "react";
import {
  ApiError,
  fetchAdminResources,
  fetchUploadRules,
  removeResource,
  saveResource,
  type AdminResource,
  type UploadRules,
} from "../api/contentApi";
import { useAdminSession } from "../hooks/useAdminSession";
import { formatFileSize } from "../utils/contentFormat";
import { clearDraft, loadDraft, saveDraft } from "../utils/draftStorage";
import "./AdminContent.css";

type ResourceForm = {
  title: string;
  description: string;
  category: string;
  // Web links, one per line.
  linkUrls: string;
  altText: string;
  removeFile: boolean;
};

type FieldName =
  | "title"
  | "description"
  | "category"
  | "linkUrls"
  | "file"
  | "altText";

type FieldErrors = Partial<Record<FieldName, string>>;

type DuplicateWarning = {
  message: string;
  existing: AdminResource | null;
};

// Limits keep every resource row and popup looking the same.
const TITLE_MAX = 80;
const DESCRIPTION_MAX = 300;
const CATEGORY_MAX = 40;
const ALT_TEXT_MAX = 150;
const MAX_LINKS = 5;

// The order keyboard focus follows when several fields need attention.
const FIELD_ORDER: FieldName[] = [
  "title",
  "description",
  "category",
  "linkUrls",
  "file",
  "altText",
];

const EMPTY_FORM: ResourceForm = {
  title: "",
  description: "",
  category: "",
  linkUrls: "",
  altText: "",
  removeFile: false,
};

const draftKey = (resource: AdminResource | null) =>
  resource ? `resource:${resource.id}` : "resource:new";

const isImageName = (name: string) => /\.(png|jpe?g)$/i.test(name);

function formFor(resource: AdminResource | null): ResourceForm {
  if (!resource) return EMPTY_FORM;

  return {
    title: resource.title,
    description: resource.description,
    category: resource.category,
    linkUrls: resource.links.join("\n"),
    altText: resource.altText ?? "",
    removeFile: false,
  };
}

// One link per line; blank lines and repeats are ignored.
function parseLinks(text: string): string[] {
  const links: string[] = [];

  for (const line of text.split(/\r?\n/)) {
    const link = line.trim();

    if (link && !links.includes(link)) links.push(link);
  }

  return links;
}

// The same title (any capitalisation) or the same first link as another resource.
function findDuplicate(
  resources: AdminResource[],
  title: string,
  firstLink: string,
  ignoreId: number | null
): AdminResource | null {
  const wantedTitle = title.trim().toLowerCase();

  return (
    resources.find(
      (resource) =>
        resource.id !== ignoreId &&
        ((wantedTitle !== "" &&
          resource.title.trim().toLowerCase() === wantedTitle) ||
          (firstLink !== "" && resource.linkUrl === firstLink))
    ) ?? null
  );
}

// First line of defence in the browser. The server checks everything again.
function checkFile(file: File, rules: UploadRules | null): string {
  if (file.size === 0) {
    return "The selected file is empty. Please choose a different file.";
  }

  if (!rules) return "";

  const extension = file.name.split(".").pop()?.toLowerCase() ?? "";

  if (!rules.allowedExtensions.includes(extension)) {
    return `This file type is not supported. Please upload a ${rules.acceptedFormats} file.`;
  }

  if (file.size > rules.maxFileSizeBytes) {
    return `This file is too large. The maximum size is ${rules.maxFileSizeLabel}.`;
  }

  return "";
}

function AdminResources() {
  const handleSessionExpired = useAdminSession();

  const [resources, setResources] = useState<AdminResource[]>([]);
  const [rules, setRules] = useState<UploadRules | null>(null);
  const [editing, setEditing] = useState<AdminResource | null>(null);
  const [form, setForm] = useState<ResourceForm>(EMPTY_FORM);
  const [baseline, setBaseline] = useState<ResourceForm>(EMPTY_FORM);
  const [file, setFile] = useState<File | null>(null);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [duplicate, setDuplicate] = useState<DuplicateWarning | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const formRef = useRef<HTMLFormElement>(null);
  const fieldRefs = useRef<Partial<Record<FieldName, HTMLElement | null>>>({});
  const duplicateRef = useRef<HTMLDivElement>(null);

  const dirty = JSON.stringify(form) !== JSON.stringify(baseline);

  const existingFile = editing?.fileName ? editing : null;
  const keepsExistingFile = Boolean(existingFile) && !form.removeFile;
  const hasFile = file !== null || keepsExistingFile;

  const existingIsImage = Boolean(
    existingFile?.fileContentType?.startsWith("image/")
  );

  const fileIsImage = file ? isImageName(file.name) : existingIsImage;
  const altTextRequired = hasFile && fileIsImage;

  const categoryNames = Array.from(
    new Set(resources.map((resource) => resource.category))
  ).sort((a, b) => a.localeCompare(b));

  // Shows the form for a new resource (null) or for editing, restoring text
  // that was kept when a session ended.
  const openForm = useCallback((resource: AdminResource | null) => {
    const start = formFor(resource);
    const kept = loadDraft<ResourceForm>(draftKey(resource));

    setEditing(resource);
    setBaseline(start);
    setFile(null);
    setErrors({});
    setDuplicate(null);
    setError("");

    if (kept && JSON.stringify(kept) !== JSON.stringify(start)) {
      setForm(kept);
      setNotice(
        "We restored the details you had entered before you were signed out. Choose the file again if there was one, then save or publish."
      );
    } else {
      setForm(start);
      setNotice("");
    }
  }, []);

  const loadResources = useCallback(async () => {
    try {
      setResources(await fetchAdminResources());
    } catch (loadError) {
      if (!handleSessionExpired(loadError)) {
        setError(
          loadError instanceof Error
            ? loadError.message
            : "The resources could not be loaded."
        );
      }
    }
  }, [handleSessionExpired]);

  useEffect(() => {
    const start = async () => {
      setLoading(true);

      await loadResources();

      // The upload rules only help the form; the server enforces them anyway.
      try {
        setRules(await fetchUploadRules());
      } catch {
        setRules(null);
      }

      openForm(null);
      setLoading(false);
    };

    void start();
  }, [loadResources, openForm]);

  // Keep what has been typed for a short time, in case the sign-in expires.
  useEffect(() => {
    if (loading) return;

    if (dirty) {
      saveDraft(draftKey(editing), form);
    } else {
      clearDraft(draftKey(editing));
    }
  }, [dirty, editing, form, loading]);

  useEffect(() => {
    if (duplicate) duplicateRef.current?.focus();
  }, [duplicate]);

  const updateForm = (changes: Partial<ResourceForm>) => {
    setForm((current) => ({ ...current, ...changes }));
  };

  const startEditing = (resource: AdminResource) => {
    openForm(resource);

    const element = formRef.current;

    if (element && typeof element.scrollIntoView === "function") {
      element.scrollIntoView({ behavior: "smooth" });
    }

    fieldRefs.current.title?.focus();
  };

  const cancelEditing = () => {
    clearDraft(draftKey(editing));
    openForm(null);
  };

  const chooseFile = (chosen: File | null, input: HTMLInputElement) => {
    if (!chosen) {
      setFile(null);
      return;
    }

    const problem = checkFile(chosen, rules);

    if (problem) {
      // An unsupported or oversized file is not kept.
      input.value = "";
      setFile(null);
      setErrors((current) => ({ ...current, file: problem }));
      return;
    }

    setFile(chosen);
    setErrors((current) => ({ ...current, file: undefined }));
  };

  const validate = (): FieldErrors => {
    const found: FieldErrors = {};
    const links = parseLinks(form.linkUrls);

    if (!form.title.trim()) found.title = "Resource title is required.";

    if (!form.description.trim()) {
      found.description = "Short description is required.";
    }

    if (!form.category.trim()) found.category = "Category is required.";

    if (links.length > MAX_LINKS) {
      found.linkUrls = `You can add up to ${MAX_LINKS} links.`;
    } else if (links.some((link) => !/^https?:\/\/\S+$/i.test(link))) {
      found.linkUrls =
        "Every link must be a web address, for example https://example.org/guide.";
    } else if (links.length === 0 && !hasFile) {
      found.linkUrls =
        "Add at least one link, or attach a document or image.";
    }

    if (altTextRequired && !form.altText.trim()) {
      found.altText =
        "Alternative text is required for images. Describe the image for people who cannot see it.";
    }

    return found;
  };

  const submit = async (publish: boolean) => {
    setNotice("");
    setError("");
    setDuplicate(null);

    const found = validate();
    setErrors(found);

    // Keyboard focus moves to the first field that needs attention.
    const firstInvalid = FIELD_ORDER.find((name) => found[name]);

    if (firstInvalid) {
      fieldRefs.current[firstInvalid]?.focus();
      return;
    }

    const links = parseLinks(form.linkUrls);
    const firstLink = links[0] ?? "";
    const match = findDuplicate(
      resources,
      form.title,
      firstLink,
      editing?.id ?? null
    );

    if (match) {
      setDuplicate({
        message: `A resource with the same title or link already exists: "${match.title}".`,
        existing: match,
      });
      return;
    }

    setSaving(true);

    try {
      const saved = await saveResource(
        editing?.id ?? null,
        {
          title: form.title.trim(),
          description: form.description.trim(),
          category: form.category.trim(),
          altText: hasFile && fileIsImage ? form.altText.trim() : "",
          linkUrls: links.join("\n"),
          removeFile: form.removeFile,
          publish,
        },
        file
      );

      clearDraft(draftKey(editing));
      openForm(null);
      setNotice(
        publish
          ? `"${saved.title}" is published in the Resource Library.`
          : `"${saved.title}" was saved as a draft. Visitors cannot see it yet.`
      );
      await loadResources();
    } catch (saveError) {
      // The entered details stay on screen and are kept for after sign-in.
      if (saveError instanceof ApiError && saveError.status === 401) {
        saveDraft(draftKey(editing), form);
      }

      if (handleSessionExpired(saveError)) return;

      if (saveError instanceof ApiError && saveError.status === 409) {
        setDuplicate({
          message: saveError.message,
          existing: findDuplicate(
            resources,
            form.title,
            firstLink,
            editing?.id ?? null
          ),
        });
      } else {
        setError(
          saveError instanceof Error
            ? saveError.message
            : "We could not save this resource. Please try again."
        );
      }
    } finally {
      setSaving(false);
    }
  };

  const remove = async (resource: AdminResource) => {
    if (!window.confirm(`Remove "${resource.title}" from the Resource Library?`)) {
      return;
    }

    setNotice("");
    setError("");

    try {
      await removeResource(resource.id);

      if (editing?.id === resource.id) {
        clearDraft(draftKey(editing));
        openForm(null);
      }

      setNotice(`"${resource.title}" was removed.`);
      await loadResources();
    } catch (removeError) {
      if (!handleSessionExpired(removeError)) {
        setError(
          removeError instanceof Error
            ? removeError.message
            : "We could not remove this resource. Please try again."
        );
      }
    }
  };

  const setFieldRef =
    (name: FieldName) =>
    (element: HTMLElement | null) => {
      fieldRefs.current[name] = element;
    };

  const describedBy = (name: FieldName, ...extra: string[]) =>
    [...extra, errors[name] ? `resource-${name}-error` : undefined]
      .filter(Boolean)
      .join(" ") || undefined;

  const fieldError = (name: FieldName) =>
    errors[name] ? (
      <p id={`resource-${name}-error`} className="admin-content-field-error">
        {errors[name]}
      </p>
    ) : null;

  const counter = (id: string, length: number, max: number) => (
    <small id={id} className="admin-content-counter">
      {length} of {max} characters
    </small>
  );

  const errorList = FIELD_ORDER.map((name) => errors[name]).filter(Boolean);
  const linkCount = parseLinks(form.linkUrls).length;

  return (
    <section className="admin-content-panel" aria-labelledby="content-resources-title">
      <h3 id="content-resources-title">Resource Library</h3>
      <p className="admin-content-hint">
        Each resource shows on the public website as a row with its title, a
        photo (if you attach an image) and a short description. Visitors click
        &quot;Read more&quot; to see the full description and the links. Only
        published resources appear.
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

        {duplicate && (
          <div
            ref={duplicateRef}
            className="admin-content-alert warning"
            role="alert"
            tabIndex={-1}
          >
            <p>{duplicate.message}</p>
            <p>You can edit the existing resource instead, or cancel.</p>
            <div className="admin-content-actions">
              {duplicate.existing && (
                <button
                  type="button"
                  className="secondary"
                  onClick={() => startEditing(duplicate.existing as AdminResource)}
                >
                  Edit the existing resource
                </button>
              )}
              <button
                type="button"
                className="secondary"
                onClick={() => setDuplicate(null)}
              >
                Cancel
              </button>
            </div>
          </div>
        )}

        <div role="status" aria-live="polite" aria-atomic="true">
          {notice && (
            <div className="admin-content-alert success">{notice}</div>
          )}
        </div>
      </div>

      <form
        ref={formRef}
        className="admin-content-form"
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          void submit(true);
        }}
      >
        <h4>{editing ? `Edit resource: ${editing.title}` : "Add Resource"}</h4>

        <div className="admin-content-field">
          <label htmlFor="resource-title">
            Resource title <span aria-hidden="true">*</span>
          </label>
          <input
            id="resource-title"
            ref={setFieldRef("title")}
            value={form.title}
            maxLength={TITLE_MAX}
            aria-required="true"
            aria-invalid={errors.title ? "true" : undefined}
            aria-describedby={describedBy("title", "resource-title-count")}
            onChange={(event) => updateForm({ title: event.target.value })}
          />
          {counter("resource-title-count", form.title.length, TITLE_MAX)}
          {fieldError("title")}
        </div>

        <div className="admin-content-field">
          <label htmlFor="resource-description">
            Short description <span aria-hidden="true">*</span>
          </label>
          <textarea
            id="resource-description"
            ref={setFieldRef("description")}
            value={form.description}
            rows={4}
            maxLength={DESCRIPTION_MAX}
            aria-required="true"
            aria-invalid={errors.description ? "true" : undefined}
            aria-describedby={describedBy("description", "resource-description-count")}
            onChange={(event) => updateForm({ description: event.target.value })}
          />
          {counter("resource-description-count", form.description.length, DESCRIPTION_MAX)}
          {fieldError("description")}
        </div>

        <div className="admin-content-field">
          <label htmlFor="resource-category">
            Category <span aria-hidden="true">*</span>
          </label>
          <input
            id="resource-category"
            ref={setFieldRef("category")}
            value={form.category}
            maxLength={CATEGORY_MAX}
            list="resource-category-options"
            aria-required="true"
            aria-invalid={errors.category ? "true" : undefined}
            aria-describedby={describedBy("category", "resource-category-help", "resource-category-count")}
            onChange={(event) => updateForm({ category: event.target.value })}
          />
          <datalist id="resource-category-options">
            {categoryNames.map((name) => (
              <option key={name} value={name} />
            ))}
          </datalist>
          <small id="resource-category-help">
            Choose an existing category or type a new one.
          </small>
          {counter("resource-category-count", form.category.length, CATEGORY_MAX)}
          {fieldError("category")}
        </div>

        <div className="admin-content-field">
          <label htmlFor="resource-links">
            Links (one per line, up to {MAX_LINKS}){" "}
            {!hasFile && <span aria-hidden="true">*</span>}
          </label>
          <textarea
            id="resource-links"
            ref={setFieldRef("linkUrls")}
            value={form.linkUrls}
            rows={4}
            inputMode="url"
            maxLength={MAX_LINKS * 2100}
            aria-required={hasFile ? undefined : "true"}
            aria-invalid={errors.linkUrls ? "true" : undefined}
            aria-describedby={describedBy("linkUrls", "resource-links-help")}
            onChange={(event) => updateForm({ linkUrls: event.target.value })}
          />
          <small id="resource-links-help">
            For example https://example.org/guide. {linkCount} of {MAX_LINKS}{" "}
            links added. These appear in the &quot;Read more&quot; popup.
          </small>
          {fieldError("linkUrls")}
        </div>

        <div className="admin-content-field">
          <label htmlFor="resource-file">
            Photo or document (optional if you added a link)
          </label>
          <input
            id="resource-file"
            ref={setFieldRef("file")}
            type="file"
            accept={
              rules
                ? rules.allowedExtensions.map((ext) => `.${ext}`).join(",")
                : undefined
            }
            aria-invalid={errors.file ? "true" : undefined}
            aria-describedby={describedBy("file", "resource-file-help")}
            onChange={(event) =>
              chooseFile(event.target.files?.[0] ?? null, event.target)
            }
          />
          <small id="resource-file-help">
            {rules
              ? `Accepted: ${rules.acceptedFormats}. Maximum size ${rules.maxFileSizeLabel}. An image is shown as the photo in the resource's row.`
              : "Choose a PDF, DOCX, PNG or JPEG file. An image is shown as the photo in the resource's row."}
          </small>
          {existingFile && !file && (
            <p className="admin-content-hint">
              Current file: {existingFile.fileName}
              {existingFile.fileSize !== null &&
                ` (${formatFileSize(existingFile.fileSize)})`}
              . Choose a new file only to replace it.
            </p>
          )}
          {fieldError("file")}
        </div>

        {existingFile && !file && (
          <label className="admin-content-radio" htmlFor="resource-remove-file">
            <input
              id="resource-remove-file"
              type="checkbox"
              checked={form.removeFile}
              onChange={(event) =>
                updateForm({ removeFile: event.target.checked })
              }
            />
            Remove the current file from this resource
          </label>
        )}

        {hasFile && (
          <div className="admin-content-field">
            <label htmlFor="resource-alt">
              Alternative text{" "}
              {altTextRequired ? (
                <span aria-hidden="true">*</span>
              ) : (
                <span>(optional)</span>
              )}
            </label>
            <input
              id="resource-alt"
              ref={setFieldRef("altText")}
              value={form.altText}
              maxLength={ALT_TEXT_MAX}
              aria-required={altTextRequired ? "true" : undefined}
              aria-invalid={errors.altText ? "true" : undefined}
              aria-describedby={describedBy("altText", "resource-alt-help", "resource-alt-count")}
              onChange={(event) => updateForm({ altText: event.target.value })}
            />
            <small id="resource-alt-help">
              Required for images. Describe what the image shows for people
              who cannot see it.
            </small>
            {counter("resource-alt-count", form.altText.length, ALT_TEXT_MAX)}
            {fieldError("altText")}
          </div>
        )}

        {editing?.status === "PUBLISHED" && (
          <p className="admin-content-hint">
            This resource is published. &quot;Save as draft&quot; removes it
            from the public library until you publish it again.
          </p>
        )}

        <div className="admin-content-actions">
          {editing && (
            <button
              type="button"
              className="secondary"
              disabled={saving}
              onClick={cancelEditing}
            >
              Cancel editing
            </button>
          )}
          <button
            type="button"
            className="secondary"
            disabled={saving}
            onClick={() => void submit(false)}
          >
            Save as draft
          </button>
          <button type="submit" className="primary" disabled={saving}>
            {saving ? "Saving..." : "Publish"}
          </button>
        </div>
      </form>

      <section className="admin-content-list" aria-labelledby="existing-resources-title">
        <h4 id="existing-resources-title">
          Existing resources ({resources.length})
        </h4>

        {loading ? (
          <p role="status">Loading resources...</p>
        ) : resources.length === 0 ? (
          <p>No resources have been added yet.</p>
        ) : (
          <ul>
            {resources.map((resource) => (
              <li key={resource.id} className="admin-content-item">
                <div>
                  <span
                    className={`admin-content-badge ${resource.status.toLowerCase()}`}
                  >
                    {resource.status === "PUBLISHED" ? "Published" : "Draft"}
                  </span>
                  <h5>{resource.title}</h5>
                  <p>{resource.description}</p>
                  <small>
                    {resource.category} ·{" "}
                    {resource.links.length > 0
                      ? `${resource.links.length} ${resource.links.length === 1 ? "link" : "links"}`
                      : "No links"}
                    {resource.fileName ? ` · File: ${resource.fileName}` : ""}
                  </small>
                </div>
                <div className="admin-content-item-actions">
                  <button
                    type="button"
                    className="secondary"
                    onClick={() => startEditing(resource)}
                  >
                    Edit
                    <span className="sr-only"> {resource.title}</span>
                  </button>
                  <button
                    type="button"
                    className="danger"
                    onClick={() => void remove(resource)}
                  >
                    Remove
                    <span className="sr-only"> {resource.title}</span>
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </section>
  );
}

export default AdminResources;
