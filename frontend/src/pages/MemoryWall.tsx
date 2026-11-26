import { useEffect, useRef, useState } from "react";
import { fetchWall, publicImageUrl } from "../api/memoryWallApi";
import type { WallMonth } from "../api/memoryWallApi";
import "./MemoryWall.css";

export default function MemoryWall() {
  const [months, setMonths] = useState<WallMonth[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [index, setIndex] = useState(0);

  const dialogRef = useRef<HTMLDialogElement>(null);
  const openButtonRef = useRef<HTMLButtonElement>(null);
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(false);

    fetchWall()
      .then((result) => {
        if (active) setMonths(result);
      })
      .catch(() => {
        if (active) setError(true);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [attempt]);

  // Keep the published photos in the order returned by the Memory Wall API.
  const photos = months.flatMap((month) => month.photos);
  const current = photos[index];

  function openSlideshow() {
    const dialog = dialogRef.current;
    if (!dialog || dialog.open || photos.length === 0) return;

    setIndex(0);
    dialog.showModal();
    closeButtonRef.current?.focus();
  }

  function moveSlide(direction: number) {
    if (photos.length < 2) return;
    setIndex((previous) =>
      (previous + direction + photos.length) % photos.length
    );
  }

  return (
    <main className="wall-page">
      <header className="wall-header">
        <p className="wall-eyebrow">ESSENTIAL BUTTERFLY</p>
        <h1>Memory Wall</h1>
        <p>
          Explore moments shared by our families and community.
          Open the slideshow to browse the photos at your own pace.
        </p>
      </header>

      {loading ? (
        <p role="status">Loading memories…</p>
      ) : error ? (
        <div className="wall-error">
          <p role="alert">We couldn’t load the photos. Please try again.</p>
          <button type="button" onClick={() => setAttempt((value) => value + 1)}>
            Try again
          </button>
        </div>
      ) : photos.length === 0 ? (
        <p className="wall-empty">
          No photos have been published yet. Please check back soon.
        </p>
      ) : (
        <div className="wall-launch">
          <p>
            {photos.length} published {photos.length === 1 ? "photo" : "photos"}
          </p>
          <button
            ref={openButtonRef}
            type="button"
            className="wall-launch-button"
            aria-haspopup="dialog"
            onClick={openSlideshow}
          >
            Open slideshow
          </button>
        </div>
      )}

      <dialog
        ref={dialogRef}
        className="wall-dialog"
        aria-labelledby="wall-slideshow-title"
        onClose={() => openButtonRef.current?.focus()}
        onKeyDown={(event) => {
          if (event.key === "ArrowLeft" || event.key === "ArrowRight") {
            event.preventDefault();
            moveSlide(event.key === "ArrowLeft" ? -1 : 1);
          }
        }}
      >
        <h2 id="wall-slideshow-title" className="wall-dialog-title">
          Memory Wall slideshow
        </h2>

        <div className="wall-dialog-actions">
          <button
            ref={closeButtonRef}
            type="button"
            className="wall-dialog-close"
            onClick={() => dialogRef.current?.close()}
          >
            Close slideshow
          </button>
        </div>

        {current && (
          <>
            <figure className="wall-slide">
              <img
                className="wall-dialog-image"
                src={publicImageUrl(current.id)}
                alt={current.altText}
              />
              <figcaption className="wall-dialog-caption">
                {current.altText}
                <span> · Uploaded {current.uploadedOn}</span>
              </figcaption>
            </figure>

            <p className="wall-dialog-count" role="status" aria-atomic="true">
              Photo {index + 1} of {photos.length}
            </p>

            {photos.length > 1 && (
              <div className="wall-dialog-actions">
                <button type="button" onClick={() => moveSlide(-1)}>
                  ← Previous
                </button>
                <button type="button" onClick={() => moveSlide(1)}>
                  Next →
                </button>
              </div>
            )}
          </>
        )}
      </dialog>
    </main>
  );
}