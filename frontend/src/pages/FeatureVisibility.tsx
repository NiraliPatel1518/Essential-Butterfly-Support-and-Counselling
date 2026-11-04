
import { useCallback, useEffect, useMemo, useState } from "react";
import { apiUrl } from "../api/api";
import "./FeatureVisibility.css";

type FeatureKey = "NEWSLETTER" | "TESTIMONIALS" | "UPDATES";

type PublicFeature = {
  featureKey: FeatureKey;
  label: string;
  enabled: boolean;
  displayOrder: number;
};

const SPRINT_FEATURES: {
  featureKey: FeatureKey;
  label: string;
  description: string;
  icon: string;
}[] = [
  {
    featureKey: "NEWSLETTER",
    label: "Newsletter Signup",
    description: "Allow visitors to subscribe to Casey's newsletter.",
    icon: "✉",
  },
  {
    featureKey: "TESTIMONIALS",
    label: "Testimonials",
    description: "Display testimonials and feedback on the public website.",
    icon: "❝",
  },
  {
    featureKey: "UPDATES",
    label: "Updates",
    description: "Show the latest news and announcements to visitors.",
    icon: "↗",
  },
];

async function readError(response: Response): Promise<string> {
  const body = await response.text();

  if (!body) return `Request failed (${response.status}).`;

  try {
    const parsed = JSON.parse(body);
    return parsed.message || parsed.error || body;
  } catch {
    return body;
  }
}

