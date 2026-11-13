import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { ChangeEvent, FormEvent, KeyboardEvent, ReactNode } from "react";
import { Link } from "react-router-dom";
import {
  ApiError,
  deletePhoto,
  fetchAdminImage,
  fetchOverview,
  savePhotoDescription,
  saveSelection,
  uploadPhoto,
} from "../api/memoryWallApi";
import type { AdminOverview, AdminPhoto, PhotoStatus } from "../api/memoryWallApi";
import { useAdminSession } from "../hooks/useAdminSession";
import { FRIENDLY_SERVER_ERROR } from "../api/contentApi";
import { parseLocal } from "../utils/appointmentFormat";
import "./Dashboard.css";
import "./AdminMemoryWall.css";

const STATUS_LABELS: Record<PhotoStatus, { label: string; symbol: string }> = {
  PENDING_REVIEW: { label: "Waiting for review", symbol: "◔" },
  PUBLISHED: { label: "Published", symbol: "✓" },
  UNPUBLISHED: { label: "Unpublished", symbol: "⊘" },
};

type Filter = "ALL" | PhotoStatus;

function messageOf(error: unknown): string {
  return error instanceof ApiError ? error.message : FRIENDLY_SERVER_ERROR;
}

function uploadedLabel(value: string): string {
  return parseLocal(value).toLocaleString("en-CA", {
    dateStyle: "medium",
    timeStyle: "short",
  });
}

function sizeLabel(bytes: number): string {
  if (bytes < 1024 * 1024) {
    return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  }

  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

// A pop-up window (native <dialog>): focus stays inside and Escape closes it.
function Modal({
  title,
  labelId,
  onClose,
  onKeyDown,
  children,
}: {
  title: string;
  labelId: string;
  onClose: () => void;
  onKeyDown?: (event: KeyboardEvent<HTMLDialogElement>) => void;
  children: ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;

    if (dialog && !dialog.open) {
      dialog.showModal();
    }
  }, []);

  return (
    <dialog
      ref={ref}
      className="mw-modal"
      aria-labelledby={labelId}
      onClose={onClose}
      onKeyDown={onKeyDown}
    >
      <div className="mw-modal-head">
        <h2 id={labelId}>{title}</h2>

        <button
          type="button"
          className="mw-secondary"
          onClick={() => ref.current?.close()}
        >
          Close
        </button>
      </div>

      {children}
    </dialog>
  );
}

// The administrator's pictures need the sign-in header, so each one is fetched
// and shown from a temporary address that is released when it goes away.
function AdminPhotoImage({
  id,
  alt,
  className,
  onSessionError,
}: {
  id: number;
  alt: string;
  className?: string;
  onSessionError: (error: unknown) => void;
}) {
  const [source, setSource] = useState("");
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    let address = "";

    setSource("");
    setFailed(false);

    fetchAdminImage(id)
      .then((url) => {
        if (cancelled) {
          URL.revokeObjectURL(url);
          return;
        }

        address = url;
        setSource(url);
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setFailed(true);
          onSessionError(error);
        }
      });

    return () => {
      cancelled = true;

      if (address) URL.revokeObjectURL(address);
    };
  }, [id, onSessionError]);

  if (failed) {
    return <span className="mw-image-missing">Photo could not be loaded</span>;
  }

  if (!source) {
    return <span className="mw-image-loading">Loading…</span>;
  }

  return <img src={source} alt={alt} className={className} />;
}

