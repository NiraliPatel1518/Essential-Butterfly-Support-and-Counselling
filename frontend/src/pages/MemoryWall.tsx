import { useEffect, useState } from "react";
import { fetchWall, publicImageUrl } from "../api/memoryWallApi";
import type { WallMonth } from "../api/memoryWallApi";
import "./MemoryWall.css";

const SLIDE_DURATION_MS = 7000;
const REDUCED_MOTION = "(prefers-reduced-motion: reduce)";

function prefersReducedMotion() {
  return window.matchMedia?.(REDUCED_MOTION)?.matches ?? false;
}

export default function MemoryWall() {
  const [months, setMonths] = useState<WallMonth[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [attempt, setAttempt] = useState(0);

  const [index, setIndex] = useState(0);
  const [playing, setPlaying] = useState(() => !prefersReducedMotion());
  const [hovered, setHovered] = useState(false);
  const [pageVisible, setPageVisible] = useState(() => !document.hidden);
  const [announcement, setAnnouncement] = useState("");

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(false);

    fetchWall()
      .then((result) => {
        if (!active) return;
        setMonths(result);
        setIndex(0);
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

  useEffect(() => {
    const preference = window.matchMedia?.(REDUCED_MOTION);

    function updateMotionPreference() {
      if (preference?.matches) setPlaying(false);
    }

    function updateVisibility() {
      setPageVisible(!document.hidden);
    }

    preference?.addEventListener?.("change", updateMotionPreference);
    document.addEventListener("visibilitychange", updateVisibility);

    return () => {
      preference?.removeEventListener?.("change", updateMotionPreference);
      document.removeEventListener("visibilitychange", updateVisibility);
    };
  }, []);

  // Only use photos returned by the public Memory Wall API.
  const photos = months.flatMap((month) => month.photos);
  const count = photos.length;
  const current = photos[index];
  const rotating = count > 1 && playing && !hovered && pageVisible;

  useEffect(() => {
    if (!rotating) return;

    const timer = window.setInterval(() => {
      setIndex((previous) => (previous + 1) % count);
    }, SLIDE_DURATION_MS);

    return () => window.clearInterval(timer);
  }, [rotating, count]);

  function photoTitle(photo: (typeof photos)[number], position: number) {
    if ("title" in photo && typeof photo.title === "string" && photo.title.trim()) {
      return photo.title.trim();
    }
    return photo.altText?.trim() || `Community memory ${position + 1}`;
  }

  function choosePhoto(position: number) {
    setPlaying(false);
    setIndex(position);
    setAnnouncement(
      `Photo ${position + 1} of ${count}: ${photoTitle(photos[position], position)}`
    );
  }

  return (
    <main className={`wall-page${current ? " wall-page--has-photos" : ""}`}>
      <header className="wall-header">
        <p className="wall-eyebrow">Essential Butterfly</p>
        <h1>Memory Wall</h1>
        <p className="wall-intro">Moments shared by our families and community.</p>
      </header>

      {loading ? (
        <p className="wall-message" role="status">Loading memories…</p>
      ) : error ? (
        <div className="wall-message">
          <p role="alert">We couldn’t load the photos right now.</p>
          <button
            type="button"
            className="wall-retry"
            onClick={() => setAttempt((value) => value + 1)}
          >
            Try again
          </button>
        </div>
      ) : !current ? (
        <p className="wall-message">
          No photos have been published yet. Please check back soon.
        </p>
      ) : (
        <section
          className="memory-feature"
          aria-label="Community memories"
          aria-roledescription="carousel"
          onPointerEnter={(event) => {
            if (event.pointerType === "mouse") setHovered(true);
          }}
          onPointerLeave={() => setHovered(false)}
          onFocusCapture={(event) => {
            if (
              !event.currentTarget.contains(event.relatedTarget) &&
              event.target.matches(":focus-visible")
            ) {
              setPlaying(false);
            }
          }}
        >
          <figure className="memory-feature-card">
            <div className="memory-feature-stage">
              <img
                key={current.id}
                className="memory-feature-photo"
                src={publicImageUrl(current.id)}
                alt={current.altText || ""}
              />
            </div>

            <figcaption className="memory-feature-caption">
              <p className="memory-feature-kicker">
                Community memory · {index + 1} of {count}
              </p>
              <h2>{photoTitle(current, index)}</h2>
              {current.uploadedOn && (
                <p className="memory-feature-date">Uploaded {current.uploadedOn}</p>
              )}
            </figcaption>
          </figure>

          {count > 1 && (
            <div className="memory-feature-toolbar">
              <button
                type="button"
                className="memory-feature-play"
                aria-label={playing ? "Pause slideshow" : "Play slideshow"}
                title={playing ? "Pause slideshow" : "Play slideshow"}
                onClick={() => setPlaying((value) => !value)}
              >
                <span aria-hidden="true">{playing ? "Ⅱ" : "▶"}</span>
              </button>

              <div
                className="memory-feature-dots"
                role="group"
                aria-label="Choose a photo"
              >
                {photos.map((photo, position) => (
                  <button
                    key={photo.id}
                    type="button"
                    className="memory-feature-dot"
                    aria-label={`Show photo ${position + 1}: ${photoTitle(photo, position)}`}
                    aria-current={position === index ? "true" : undefined}
                    onClick={() => choosePhoto(position)}
                  >
                    <span aria-hidden="true" />
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Announce a visitor's selection, not every automatic change. */}
          <span className="wall-sr-only" role="status" aria-atomic="true">
            {announcement}
          </span>
        </section>
      )}
    </main>
  );
}