
import { useCallback, useEffect, useState } from "react";
import type { FormEvent } from "react";
import { apiUrl } from "../api/api";
import "./TestimonialCards.css";

type TestimonialItem = {
  id: number;
  displayName: string;
  content: string;
  photos?: string[];
  createdAt: string;
};

const MAX_PHOTOS = 3;
const MAX_PHOTO_SIZE = 3 * 1024 * 1024;
const ALLOWED_TYPES = ["image/jpeg", "image/png", "image/webp"];

export default function ClientTestimonials() {
  const [items, setItems] = useState<TestimonialItem[]>([]);
  const [displayName, setDisplayName] = useState("");
  const [content, setContent] = useState("");
  const [consent, setConsent] = useState(false);
  const [photos, setPhotos] = useState<string[]>([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);

  const token = localStorage.getItem("authToken") || "";

  const loadTestimonials = useCallback(async () => {
    try {
      const response = await fetch(apiUrl("/api/testimonials/mine"), {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });

      if (!response.ok) {
        throw new Error("Unable to load your testimonials.");
      }

      const data: TestimonialItem[] = await response.json();
      setItems(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Unable to load your submissions."
      );
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    void loadTestimonials();
  }, [loadTestimonials]);

  async function selectPhotos(files: FileList | null) {
    if (!files) return;

    setError("");

    try {
      const selectedFiles = Array.from(files);

      if (selectedFiles.length > MAX_PHOTOS) {
        throw new Error("You can select a maximum of 3 photos.");
      }

      const photoData = await Promise.all(
        selectedFiles.map((file) => {
          return new Promise<string>((resolve, reject) => {
            if (!ALLOWED_TYPES.includes(file.type)) {
              reject(
                new Error("Only JPG, PNG and WEBP images are allowed.")
              );
              return;
            }

            if (file.size > MAX_PHOTO_SIZE) {
              reject(
                new Error("Each photo must be smaller than 3 MB.")
              );
              return;
            }

            const reader = new FileReader();

            reader.onload = () => {
              if (typeof reader.result === "string") {
                resolve(reader.result);
              } else {
                reject(new Error("Unable to read the selected photo."));
              }
            };

            reader.onerror = () => {
              reject(new Error("Could not read an image."));
            };

            reader.readAsDataURL(file);
          });
        })
      );

      setPhotos(photoData);
    } catch (err) {
      setPhotos([]);
      setError(
        err instanceof Error ? err.message : "Unable to add photos."
      );
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setNotice("");

    if (!token) {
      setError("Your session has expired. Please log in again.");
      return;
    }

    if (!displayName.trim()) {
      setError("Please enter your public display name.");
      return;
    }

    if (content.trim().length < 10) {
      setError("Your testimonial must contain at least 10 characters.");
      return;
    }

    if (!consent) {
      setError("Please provide publication consent.");
      return;
    }

    setBusy(true);

    try {
      const response = await fetch(apiUrl("/api/testimonials"), {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          displayName: displayName.trim(),
          content: content.trim(),
          publicationConsent: consent,
          photos,
        }),
      });

      if (!response.ok) {
        const message = await response.text();
        throw new Error(message || "Testimonial submission failed.");
      }

      setDisplayName("");
      setContent("");
      setConsent(false);
      setPhotos([]);

      setNotice(
        "Your testimonial has been submitted and is awaiting review."
      );

      await loadTestimonials();
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Submission failed. Please try again."
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="client-testimonials">
      <form
        onSubmit={handleSubmit}
        className="client-testimonial-form"
        style={{ display: "grid", gap: 12 }}
      >
        <h3>Share your experience</h3>

        <label>
          Public display name
          <input
            type="text"
            required
            maxLength={80}
            value={displayName}
            onChange={(event) => setDisplayName(event.target.value)}
            placeholder="Enter your display name"
          />
        </label>

        <label>
          Your testimonial
          <textarea
            required
            minLength={10}
            maxLength={5000}
            rows={5}
            value={content}
            onChange={(event) => setContent(event.target.value)}
            placeholder="Share your experience..."
          />
        </label>

        <label>
          Photos (optional, maximum 3)
          <input
            type="file"
            accept="image/jpeg,image/png,image/webp"
            multiple
            onChange={(event) => {
              void selectPhotos(event.target.files);
              event.target.value = "";
            }}
          />
        </label>

        {photos.length > 0 && (
          <div>
            <p>{photos.length} photo(s) selected.</p>

            <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
              {photos.map((photo, index) => (
                <img
                  key={index}
                  src={photo}
                  alt={`Selected testimonial photo ${index + 1}`}
                  style={{
                    width: 100,
                    height: 80,
                    objectFit: "cover",
                    borderRadius: 8,
                  }}
                />
              ))}
            </div>

            <button
              type="button"
              onClick={() => setPhotos([])}
              disabled={busy}
            >
              Remove selected photos
            </button>
          </div>
        )}

        <label>
          <input
            type="checkbox"
            checked={consent}
            onChange={(event) => setConsent(event.target.checked)}
          />
          I give permission for my testimonial, display name and selected
          photos to appear publicly if approved by the administrator.
        </label>

        {error && (
          <p role="alert" style={{ color: "#c62828" }}>
            {error}
          </p>
        )}

        {notice && (
          <p role="status" style={{ color: "#287a45" }}>
            {notice}
          </p>
        )}

        <button
          type="submit"
          className="testimonial-primary"
          disabled={busy}
        >
          {busy ? "Submitting..." : "Submit Testimonial"}
        </button>
      </form>

      <section className="my-testimonials">
        <h3>My submissions</h3>

        {loading ? (
          <p>Loading your submissions...</p>
        ) : items.length === 0 ? (
          <p>You have not submitted a testimonial yet.</p>
        ) : (
          <div className="my-testimonial-list">
            {items.map((item) => (
              <article key={item.id} className="dashboard-detail">
                <strong>{item.displayName}</strong>

                <p>{item.content}</p>

                {item.photos && item.photos.length > 0 && (
                  <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                    {item.photos.map((photo, index) => (
                      <img
                        key={index}
                        src={photo}
                        alt={`Testimonial photo ${index + 1}`}
                        style={{
                          width: 90,
                          height: 70,
                          objectFit: "cover",
                          borderRadius: 6,
                        }}
                      />
                    ))}
                  </div>
                )}

                <small>
                  Submitted{" "}
                  {item.createdAt
                    ? new Date(item.createdAt).toLocaleDateString()
                    : "recently"}{" "}
                  · Submitted for review
                </small>
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