function AdminMemoryWall() {
  const handleExpiredSession = useAdminSession();

  const [overview, setOverview] = useState<AdminOverview | null>(null);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const [filter, setFilter] = useState<Filter>("ALL");
  const [newestFirst, setNewestFirst] = useState(true);

  const [uploading, setUploading] = useState("");
  const [uploadProblems, setUploadProblems] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);

  const [previewId, setPreviewId] = useState<number | null>(null);
  const [description, setDescription] = useState("");
  const [descriptionError, setDescriptionError] = useState("");
  const [savingDescription, setSavingDescription] = useState(false);

  const [deleting, setDeleting] = useState<AdminPhoto | null>(null);
  const [confirmingSave, setConfirmingSave] = useState(false);

  const fileInput = useRef<HTMLInputElement>(null);
  const openerRef = useRef<HTMLElement | null>(null);

  // Stable, so a photo's image is not fetched again on every change.
  const onSessionError = useCallback(
    (caught: unknown) => {
      handleExpiredSession(caught);
    },
    [handleExpiredSession]
  );

  const fail = useCallback(
    (caught: unknown) => {
      if (handleExpiredSession(caught)) return;

      setNotice("");
      setError(messageOf(caught));
    },
    [handleExpiredSession]
  );

  // keepSelection: keep the boxes ticked now (an upload must not undo them).
  const apply = useCallback((data: AdminOverview, keepSelection: boolean) => {
    setOverview(data);

    setSelected((previous) => {
      const ids = new Set(data.photos.map((photo) => photo.id));

      if (keepSelection) {
        return new Set([...previous].filter((id) => ids.has(id)));
      }

      return new Set(
        data.photos
          .filter((photo) => photo.status === "PUBLISHED")
          .map((photo) => photo.id)
      );
    });
  }, []);

  const load = useCallback(
    async (keepSelection: boolean) => {
      setLoading(true);
      setError("");

      try {
        apply(await fetchOverview(), keepSelection);
      } catch (caught) {
        fail(caught);
      } finally {
        setLoading(false);
      }
    },
    [apply, fail]
  );

  useEffect(() => {
    void load(false);
  }, [load]);

  const photos = useMemo(() => overview?.photos ?? [], [overview]);
  const summary = overview?.summary;

  const published = useMemo(
    () =>
      new Set(
        photos.filter((photo) => photo.status === "PUBLISHED").map((p) => p.id)
      ),
    [photos]
  );

  const toAdd = [...selected].filter((id) => !published.has(id));
  const toRemove = [...published].filter((id) => !selected.has(id));
  const dirty = toAdd.length + toRemove.length > 0;

  const maxPublished = summary?.maxPublishedPhotos ?? 120;
  const atLimit = selected.size >= maxPublished;

  const shown = useMemo(() => {
    const filtered =
      filter === "ALL"
        ? photos
        : photos.filter((photo) => photo.status === filter);

    // the list arrives newest first
    return newestFirst ? filtered : [...filtered].reverse();
  }, [photos, filter, newestFirst]);

  const previewIndex = shown.findIndex((photo) => photo.id === previewId);
  const preview = previewIndex >= 0 ? shown[previewIndex] : null;

  // ------------------------------------------------------------- uploading

  const onFiles = async (event: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files ?? []);

    event.target.value = "";

    if (files.length === 0 || !summary) return;

    setNotice("");
    setError("");
    setUploadProblems([]);

    if (files.length > summary.remainingUploads) {
      setUploadProblems([
        summary.remainingUploads === 0
          ? `The Memory Wall already holds the maximum of ${summary.maxPhotos} photos. Delete some photos below before adding more.`
          : `You can add ${summary.remainingUploads} more photo${
              summary.remainingUploads === 1 ? "" : "s"
            } (the limit is ${summary.maxPhotos}), but you chose ${files.length}. Choose fewer photos.`,
      ]);
      return;
    }

    const problems: string[] = [];
    let added = 0;

    for (let index = 0; index < files.length; index++) {
      setUploading(`Uploading photo ${index + 1} of ${files.length}…`);

      try {
        await uploadPhoto(files[index]);
        added++;
      } catch (caught) {
        if (handleExpiredSession(caught)) return;

        problems.push(`${files[index].name}: ${messageOf(caught)}`);
      }
    }

    setUploading("");
    setUploadProblems(problems);

    if (added > 0) {
      setNotice(
        `${added} photo${added === 1 ? "" : "s"} added. New photos are not public until you tick them and choose Save changes.`
      );
    }

    await load(true);
  };

  // ------------------------------------------------------------- selecting

  const toggle = (id: number) => {
    setNotice("");

    setSelected((previous) => {
      const next = new Set(previous);

      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }

      return next;
    });
  };

  const discard = () => {
    setSelected(new Set(published));
    setNotice("");
  };

  const save = async () => {
    setConfirmingSave(false);
    setSaving(true);
    setError("");
    setNotice("");

    try {
      apply(await saveSelection([...selected]), false);

      setNotice(
        "Changes saved. The public Memory Wall now shows exactly the photos you ticked."
      );
    } catch (caught) {
      fail(caught);
    } finally {
      setSaving(false);
    }
  };

  const askToSave = () => {
    if (toRemove.length > 0) {
      setConfirmingSave(true);
    } else {
      void save();
    }
  };

  // ------------------------------------------------------------- previewing

  const openPreview = (photo: AdminPhoto, button: HTMLElement) => {
    openerRef.current = button;
    setDescription(photo.description ?? "");
    setDescriptionError("");
    setPreviewId(photo.id);
  };

  const stepPreview = (direction: number) => {
    if (shown.length === 0) return;

    const next = shown[(previewIndex + direction + shown.length) % shown.length];

    setDescription(next.description ?? "");
    setDescriptionError("");
    setPreviewId(next.id);
  };

  const onPreviewKeys = (event: KeyboardEvent<HTMLDialogElement>) => {
    // arrows inside the description box must keep moving the text cursor
    if ((event.target as HTMLElement).tagName === "INPUT") return;

    if (event.key === "ArrowRight") {
      event.preventDefault();
      stepPreview(1);
    } else if (event.key === "ArrowLeft") {
      event.preventDefault();
      stepPreview(-1);
    }
  };

  const closePreview = () => {
    setPreviewId(null);
    openerRef.current?.focus();
  };

  const submitDescription = async (event: FormEvent) => {
    event.preventDefault();

    if (!preview) return;

    setSavingDescription(true);
    setDescriptionError("");

    try {
      await savePhotoDescription(preview.id, description);
      await load(true);

      setNotice("Description saved.");
    } catch (caught) {
      if (handleExpiredSession(caught)) return;

      setDescriptionError(messageOf(caught));
    } finally {
      setSavingDescription(false);
    }
  };

  // -------------------------------------------------------------- deleting

  const confirmDelete = async () => {
    if (!deleting) return;

    const target = deleting;

    setDeleting(null);
    setError("");
    setNotice("");

    try {
      await deletePhoto(target.id);
      await load(true);

      setNotice("Photo deleted. Your available space has been updated.");
    } catch (caught) {
      fail(caught);
    }
  };

  const deleteMessage = (photo: AdminPhoto): string => {
    const parts = [
      photo.source === "TESTIMONIAL"
        ? "This photo will be removed from the Memory Wall. It stays with the client's testimonial."
        : "This photo will be permanently deleted. This cannot be undone.",
    ];

    if (photo.status === "PUBLISHED") {
      parts.push("It will also disappear from the public Memory Wall.");
    }

    return parts.join(" ");
  };

  return (
    <main className="dashboard-page mw-admin">
      <div className="dashboard-container">
        <p>
          <Link to="/admin-dashboard">← Back to the admin dashboard</Link>
        </p>

        <header className="dashboard-header">
          <div>
            <p className="dashboard-eyebrow">ESSENTIAL BUTTERFLY</p>
            <h1>Manage Memory Wall</h1>
            <p className="dashboard-description">
              Every photo uploaded to the website is here. Tick the photos that
              should appear on the public Memory Wall, then choose Save
              changes.
            </p>
          </div>

          <button
            type="button"
            className="dashboard-refresh"
            onClick={() => void load(false)}
            disabled={loading}
          >
            {loading ? "Refreshing…" : "Refresh"}
          </button>
        </header>

        <div aria-live="polite" className="mw-live">
          {uploading && <p className="mw-banner">{uploading}</p>}

          {notice && (
            <p className="mw-banner mw-banner-ok" role="status">
              <span aria-hidden="true">✓ </span>
              {notice}
            </p>
          )}
        </div>

        {error && (
          <p className="mw-banner mw-banner-error" role="alert">
            <span aria-hidden="true">✕ </span>
            {error}
          </p>
        )}

        {uploadProblems.length > 0 && (
          <div className="mw-banner mw-banner-error" role="alert">
            <p>
              <span aria-hidden="true">✕ </span>
              Some photos were not added:
            </p>
            <ul>
              {uploadProblems.map((problem) => (
                <li key={problem}>{problem}</li>
              ))}
            </ul>
          </div>
        )}

        {summary && (
          <section className="mw-panel" aria-labelledby="mw-summary-title">
            <h2 id="mw-summary-title">Photos and space</h2>

            <dl className="mw-stats">
              <div>
                <dt>Photos uploaded</dt>
                <dd>
                  {summary.totalPhotos} of {summary.maxPhotos}
                </dd>
              </div>
              <div>
                <dt>Space left for new photos</dt>
                <dd>{summary.remainingUploads}</dd>
              </div>
              <div>
                <dt>Ticked to show on the website</dt>
                <dd>
                  {selected.size} of {summary.maxPublishedPhotos}
                </dd>
              </div>
              <div>
                <dt>Showing on the website now</dt>
                <dd>{summary.publishedPhotos}</dd>
              </div>
              <div>
                <dt>Storage used</dt>
                <dd>{sizeLabel(summary.storageUsedBytes)}</dd>
              </div>
            </dl>

            <div className="mw-upload">
              <input
                id="mw-file"
                ref={fileInput}
                type="file"
                multiple
                className="mw-file-input"
                accept={summary.allowedExtensions.map((e) => `.${e}`).join(",")}
                disabled={Boolean(uploading) || summary.remainingUploads === 0}
                aria-describedby="mw-upload-help"
                onChange={(event) => void onFiles(event)}
              />

              <label htmlFor="mw-file" className="mw-primary mw-upload-button">
                Add Photos
              </label>

              <p id="mw-upload-help" className="mw-muted">
                {summary.acceptedFormats} photos, up to {summary.maxFileSizeLabel}{" "}
                each. New photos wait for your review and are never public
                until you tick them and save.
                {summary.remainingUploads === 0 &&
                  " The wall is full: delete a photo below to add another."}
              </p>
            </div>
          </section>
        )}

        <section className="mw-panel" aria-labelledby="mw-photos-title">
          <h2 id="mw-photos-title">All photos</h2>

          <div className="mw-toolbar">
            <div>
              <label htmlFor="mw-filter">Show</label>
              <select
                id="mw-filter"
                value={filter}
                onChange={(event) => setFilter(event.target.value as Filter)}
              >
                <option value="ALL">All photos</option>
                <option value="PENDING_REVIEW">Waiting for review</option>
                <option value="PUBLISHED">Published</option>
                <option value="UNPUBLISHED">Unpublished</option>
              </select>
            </div>

            <div>
              <label htmlFor="mw-sort">Order</label>
              <select
                id="mw-sort"
                value={newestFirst ? "new" : "old"}
                onChange={(event) => setNewestFirst(event.target.value === "new")}
              >
                <option value="new">Newest upload first</option>
                <option value="old">Oldest upload first</option>
              </select>
            </div>
          </div>

          {atLimit && (
            <p className="mw-banner mw-banner-warn" role="status">
              <span aria-hidden="true">⚠ </span>
              You have ticked the maximum of {maxPublished} photos. Untick a
              photo to choose another.
            </p>
          )}

          {!loading && shown.length === 0 && (
            <p className="mw-muted">
              {photos.length === 0
                ? "No photos yet. Choose Add Photos to upload the first one."
                : "No photos match this filter."}
            </p>
          )}

          <ul className="mw-grid">
            {shown.map((photo) => {
              const checked = selected.has(photo.id);
              const status = STATUS_LABELS[photo.status];
              const locked =
                !checked && (!photo.canPublish || atLimit);
              const change = checked && !published.has(photo.id)
                ? "Will be added when you save."
                : !checked && published.has(photo.id)
                  ? "Will be removed when you save."
                  : "";

              return (
                <li key={photo.id} className="mw-card">
                  <button
                    type="button"
                    className="mw-thumb"
                    aria-label={`Preview and enlarge photo: ${photo.altText}`}
                    onClick={(event) => openPreview(photo, event.currentTarget)}
                  >
                    <AdminPhotoImage
                      id={photo.id}
                      alt={photo.altText}
                      onSessionError={onSessionError}
                    />
                  </button>

                  <div className="mw-card-body">
                    <label className="mw-check" htmlFor={`mw-check-${photo.id}`}>
                      <input
                        id={`mw-check-${photo.id}`}
                        type="checkbox"
                        checked={checked}
                        disabled={locked}
                        onChange={() => toggle(photo.id)}
                      />
                      <span>
                        Show on the Memory Wall
                        <span className="sr-only">
                          {" "}
                          (photo uploaded {uploadedLabel(photo.uploadedAt)})
                        </span>
                      </span>
                    </label>

                    <p className={`mw-status mw-status-${photo.status}`}>
                      <span aria-hidden="true">{status.symbol} </span>
                      <span className="sr-only">Status: </span>
                      {status.label}
                    </p>

                    <p className="mw-meta">
                      Uploaded {uploadedLabel(photo.uploadedAt)}
                      <br />
                      By {photo.uploadedBy}
                    </p>

                    {photo.publishBlockedReason && !checked && (
                      <p className="mw-muted">{photo.publishBlockedReason}</p>
                    )}

                    {change && <p className="mw-change">{change}</p>}

                    <button
                      type="button"
                      className="mw-danger"
                      onClick={(event) => {
                        openerRef.current = event.currentTarget;
                        setDeleting(photo);
                      }}
                    >
                      Delete
                      <span className="sr-only">
                        {" "}
                        photo uploaded {uploadedLabel(photo.uploadedAt)}
                      </span>
                    </button>
                  </div>
                </li>
              );
            })}
          </ul>
        </section>

        {dirty && (
          <div className="mw-savebar" role="region" aria-label="Unsaved changes">
            <p>
              <strong>Unsaved changes:</strong> {toAdd.length} to add,{" "}
              {toRemove.length} to remove.
            </p>

            <div>
              <button
                type="button"
                className="mw-primary"
                disabled={saving}
                onClick={askToSave}
              >
                {saving ? "Saving…" : "Save changes"}
              </button>

              <button
                type="button"
                className="mw-secondary"
                disabled={saving}
                onClick={discard}
              >
                Discard changes
              </button>
            </div>
          </div>
        )}
      </div>

      {preview && (
        <Modal
          title="Photo preview"
          labelId="mw-preview-title"
          onClose={closePreview}
          onKeyDown={onPreviewKeys}
        >
          <div className="mw-preview-image">
            <AdminPhotoImage
              id={preview.id}
              alt={preview.altText}
              onSessionError={onSessionError}
            />
          </div>

          <p className="mw-meta">
            {STATUS_LABELS[preview.status].symbol}{" "}
            {STATUS_LABELS[preview.status].label} · Uploaded{" "}
            {uploadedLabel(preview.uploadedAt)} by {preview.uploadedBy} ·{" "}
            {sizeLabel(preview.sizeBytes)}
          </p>

          <form onSubmit={(event) => void submitDescription(event)} noValidate>
            <label htmlFor="mw-description">
              Description (read aloud by screen readers)
            </label>

            <input
              id="mw-description"
              type="text"
              maxLength={200}
              value={description}
              aria-invalid={descriptionError ? true : undefined}
              aria-describedby={descriptionError ? "mw-description-error" : undefined}
              onChange={(event) => setDescription(event.target.value)}
            />

            {descriptionError && (
              <p id="mw-description-error" className="mw-error" role="alert">
                {descriptionError}
              </p>
            )}

            <div className="mw-modal-actions">
              <button
                type="submit"
                className="mw-primary"
                disabled={savingDescription}
              >
                {savingDescription ? "Saving…" : "Save description"}
              </button>

              <button
                type="button"
                className="mw-secondary"
                onClick={() => stepPreview(-1)}
                disabled={shown.length < 2}
              >
                ← Previous
              </button>

              <button
                type="button"
                className="mw-secondary"
                onClick={() => stepPreview(1)}
                disabled={shown.length < 2}
              >
                Next →
              </button>
            </div>
          </form>
        </Modal>
      )}

      {deleting && (
        <Modal
          title="Delete this photo?"
          labelId="mw-delete-title"
          onClose={() => {
            setDeleting(null);
            openerRef.current?.focus();
          }}
        >
          <p>{deleteMessage(deleting)}</p>

          <div className="mw-modal-actions">
            <button
              type="button"
              className="mw-danger"
              onClick={() => void confirmDelete()}
            >
              Yes, delete
            </button>
          </div>
        </Modal>
      )}

      {confirmingSave && (
        <Modal
          title="Remove photos from the website?"
          labelId="mw-save-title"
          onClose={() => setConfirmingSave(false)}
        >
          <p>
            Saving will take {toRemove.length} photo
            {toRemove.length === 1 ? "" : "s"} off the public Memory Wall
            {toAdd.length > 0
              ? ` and add ${toAdd.length} new photo${toAdd.length === 1 ? "" : "s"}`
              : ""}
            . The photos are kept here, and you can show them again later.
          </p>

          <div className="mw-modal-actions">
            <button
              type="button"
              className="mw-primary"
              onClick={() => void save()}
            >
              Yes, save changes
            </button>
          </div>
        </Modal>
      )}
    </main>
  );
}

export default AdminMemoryWall;
