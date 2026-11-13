import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { KeyboardEvent } from "react";
import { ApiError, fetchWall, publicImageUrl } from "../api/memoryWallApi";
import type { PublicPhoto, WallMonth } from "../api/memoryWallApi";
import "./MemoryWall.css";

// The public Memory Wall: published photos, grouped by month of upload.
function MemoryWall() {
  const [months, setMonths] = useState<WallMonth[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // index into the flat list of every photo, or -1 when no photo is open
  const [openIndex, setOpenIndex] = useState(-1);

  const dialogRef = useRef<HTMLDialogElement>(null);
  const openerRef = useRef<HTMLElement | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      setMonths(await fetchWall());
    } catch (caught) {
      setError(
        caught instanceof ApiError
          ? caught.message
          : "We could not load the Memory Wall right now. Please try again."
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const flat = useMemo(
    () =>
      months.flatMap((month) =>
        month.photos.map((photo) => ({ photo, month: month.label }))
      ),
    [months]
  );

  const current = openIndex >= 0 ? flat[openIndex] : undefined;

  useEffect(() => {
    const dialog = dialogRef.current;

    if (!dialog || typeof dialog.showModal !== "function") return;

    if (current && !dialog.open) {
      dialog.showModal();
    }
  }, [current]);

  const openPhoto = (photo: PublicPhoto, button: HTMLElement) => {
    openerRef.current = button;
    setOpenIndex(flat.findIndex((item) => item.photo.id === photo.id));
  };

  const step = (direction: number) => {
    if (flat.length === 0) return;

    setOpenIndex((index) => (index + direction + flat.length) % flat.length);
  };

  // Escape is handled by the dialog itself; the arrow keys move between photos.
  const onKeyDown = (event: KeyboardEvent<HTMLDialogElement>) => {
    if (event.key === "ArrowRight") {
      event.preventDefault();
      step(1);
    } else if (event.key === "ArrowLeft") {
      event.preventDefault();
      step(-1);
    }
  };

  const afterClose = () => {
    setOpenIndex(-1);
    openerRef.current?.focus();
  };

  return (
    <main className="wall-page">
      <header className="wall-header">
        <p className="wall-eyebrow">ESSENTIAL BUTTERFLY</p>
        <h1>Memory Wall</h1>
        <p>
          Happy moments shared by the families and community we are proud to
          support. Select any photo to see it larger.
        </p>
      </header>

      {loading && <p role="status">Loading the Memory Wall…</p>}

      {error && (
        <p className="wall-error" role="alert">
          {error}{" "}
          <button type="button" onClick={() => void load()}>
            Try again
          </button>
        </p>
      )}

      {!loading && !error && months.length === 0 && (
        <p className="wall-empty">
          New memories are on their way. Please check back soon.
        </p>
      )}

      {months.map((month) => (
        <section
          key={month.key}
          className="wall-month"
          aria-labelledby={`wall-month-${month.key}`}
        >
          <h2 id={`wall-month-${month.key}`}>
            Happy moments in {month.label}
          </h2>

          <ul className="wall-grid">
            {month.photos.map((photo) => (
              <li key={photo.id}>
                <button
                  type="button"
                  className="wall-photo"
                  aria-label={`Enlarge photo: ${photo.altText}. Uploaded ${photo.uploadedOn}.`}
                  onClick={(event) => openPhoto(photo, event.currentTarget)}
                >
                  <img
                    src={publicImageUrl(photo.id)}
                    alt={photo.altText}
                    loading="lazy"
                  />
                </button>
              </li>
            ))}
          </ul>
        </section>
      ))}

      <dialog
        ref={dialogRef}
        className="wall-dialog"
        aria-labelledby="wall-dialog-title"
        onClose={afterClose}
        onKeyDown={onKeyDown}
      >
        {current && (
          <div>
            <h2 id="wall-dialog-title" className="wall-dialog-title">
              Memory Wall photo
            </h2>

            <img
              className="wall-dialog-image"
              src={publicImageUrl(current.photo.id)}
              alt={current.photo.altText}
            />

            <p className="wall-dialog-caption">
              {current.photo.altText}
              <span>
                {" "}
                · Uploaded {current.photo.uploadedOn}
              </span>
            </p>

            <p className="wall-dialog-count" aria-live="polite">
              Photo {openIndex + 1} of {flat.length}
            </p>

            <div className="wall-dialog-actions">
              <button type="button" onClick={() => step(-1)} disabled={flat.length < 2}>
                ← Previous
              </button>
              <button type="button" onClick={() => step(1)} disabled={flat.length < 2}>
                Next →
              </button>
              <button
                type="button"
                className="wall-dialog-close"
                onClick={() => dialogRef.current?.close()}
              >
                Close
              </button>
            </div>
          </div>
        )}
      </dialog>
    </main>
  );
}

export default MemoryWall;
