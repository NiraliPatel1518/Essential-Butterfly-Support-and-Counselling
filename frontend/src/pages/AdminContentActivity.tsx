import { useCallback, useEffect, useState } from "react";
import { fetchAuditLog, type AuditEntry } from "../api/contentApi";
import { useAdminSession } from "../hooks/useAdminSession";
import "./AdminContent.css";

const ACTION_LABELS: Record<string, string> = {
  CONTENT_DRAFT_SAVED: "Page draft saved",
  CONTENT_PUBLISHED: "Page published",
  RESOURCE_CREATED: "Resource added",
  RESOURCE_UPDATED: "Resource updated",
  RESOURCE_PUBLISHED: "Resource published",
  RESOURCE_UNPUBLISHED: "Resource unpublished",
  RESOURCE_REMOVED: "Resource removed",
  SECURITY_UNSAFE_FILE_REJECTED: "Unsafe file rejected",
  SECURITY_UNSAFE_LINK_REJECTED: "Unsafe link rejected",
};

// "title" or "page_key" or "reason" from the stored details, for display only.
function summarize(details: string): string {
  try {
    const parsed = JSON.parse(details) as Record<string, unknown>;
    const value = parsed.title ?? parsed.page_key ?? parsed.reason ?? "";

    return typeof value === "string" ? value : "";
  } catch {
    return "";
  }
}

function formatTime(value: string): string {
  const date = new Date(value.replace(" ", "T"));

  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

function AdminContentActivity() {
  const handleSessionExpired = useAdminSession();

  const [entries, setEntries] = useState<AuditEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      setEntries(await fetchAuditLog(50));
    } catch (loadError) {
      if (!handleSessionExpired(loadError)) {
        setError(
          loadError instanceof Error
            ? loadError.message
            : "The recent changes could not be loaded."
        );
      }
    } finally {
      setLoading(false);
    }
  }, [handleSessionExpired]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <section className="admin-content-panel" aria-labelledby="content-activity-title">
      <div className="admin-content-panel-heading">
        <div>
          <h3 id="content-activity-title">Recent changes</h3>
          <p className="admin-content-hint">
            Every change to website content is recorded here, including files
            and links that were rejected for safety.
          </p>
        </div>
        <button
          type="button"
          className="secondary"
          disabled={loading}
          onClick={() => void load()}
        >
          Refresh
        </button>
      </div>

      {error && (
        <div className="admin-content-alert error" role="alert">
          {error}
        </div>
      )}

      {loading ? (
        <p role="status">Loading recent changes...</p>
      ) : entries.length === 0 ? (
        <p>No changes have been recorded yet.</p>
      ) : (
        <table className="admin-content-table">
          <caption className="sr-only">Recent website content changes</caption>
          <thead>
            <tr>
              <th scope="col">When</th>
              <th scope="col">What happened</th>
              <th scope="col">Item</th>
              <th scope="col">By</th>
            </tr>
          </thead>
          <tbody>
            {entries.map((entry) => (
              <tr key={entry.id}>
                <td>{formatTime(entry.occurredAt)}</td>
                <td>{ACTION_LABELS[entry.action] ?? entry.action}</td>
                <td>{summarize(entry.details)}</td>
                <td>{entry.actor ?? "Unknown"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

export default AdminContentActivity;
