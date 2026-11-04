import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { apiUrl } from "../api/api";
import "./Updates.css";

type UpdateSummary = {
  id: number;
  title: string;
  preview: string;
  createdAt: string;
  updatedAt: string;
  coverImage?: string | null;
};

type FullUpdate = {
  id: number;
  title: string;
  content: string;
  published: boolean;
  createdAt: string;
  updatedAt: string;
  photos: string[];
  socialLinks: string[];
};

function formatDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "Date unavailable"
    : date.toLocaleDateString(undefined, {
        year: "numeric",
        month: "long",
        day: "numeric",
      });
}

function Updates() {
  const { id } = useParams();
  const navigate = useNavigate();

  const [items, setItems] = useState<UpdateSummary[]>([]);
  const [selected, setSelected] = useState<FullUpdate | null>(null);
  const [photoIndex, setPhotoIndex] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const closeRef = useRef<HTMLButtonElement>(null);

  const loadUpdates = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      const response = await fetch(apiUrl("/api/updates"));
      if (!response.ok) throw new Error("Unable to load updates.");
      setItems(await response.json());
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to load updates.");
    } finally {
      setLoading(false);
    }
  }, []);

  const loadFullUpdate = useCallback(async (updateId: string) => {
    const token =
      localStorage.getItem("authToken") ||
      localStorage.getItem("adminToken");

    if (!token) {
      navigate(`/login?returnTo=${encodeURIComponent(`/updates/${updateId}`)}`);
      return;
    }

    setLoading(true);
    setError("");

    try {
      const response = await fetch(
        apiUrl(`/api/updates/${encodeURIComponent(updateId)}`),
        { headers: { Authorization: `Bearer ${token}` } }
      );

      if (response.status === 401) {
        localStorage.removeItem("authToken");
        localStorage.removeItem("adminToken");
        navigate(`/login?returnTo=${encodeURIComponent(`/updates/${updateId}`)}`);
        return;
      }

      if (!response.ok) throw new Error("Unable to load this update.");

      setSelected(await response.json());
      setPhotoIndex(0);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to load this update.");
    } finally {
      setLoading(false);
    }
  }, [navigate]);

  useEffect(() => {
    if (id) void loadFullUpdate(id);
    else {
      setSelected(null);
      void loadUpdates();
    }
  }, [id, loadFullUpdate, loadUpdates]);

  useEffect(() => {
    if (!selected) return;

    closeRef.current?.focus();

    const handleKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") navigate("/updates");

      if (event.key === "ArrowRight" && selected.photos?.length) {
        setPhotoIndex((current) => (current + 1) % selected.photos.length);
      }

      if (event.key === "ArrowLeft" && selected.photos?.length) {
        setPhotoIndex((current) =>
          (current - 1 + selected.photos.length) % selected.photos.length
        );
      }
    };

    document.addEventListener("keydown", handleKey);
    return () => document.removeEventListener("keydown", handleKey);
  }, [selected, navigate]);

  if (loading) {
    return <main className="updates-page"><div className="updates-container"><p role="status">Loading updates...</p></div></main>;
  }

  if (error) {
    return (
      <main className="updates-page">
        <div className="updates-container">
          <h1>Unable to load updates</h1>
          <p role="alert">{error}</p>
          <button onClick={() => id ? void loadFullUpdate(id) : void loadUpdates()}>Retry</button>
          <p><Link to="/updates">Back to Updates</Link></p>
        </div>
      </main>
    );
  }

  if (id && selected) {
    const photos = selected.photos || [];
    const links = selected.socialLinks || [];

    return (
      <main className="updates-page">
        <div className="updates-modal-backdrop" onMouseDown={(e) => {
          if (e.target === e.currentTarget) navigate("/updates");
        }}>
          <section
            className="updates-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="updates-modal-title"
          >
            <button
              ref={closeRef}
              type="button"
              className="updates-modal-close"
              onClick={() => navigate("/updates")}
              aria-label="Close update"
            >
              ×
            </button>

            <p className="updates-eyebrow">ESSENTIAL BUTTERFLY · NEWS</p>
            <h1 id="updates-modal-title">{selected.title}</h1>
            <p className="updates-date">Published {formatDate(selected.updatedAt)}</p>

            {photos.length > 0 ? (
              <div className="updates-gallery">
                <img
                  src={photos[photoIndex]}
                  alt={`${selected.title} photo ${photoIndex + 1} of ${photos.length}`}
                />

                {photos.length > 1 && (
                  <div className="updates-gallery-controls">
                    <button
                      type="button"
                      onClick={() => setPhotoIndex((photoIndex - 1 + photos.length) % photos.length)}
                      aria-label="Previous photo"
                    >
                      ← Previous
                    </button>
                    <span>{photoIndex + 1} / {photos.length}</span>
                    <button
                      type="button"
                      onClick={() => setPhotoIndex((photoIndex + 1) % photos.length)}
                      aria-label="Next photo"
                    >
                      Next →
                    </button>
                  </div>
                )}
              </div>
            ) : (
              <div className="updates-default-art" aria-label="Essential Butterfly">
                <span aria-hidden="true">❋</span>
                <strong>Essential Butterfly</strong>
              </div>
            )}

            <div className="updates-full-content">
              {selected.content.split(/\r?\n/).map((paragraph, index) =>
                paragraph.trim() ? <p key={index}>{paragraph}</p> : <br key={index} />
              )}
            </div>

            {links.length > 0 && (
              <div className="updates-social-links">
                {links.map((link, index) => (
                  <a key={index} href={link} target="_blank" rel="noopener noreferrer">
                    {link.includes("instagram") ? "Instagram" :
                     link.includes("facebook") ? "Facebook" :
                     link.includes("linkedin") ? "LinkedIn" :
                     link.includes("youtube") ? "YouTube" : "Visit link"} ↗
                  </a>
                ))}
              </div>
            )}

            <div className="updates-article-footer">
              <button type="button" className="updates-primary-button" onClick={() => navigate("/updates")}>
                Close update
              </button>
            </div>
          </section>
        </div>
      </main>
    );
  }

  return (
    <main className="updates-page">
      <section className="updates-hero">
        <div className="updates-container">
          <p className="updates-eyebrow">NEWS &amp; ANNOUNCEMENTS</p>
          <h1>Updates from Essential Butterfly</h1>
          <p>News, announcements and helpful information from Casey.</p>
        </div>
      </section>

      <section className="updates-container updates-list-section">
        <div className="updates-section-heading">
          <h2>Latest Updates</h2>
          <p>Stay informed about what's happening.</p>
        </div>

        {items.length === 0 ? (
          <div className="updates-empty">
            <h2>No updates yet</h2>
            <p>Please check back soon for news and announcements.</p>
          </div>
        ) : (
          <div className="updates-grid">
            {items.map((item) => (
              <article className="updates-card" key={item.id}>
                {item.coverImage ? (
                  <img className="updates-card-image" src={item.coverImage} alt="" />
                ) : (
                  <div className="updates-card-art" aria-hidden="true">
                    <span className="updates-butterfly">❋</span>
                    <span>Essential Butterfly</span>
                  </div>
                )}

                <div className="updates-card-body">
                  <p className="updates-card-date">{formatDate(item.updatedAt)}</p>
                  <h3>{item.title}</h3>
                  <p className="updates-preview">{item.preview}</p>
                  <Link className="updates-read-more" to={`/updates/${item.id}`}>
                    Read full update →
                  </Link>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>
    </main>
  );
}

export default Updates;