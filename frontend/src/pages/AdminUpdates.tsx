import { useCallback, useEffect, useState } from "react";
import { apiUrl } from "../api/api";
import "./AdminUpdates.css";

type WebsiteUpdate = {
  id: number;
  title: string;
  content: string;
  published: boolean;
  createdAt: string;
  updatedAt: string;
  photos: string[];
  socialLinks: string[];
};

async function readError(response: Response) {
  const body = await response.text();

  try {
    const parsed = JSON.parse(body);
    return parsed.message || parsed.error || body;
  } catch {
    return body || `Request failed (${response.status})`;
  }
}

function AdminUpdates() {
  const [updates, setUpdates] = useState<WebsiteUpdate[]>([]);
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [published, setPublished] = useState(true);
  const [photos, setPhotos] = useState<string[]>([]);
  const [socialLinksText, setSocialLinksText] = useState("");
  const [editingId, setEditingId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const token = localStorage.getItem("adminToken");

  const headers = {
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json",
  };

  const loadUpdates = useCallback(async () => {
    setLoading(true);
    try {
      const response = await fetch(apiUrl("/api/admin/updates"), {
        headers,
      });
      if (!response.ok) throw new Error(await readError(response));
      setUpdates(await response.json());
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to load updates.");
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    void loadUpdates();
  }, [loadUpdates]);

  const resetForm = () => {
    setTitle("");
    setContent("");
    setPublished(true);
    setPhotos([]);
    setSocialLinksText("");
    setEditingId(null);
  };

  const startEditing = (item: WebsiteUpdate) => {
    setEditingId(item.id);
    setTitle(item.title);
    setContent(item.content);
    setPublished(item.published);
    setPhotos(item.photos || []);
    setSocialLinksText((item.socialLinks || []).join("\n"));
    setError("");
    setNotice("");
    document.getElementById("admin-update-form")?.scrollIntoView({
      behavior: "smooth",
    });
  };

  const handlePhotos = async (files: FileList | null) => {
    if (!files) return;

    const selected = Array.from(files);

    if (photos.length + selected.length > 5) {
      setError("You can upload a maximum of 5 photos.");
      return;
    }

    for (const file of selected) {
      if (!["image/jpeg", "image/png", "image/webp"].includes(file.type)) {
        setError("Only JPEG, PNG and WebP photos are allowed.");
        return;
      }

      if (file.size > 2 * 1024 * 1024) {
        setError(`${file.name} is larger than 2 MB.`);
        return;
      }
    }

    try {
      const encoded = await Promise.all(
        selected.map(
          (file) =>
            new Promise<string>((resolve, reject) => {
              const reader = new FileReader();
              reader.onload = () => resolve(String(reader.result));
              reader.onerror = reject;
              reader.readAsDataURL(file);
            })
        )
      );

      setPhotos((current) => [...current, ...encoded]);
      setError("");
    } catch {
      setError("Unable to read one of the selected photos.");
    }
  };

  const saveUpdate = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const socialLinks = socialLinksText
      .split("\n")
      .map((link) => link.trim())
      .filter(Boolean);

    if (socialLinks.length > 5) {
      setError("You can add a maximum of 5 social media links.");
      return;
    }

    if (socialLinks.some((link) => !/^https?:\/\/.+/i.test(link))) {
      setError("Every social media link must start with http:// or https://.");
      return;
    }

    setSaving(true);
    setError("");
    setNotice("");

    try {
      const editing = editingId !== null;

      const response = await fetch(
        apiUrl(
          editing
            ? `/api/admin/updates/${editingId}`
            : "/api/admin/updates"
        ),
        {
          method: editing ? "PUT" : "POST",
          headers,
          body: JSON.stringify({
            title: title.trim(),
            content: content.trim(),
            published,
            photos,
            socialLinks,
          }),
        }
      );

      if (!response.ok) throw new Error(await readError(response));

      resetForm();
      setNotice("Update saved successfully.");
      await loadUpdates();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to save update.");
    } finally {
      setSaving(false);
    }
  };

  const removeUpdate = async (item: WebsiteUpdate) => {
    if (!window.confirm(`Remove "${item.title}"?`)) return;

    try {
      const response = await fetch(
        apiUrl(`/api/admin/updates/${item.id}`),
        {
          method: "DELETE",
          headers: { Authorization: `Bearer ${token}` },
        }
      );

      if (!response.ok) throw new Error(await readError(response));

      if (editingId === item.id) resetForm();
      setNotice("Update removed.");
      await loadUpdates();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to remove update.");
    }
  };

  return (
    <section className="admin-updates">
      <header className="admin-updates-heading">
        <div>
          <p className="dashboard-eyebrow">PUBLIC WEBSITE CONTENT</p>
          <h2>Manage Updates</h2>
          <p>Create announcements, add photos and share social links.</p>
        </div>
        <button onClick={() => void loadUpdates()} disabled={loading}>
          Refresh
        </button>
      </header>

      {error && <div className="admin-updates-alert error" role="alert">{error}</div>}
      {notice && <div className="admin-updates-alert success" role="status">{notice}</div>}

      <form id="admin-update-form" className="admin-update-form" onSubmit={saveUpdate}>
        <h3>{editingId ? "Edit Update" : "Add a New Update"}</h3>

        <div className="admin-update-field">
          <label htmlFor="update-title">Update title</label>
          <input
            id="update-title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            maxLength={180}
            required
          />
        </div>

        <div className="admin-update-field">
          <label htmlFor="update-content">Description / update content</label>
          <textarea
            id="update-content"
            value={content}
            onChange={(e) => setContent(e.target.value)}
            maxLength={20000}
            rows={8}
            required
          />
        </div>

        <div className="admin-update-field">
          <label htmlFor="update-photos">Photos (maximum 5, 2 MB each)</label>
          <input
            id="update-photos"
            type="file"
            accept="image/jpeg,image/png,image/webp"
            multiple
            onChange={(e) => {
              void handlePhotos(e.target.files);
              e.target.value = "";
            }}
          />
          <small>The first photo selected is the cover photo.</small>
        </div>

        {photos.length > 0 && (
          <div className="admin-update-photo-list">
            {photos.map((photo, index) => (
              <div key={index}>
                <img src={photo} alt={`Update photo ${index + 1}`} />
                <span>{index === 0 ? "Cover photo" : `Photo ${index + 1}`}</span>
                <button
                  type="button"
                  onClick={() =>
                    setPhotos((current) => current.filter((_, i) => i !== index))
                  }
                >
                  Remove
                </button>
              </div>
            ))}
          </div>
        )}

        <div className="admin-update-field">
          <label htmlFor="update-social-links">Social media links (one URL per line, maximum 5)</label>
          <textarea
            id="update-social-links"
            value={socialLinksText}
            onChange={(e) => setSocialLinksText(e.target.value)}
            rows={4}
            placeholder={"https://instagram.com/...\nhttps://facebook.com/..."}
          />
          <small>Leave empty if there are no social links.</small>
        </div>

        <label className="admin-update-publish-option">
          <input
            type="checkbox"
            checked={published}
            onChange={(e) => setPublished(e.target.checked)}
          />
          <span>
            <strong>Publish this update</strong>
            <small>Only published updates appear publicly.</small>
          </span>
        </label>

        <div className="admin-update-form-actions">
          {editingId !== null && (
            <button type="button" onClick={resetForm} disabled={saving}>
              Cancel editing
            </button>
          )}
          <button
            type="submit"
            className="admin-update-save"
            disabled={saving || !title.trim() || !content.trim()}
          >
            {saving ? "Saving..." : editingId ? "Save Changes" : "Save Update"}
          </button>
        </div>
      </form>

      <section className="admin-updates-list">
        <h3>Existing Updates ({updates.length})</h3>

        {loading ? (
          <p>Loading updates...</p>
        ) : (
          <div className="admin-updates-items">
            {updates.map((item) => (
              <article className="admin-update-item" key={item.id}>
                <div className="admin-update-item-copy">
                  <span className={item.published ? "admin-update-status published" : "admin-update-status unpublished"}>
                    {item.published ? "Published" : "Unpublished"}
                  </span>
                  <h4>{item.title}</h4>
                  <p>{item.content.slice(0, 220)}</p>
                  <small>
                    {item.photos?.length || 0} photo(s) · {item.socialLinks?.length || 0} social link(s)
                  </small>
                </div>
                <div className="admin-update-item-actions">
                  <button type="button" onClick={() => startEditing(item)}>Edit</button>
                  <button type="button" className="remove" onClick={() => void removeUpdate(item)}>Remove</button>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>
    </section>
  );
}

export default AdminUpdates;