function FeatureVisibility() {
  const [saved, setSaved] = useState<PublicFeature[]>([]);
  const [draft, setDraft] = useState<PublicFeature[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const token = localStorage.getItem("adminToken");

  const dirty = useMemo(
    () =>
      SPRINT_FEATURES.some((feature) => {
        const before = saved.find(
          (item) => item.featureKey === feature.featureKey
        );
        const after = draft.find(
          (item) => item.featureKey === feature.featureKey
        );

        return !before || !after || before.enabled !== after.enabled;
      }),
    [saved, draft]
  );

  const loadFeatures = useCallback(async () => {
    setLoading(true);
    setError("");
    setNotice("");

    try {
      const response = await fetch(apiUrl("/api/admin/features"), {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });

      if (!response.ok) {
        throw new Error(await readError(response));
      }

      const data = (await response.json()) as PublicFeature[];

      // Only the three features included in this sprint are shown.
      const sprintFeatures = SPRINT_FEATURES.map((definition) => {
        const existing = data.find(
          (item) => item.featureKey === definition.featureKey
        );

        return {
          featureKey: definition.featureKey,
          label: definition.label,
          enabled: existing?.enabled ?? true,
          displayOrder: SPRINT_FEATURES.findIndex(
            (item) => item.featureKey === definition.featureKey
          ) + 1,
        };
      });

      setSaved(sprintFeatures);
      setDraft(sprintFeatures);
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "Unable to load feature settings."
      );
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    void loadFeatures();
  }, [loadFeatures]);

  useEffect(() => {
    if (!dirty) return;

    const warnBeforeLeaving = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };

    window.addEventListener("beforeunload", warnBeforeLeaving);

    return () => {
      window.removeEventListener("beforeunload", warnBeforeLeaving);
    };
  }, [dirty]);

  const toggleFeature = (key: FeatureKey, enabled: boolean) => {
    setDraft((current) =>
      current.map((feature) =>
        feature.featureKey === key
          ? { ...feature, enabled }
          : feature
      )
    );

    setNotice("");
    setError("");
  };

  const discardChanges = () => {
    if (
      dirty &&
      !window.confirm("Discard your unsaved feature visibility changes?")
    ) {
      return;
    }

    setDraft(saved.map((feature) => ({ ...feature })));
    setError("");
    setNotice("");
  };

  const saveChanges = async () => {
    if (!dirty || saving) return;

    setSaving(true);
    setError("");
    setNotice("");

    try {
      const response = await fetch(apiUrl("/api/admin/features"), {
        method: "PUT",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(
          draft.map(({ featureKey, enabled }) => ({
            featureKey,
            enabled,
          }))
        ),
      });

      if (!response.ok) {
        throw new Error(await readError(response));
      }

      const result = (await response.json()) as PublicFeature[];

      const updated = SPRINT_FEATURES.map((definition, index) => {
        const returned = result.find(
          (item) => item.featureKey === definition.featureKey
        );

        return {
          featureKey: definition.featureKey,
          label: definition.label,
          enabled: returned?.enabled ?? draft[index].enabled,
          displayOrder: index + 1,
        };
      });

      setSaved(updated);
      setDraft(updated);
      setNotice("Your website feature settings have been saved.");

      window.dispatchEvent(new Event("public-features-updated"));
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "Unable to save settings. Your previous saved settings remain active."
      );
    } finally {
      setSaving(false);
    }
  };

  const visibleCount = draft.filter((feature) => feature.enabled).length;

  return (
    <section className="feature-settings" aria-labelledby="feature-title">
      <header className="feature-settings-header">
        <div className="feature-heading-icon" aria-hidden="true">
          ⚙
        </div>

        <div className="feature-heading-copy">
          <span className="feature-eyebrow">WEBSITE MANAGEMENT</span>
          <h2 id="feature-title">Feature Visibility</h2>
          <p>
            Choose which features visitors can see on the Essential
            Butterfly website.
          </p>
        </div>

        <button
          type="button"
          className="feature-refresh-button"
          onClick={() => void loadFeatures()}
          disabled={loading || saving}
        >
          ↻ <span>Refresh</span>
        </button>
      </header>

      <div className="feature-overview">
        <div>
          <span className="feature-overview-number">
            {visibleCount} <span>/ 3</span>
          </span>
          <span className="feature-overview-label">Features currently visible</span>
        </div>

        <span className="feature-overview-note">
          Changes take effect after saving.
        </span>
      </div>

      {error && (
        <div className="feature-message feature-message-error" role="alert">
          <span>{error}</span>
          <button
            type="button"
            onClick={() => void loadFeatures()}
            disabled={loading || saving}
          >
            Retry
          </button>
        </div>
      )}

      {notice && (
        <div className="feature-message feature-message-success" role="status">
          <span aria-hidden="true">✓</span>
          {notice}
        </div>
      )}

      {loading ? (
        <div className="feature-loading" role="status">
          <span className="feature-spinner" />
          Loading your website settings...
        </div>
      ) : (
        <>
          <div className="feature-card-list">
            {SPRINT_FEATURES.map((definition) => {
              const feature = draft.find(
                (item) => item.featureKey === definition.featureKey
              );

              if (!feature) return null;

              return (
                <article
                  className={`feature-card ${
                    feature.enabled ? "is-enabled" : "is-disabled"
                  }`}
                  key={feature.featureKey}
                >
                  <div className="feature-card-icon" aria-hidden="true">
                    {definition.icon}
                  </div>

                  <div className="feature-card-content">
                    <h3>{definition.label}</h3>
                    <p>{definition.description}</p>
                  </div>

                  <label className="feature-switch">
                    <span className="sr-only">
                      {feature.enabled ? "Hide" : "Show"} {definition.label}
                    </span>

                    <input
                      type="checkbox"
                      checked={feature.enabled}
                      disabled={saving}
                      onChange={(event) =>
                        toggleFeature(
                          feature.featureKey,
                          event.target.checked
                        )
                      }
                    />

                    <span className="feature-switch-track" />
                  </label>

                  <span
                    className={`feature-status ${
                      feature.enabled ? "status-visible" : "status-hidden"
                    }`}
                  >
                    <span className="feature-status-dot" />
                    {feature.enabled ? "Visible" : "Hidden"}
                  </span>
                </article>
              );
            })}
          </div>

          <footer className="feature-save-bar">
            <div className="feature-save-status">
              <span
                className={`feature-save-indicator ${
                  dirty ? "unsaved" : "saved"
                }`}
              />
              <div>
                <strong>
                  {dirty ? "Unsaved changes" : "All changes are saved"}
                </strong>
                <span>
                  {dirty
                    ? "Save your changes to update the website."
                    : "Your public website settings are up to date."}
                </span>
              </div>
            </div>

            <div className="feature-save-actions">
              {dirty && (
                <button
                  type="button"
                  className="feature-discard-button"
                  onClick={discardChanges}
                  disabled={saving}
                >
                  Discard
                </button>
              )}

              <button
                type="button"
                className="feature-save-button"
                onClick={() => void saveChanges()}
                disabled={!dirty || saving}
              >
                {saving ? "Saving..." : "Save changes"}
                {!saving && <span aria-hidden="true"> →</span>}
              </button>
            </div>
          </footer>
        </>
      )}
    </section>
  );
}

export default FeatureVisibility;
