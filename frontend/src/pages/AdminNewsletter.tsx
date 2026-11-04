
import { useCallback, useEffect, useState } from "react";
import { apiUrl } from "../api/api";
import "./AdminNewsletter.css";

type Campaign = {
  id: number;
  subject: string;
  content: string;
  status: string;
  recipientCount?: number;
  successCount?: number;
  failedCount?: number;
  createdAt?: string;
  sentAt?: string;
};

type Subscriber = {
  id: number;
  email: string;
  createdAt?: string;
};

type Overview = {
  activeSubscribers?: number;
  subscribers?: Subscriber[];
  campaigns?: Campaign[];
};

type PreviewResponse = {
  campaign: Campaign;
  activeRecipientCount: number;
};

async function readResponse(response: Response) {
  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    throw new Error(
      data.message || `Request failed (${response.status}).`
    );
  }

  return data;
}

export default function AdminNewsletter() {
  const [overview, setOverview] = useState<Overview>({});
  const [subject, setSubject] = useState("");
  const [content, setContent] = useState("");
  const [campaignId, setCampaignId] = useState<number | null>(null);
  const [preview, setPreview] = useState<PreviewResponse | null>(null);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const headers = {
    Authorization: `Bearer ${localStorage.getItem("adminToken") || ""}`,
    "Content-Type": "application/json",
  };

  const loadOverview = useCallback(async () => {
    try {
      const response = await fetch(
        apiUrl("/api/admin/newsletter"),
        { headers }
      );

      const data = await readResponse(response);
      setOverview(data as Overview);
      setError("");
    } catch (e) {
      setError(
        e instanceof Error
          ? e.message
          : "Unable to load newsletter data."
      );
    }
  }, []);

  useEffect(() => {
    void loadOverview();
  }, [loadOverview]);

  async function saveDraft(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();

    setBusy(true);
    setError("");
    setMessage("");

    try {
      const url = campaignId
        ? `/api/admin/newsletter/${campaignId}`
        : "/api/admin/newsletter";

      const response = await fetch(apiUrl(url), {
        method: campaignId ? "PUT" : "POST",
        headers,
        body: JSON.stringify({ subject, content }),
      });

      const data = await readResponse(response) as Campaign;

      setCampaignId(data.id);
      setMessage("Draft saved successfully.");
      setPreview(null);

      await loadOverview();
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Unable to save draft."
      );
    } finally {
      setBusy(false);
    }
  }

  async function openPreview(id: number) {
    setError("");
    setMessage("");

    try {
      const response = await fetch(
        apiUrl(`/api/admin/newsletter/${id}/preview`),
        { headers }
      );

      const data = await readResponse(response) as PreviewResponse;

      setPreview(data);
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Unable to load preview."
      );
    }
  }

  async function sendCampaign(id: number) {
    if (
      !window.confirm(
        "Send this newsletter to all active subscribers?"
      )
    ) {
      return;
    }

    setBusy(true);
    setError("");
    setMessage("");

    try {
      const response = await fetch(
        apiUrl(`/api/admin/newsletter/${id}/send`),
        {
          method: "POST",
          headers,
        }
      );

      const data = await readResponse(response) as Campaign;

      setMessage(
        `Send completed. Recipients: ${data.recipientCount ?? 0}; successful: ${data.successCount ?? 0}; failed: ${data.failedCount ?? 0}.`
      );

      setPreview(null);
      await loadOverview();
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Unable to send newsletter."
      );
    } finally {
      setBusy(false);
    }
  }

  async function retryFailed(id: number) {
    if (!window.confirm("Retry delivery to failed recipients only?")) {
      return;
    }

    setBusy(true);
    setError("");
    setMessage("");

    try {
      const response = await fetch(
        apiUrl(`/api/admin/newsletter/${id}/retry-failed`),
        {
          method: "POST",
          headers,
        }
      );

      const data = await readResponse(response) as Campaign;

      setMessage(
        `Retry completed. Successful: ${data.successCount ?? 0}; failed: ${data.failedCount ?? 0}.`
      );

      await loadOverview();
    } catch (e) {
      setError(
        e instanceof Error
          ? e.message
          : "Unable to retry failed deliveries."
      );
    } finally {
      setBusy(false);
    }
  }

  const campaigns = overview.campaigns ?? [];
  const subscribers = overview.subscribers ?? [];
  const activeCount = overview.activeSubscribers ?? 0;

  return (
    <section className="admin-newsletter">
      <header>
        <p className="dashboard-eyebrow">EMAIL COMMUNICATION</p>
        <h2>Newsletter management</h2>
        <p>Create, preview and send newsletters to active subscribers.</p>
      </header>

      <section className="newsletter-admin-count">
        <div>
          <span>Active subscribers</span>
          <strong>{activeCount}</strong>
        </div>

        <details>
          <summary>View subscriber list</summary>

          {subscribers.length === 0 ? (
            <p>No active subscribers yet.</p>
          ) : (
            <ul>
              {subscribers.map((subscriber) => (
                <li key={subscriber.id}>
                  {subscriber.email}
                </li>
              ))}
            </ul>
          )}
        </details>
      </section>

      {error && (
        <p className="newsletter-admin-error" role="alert">
          {error}
        </p>
      )}

      {message && (
        <p className="newsletter-admin-message" role="status">
          {message}
        </p>
      )}

      <form className="newsletter-editor" onSubmit={saveDraft}>
        <h3>
          {campaignId
            ? `Edit draft #${campaignId}`
            : "Create a newsletter"}
        </h3>

        <label htmlFor="campaign-subject">Email subject</label>

        <input
          id="campaign-subject"
          type="text"
          required
          maxLength={200}
          value={subject}
          onChange={(e) => setSubject(e.target.value)}
        />

        <label htmlFor="campaign-content">Newsletter content</label>

        <textarea
          id="campaign-content"
          required
          rows={10}
          value={content}
          onChange={(e) => setContent(e.target.value)}
        />

        <div className="newsletter-admin-actions">
          <button type="submit" disabled={busy}>
            {busy ? "Saving..." : "Save draft"}
          </button>

          <button
            type="button"
            className="secondary"
            onClick={() => {
              setCampaignId(null);
              setSubject("");
              setContent("");
              setPreview(null);
              setError("");
              setMessage("");
            }}
          >
            Clear editor
          </button>
        </div>
      </form>

      {preview && (
        <section className="newsletter-preview">
          <h3>Newsletter preview</h3>

          <h4>{preview.campaign.subject}</h4>

          <pre>{preview.campaign.content}</pre>

          <p>
            Active recipients: <strong>{preview.activeRecipientCount}</strong>
          </p>

          <button
            type="button"
            disabled={busy}
            onClick={() => void sendCampaign(preview.campaign.id)}
          >
            {busy ? "Sending..." : "Confirm and send"}
          </button>

          <button
            type="button"
            className="secondary"
            onClick={() => setPreview(null)}
          >
            Close preview
          </button>
        </section>
      )}

      <section className="newsletter-campaigns">
        <h3>Campaign history</h3>

        {campaigns.length === 0 ? (
          <p>No campaigns are available yet.</p>
        ) : (
          campaigns.map((campaign) => (
            <article
              className="newsletter-campaign"
              key={campaign.id}
            >
              <div>
                <h4>{campaign.subject}</h4>

                <p>Status: {campaign.status}</p>

                <p>
                  Recipients: {campaign.recipientCount ?? 0} ·
                  Sent: {campaign.successCount ?? 0} ·
                  Failed: {campaign.failedCount ?? 0}
                </p>
              </div>

              <div className="newsletter-admin-actions">
                <button
                  type="button"
                  onClick={() => void openPreview(campaign.id)}
                >
                  Preview
                </button>

                {campaign.status === "DRAFT" && (
                  <button
                    type="button"
                    onClick={() => {
                      setCampaignId(campaign.id);
                      setSubject(campaign.subject);
                      setContent(campaign.content);
                      setPreview(null);
                    }}
                  >
                    Edit draft
                  </button>
                )}

                {campaign.status === "DRAFT" && (
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => void sendCampaign(campaign.id)}
                  >
                    Send
                  </button>
                )}

                {(campaign.failedCount ?? 0) > 0 && (
                  <button
                    type="button"
                    className="secondary"
                    disabled={busy}
                    onClick={() => void retryFailed(campaign.id)}
                  >
                    Retry failed
                  </button>
                )}
              </div>
            </article>
          ))
        )}
      </section>
    </section>
  );
}
