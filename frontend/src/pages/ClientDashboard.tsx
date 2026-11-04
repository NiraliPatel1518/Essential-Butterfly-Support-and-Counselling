import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { apiUrl } from "../api/api";
import "./ClientDashboard.css";
import ClientTestimonials from "./ClientTestimonials";

type IntakeStatus = {
  status: string;
  rejectionReason?: string | null;
};

type ClientProfile = {
  fullName: string;
};

type DashboardSection =
  | "overview"
  | "requests"
  | "testimonials"
  | "resources"
  | "updates"
  | "newsletter"
  | "account";

const statusInformation: Record<
  string,
  { label: string; description: string; step: number }
> = {
  SUBMITTED: {
    label: "Submitted",
    description: "Your intake request has been received.",
    step: 1,
  },
  IN_PROGRESS: {
    label: "Under Review",
    description: "Your intake request is currently being reviewed.",
    step: 2,
  },
  ACCEPTED: {
    label: "Accepted",
    description: "Your intake request has been accepted.",
    step: 3,
  },
  REJECTED: {
    label: "Rejected",
    description: "A decision has been made on your intake request.",
    step: 3,
  },
};

const navigationItems: {
  id: DashboardSection;
  label: string;
  icon: string;
}[] = [
  { id: "overview", label: "Overview", icon: "⌂" },
  { id: "requests", label: "My Requests", icon: "▤" },
  { id: "testimonials", label: "My Testimonials", icon: "❝" },
  { id: "resources", label: "Saved Resources", icon: "▱" },
  { id: "updates", label: "Updates", icon: "♧" },
  { id: "newsletter", label: "Newsletter", icon: "✉" },
  { id: "account", label: "Account Settings", icon: "⚙" },
];

function ClientDashboard() {
  const navigate = useNavigate();

  const [intakeStatus, setIntakeStatus] = useState<IntakeStatus | null>(null);
  const [firstName, setFirstName] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [activeSection, setActiveSection] =
    useState<DashboardSection>("overview");
  const [accountMenuOpen, setAccountMenuOpen] = useState(false);

  const loadStatus = async () => {
    setLoading(true);
    setError("");

    try {
      const token = localStorage.getItem("authToken");

      if (!token) {
        navigate("/login", { replace: true });
        return;
      }

      const response = await fetch(apiUrl("/api/intake/status"), {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });

      if (response.status === 404) {
        setIntakeStatus(null);
        return;
      }

      if (response.status === 401 || response.status === 403) {
        localStorage.removeItem("authToken");
        navigate("/login?sessionExpired=true", { replace: true });
        return;
      }

      if (!response.ok) {
        const message = await response.text();
        throw new Error(message || "Unable to retrieve your intake status.");
      }

      const data = (await response.json()) as IntakeStatus;
      setIntakeStatus(data);
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "Unable to load your intake status."
      );
    } finally {
      setLoading(false);
    }
  };

  const loadClientProfile = async () => {
    try {
      const token = localStorage.getItem("authToken");

      if (!token) {
        navigate("/login", { replace: true });
        return;
      }

      const response = await fetch(apiUrl("/api/auth/me"), {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });

      if (response.status === 401 || response.status === 403) {
        localStorage.removeItem("authToken");
        navigate("/login?sessionExpired=true", { replace: true });
        return;
      }

      if (!response.ok) {
        throw new Error("Unable to load your profile.");
      }

      const data = (await response.json()) as ClientProfile;
      const name = data.fullName.trim();
      const first = name.split(/\s+/)[0];

      setFirstName(first);
    } catch (requestError) {
      console.error("Unable to load client profile:", requestError);
    }
  };

  useEffect(() => {
    void loadStatus();
    void loadClientProfile();
  }, []);

  const currentStatus = intakeStatus
    ? statusInformation[intakeStatus.status]
    : null;

  const selectSection = (section: DashboardSection) => {
    setActiveSection(section);

    const target = document.getElementById(`client-${section}`);
    target?.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  const signOut = () => {
    localStorage.removeItem("authToken");
    navigate("/login", { replace: true });
  };

  return (
    <main className="client-portal">
      <header className="client-portal-header">
        <Link
          to="/"
          className="client-brand"
          aria-label="Essential Butterfly home"
        >
          <span className="client-brand-butterfly" aria-hidden="true">
            🦋
          </span>

          <span className="client-brand-name">
            <strong>Essential Butterfly</strong>
            <small>Support &amp; Counselling</small>
          </span>
        </Link>

        <div className="client-portal-divider" />

        <span className="client-portal-label">Client Portal</span>

        <Link
          to="/"
          className="client-public-site-link"
          aria-label="Visit the Essential Butterfly public website"
        >
          <span aria-hidden="true">↗</span>
          <span>Visit Public Website</span>
        </Link>

        <div className="client-header-actions">
          <button
            type="button"
            className="client-notification-button"
            aria-label="Notifications"
            title="Notifications"
            disabled
          >
            ♧
            <span className="client-notification-dot" />
          </button>

          <button
            type="button"
            className="client-account-button"
            aria-expanded={accountMenuOpen}
            onClick={() => setAccountMenuOpen((open) => !open)}
          >
            <span className="client-avatar">C</span>
            <span>My Account</span>
            <span aria-hidden="true">⌄</span>
          </button>

          {accountMenuOpen && (
            <div className="client-account-menu">
              <button
                type="button"
                onClick={() => {
                  setAccountMenuOpen(false);
                  selectSection("account");
                }}
              >
                Account Settings
              </button>

              <button type="button" onClick={signOut}>
                Sign Out
              </button>
            </div>
          )}
        </div>
      </header>

      <div className="client-portal-layout">
        <aside className="client-sidebar">
          <nav aria-label="Client portal navigation">
            {navigationItems.map((item, index) => (
              <div key={item.id}>
                {index === 6 && (
                  <div className="client-sidebar-divider" />
                )}

                <button
                  type="button"
                  className={`client-sidebar-link ${
                    activeSection === item.id ? "active" : ""
                  }`}
                  onClick={() => selectSection(item.id)}
                  aria-current={
                    activeSection === item.id ? "page" : undefined
                  }
                >
                  <span className="client-sidebar-icon" aria-hidden="true">
                    {item.icon}
                  </span>
                  <span>{item.label}</span>
                </button>
              </div>
            ))}
          </nav>

          <div className="client-sidebar-help">
            <span aria-hidden="true">♡</span>
            <strong>Need support?</strong>
            <p>We're here if you have questions or need help.</p>
            <Link to="/contact">Contact Casey →</Link>
          </div>
        </aside>

        <div className="client-portal-content">
          <section
            id="client-overview"
            className="client-welcome-section"
          >
            <div className="client-welcome-copy">
              <p className="client-eyebrow">
                YOUR PERSONAL SUPPORT SPACE
              </p>

              <h1>
                Welcome back{firstName ? `, ${firstName}` : ""}!
              </h1>

              <p>
                Your personal support space — here to keep you connected,
                informed and supported.
              </p>

              <span className="client-gold-accent" aria-hidden="true" />
            </div>

            <div className="client-welcome-art" aria-hidden="true">
              <span className="client-art-orbit" />
              <span className="client-art-butterfly">🦋</span>
              <span className="client-art-flower">✿</span>
            </div>
          </section>

          <section className="client-quick-actions" aria-label="Quick actions">
            <Link to="/intake" className="client-quick-card">
              <span className="client-quick-icon">▤</span>
              <span className="client-quick-text">
                <strong>Submit an Intake Request</strong>
                <small>Get started with our support services.</small>
              </span>
              <span className="client-quick-arrow">→</span>
            </Link>

            <Link to="/contact" className="client-quick-card">
              <span className="client-quick-icon">✉</span>
              <span className="client-quick-text">
                <strong>Send a Contact Message</strong>
                <small>Have a question or need to connect?</small>
              </span>
              <span className="client-quick-arrow">→</span>
            </Link>

            <button
              type="button"
              className="client-quick-card client-quick-disabled"
              onClick={() => selectSection("testimonials")}
            >
              <span className="client-quick-icon">❝</span>
              <span className="client-quick-text">
                <strong>Share a Testimonial</strong>
                <small>Tell us about your experience.</small>
              </span>
              <span className="client-quick-arrow">→</span>
            </button>

            <button
              type="button"
              className="client-quick-card client-quick-disabled"
              onClick={() => selectSection("resources")}
            >
              <span className="client-quick-icon">▱</span>
              <span className="client-quick-text">
                <strong>Explore Resources</strong>
                <small>Helpful tools and guidance for you.</small>
              </span>
              <span className="client-quick-arrow">→</span>
            </button>

            <button
              type="button"
              className="client-quick-card client-quick-disabled"
              onClick={() => selectSection("updates")}
            >
              <span className="client-quick-icon">♧</span>
              <span className="client-quick-text">
                <strong>View Updates</strong>
                <small>Latest news and announcements.</small>
              </span>
              <span className="client-quick-arrow">→</span>
            </button>
          </section>

          {error && (
            <div className="client-dashboard-alert" role="alert">
              {error}
              <button type="button" onClick={() => void loadStatus()}>
                Try again
              </button>
            </div>
          )}

          <section
            id="client-requests"
            className="client-panel client-requests-panel"
          >
            <div className="client-panel-heading">
              <div className="client-panel-title">
                <span className="client-panel-icon">▤</span>

                <div>
                  <h2>My Requests</h2>
                  <p>
                    View your submitted requests and track their current status.
                  </p>
                </div>
              </div>

              <Link to="/intake" className="client-primary-button">
                <span aria-hidden="true">＋</span> Submit New Request
              </Link>
            </div>

            <div className="client-table-wrap">
              <table className="client-requests-table">
                <thead>
                  <tr>
                    <th scope="col">Request Type</th>
                    <th scope="col">Details</th>
                    <th scope="col">Status</th>
                    <th scope="col">Action</th>
                  </tr>
                </thead>

                <tbody>
                  {loading ? (
                    <tr>
                      <td colSpan={4} className="client-table-message">
                        Loading your requests...
                      </td>
                    </tr>
                  ) : intakeStatus && currentStatus ? (
                    <tr>
                      <td>
                        <span className="client-request-type">
                          <span aria-hidden="true">▤</span>
                          Intake Request
                        </span>
                      </td>

                      <td>Your support request</td>

                      <td>
                        <span
                          className={`client-status-pill client-status-${intakeStatus.status.toLowerCase()}`}
                        >
                          {currentStatus.label}
                        </span>
                      </td>

                      <td>
                        <button
                          type="button"
                          className="client-view-link"
                          onClick={() => selectSection("requests")}
                        >
                          View Details →
                        </button>
                      </td>
                    </tr>
                  ) : (
                    <tr>
                      <td colSpan={4} className="client-table-message">
                        You haven't submitted an intake request yet.
                        <Link to="/intake">
                          {" "}Submit your first request →
                        </Link>
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            {intakeStatus && currentStatus && (
              <div className="client-request-details">
                <div className="client-request-details-heading">
                  <div>
                    <p className="client-eyebrow">
                      INTAKE REQUEST TRACKER
                    </p>
                    <h3>{currentStatus.label}</h3>
                    <p>{currentStatus.description}</p>
                  </div>

                  <button
                    type="button"
                    className="client-refresh-button"
                    onClick={() => void loadStatus()}
                    disabled={loading}
                  >
                    {loading ? "Refreshing..." : "↻ Refresh"}
                  </button>
                </div>

                <div
                  className="client-progress"
                  role="img"
                  aria-label={`Request progress: ${currentStatus.label}`}
                >
                  {[
                    { label: "Submitted", step: 1 },
                    { label: "Under Review", step: 2 },
                    { label: "Decision", step: 3 },
                  ].map((step) => (
                    <div
                      key={step.step}
                      className={`client-progress-step ${
                        currentStatus.step >= step.step ? "completed" : ""
                      } ${
                        intakeStatus.status === "REJECTED" && step.step === 3
                          ? "rejected"
                          : ""
                      }`}
                    >
                      <span>{step.step}</span>
                      <small>{step.label}</small>
                    </div>
                  ))}
                </div>

                {intakeStatus.status === "REJECTED" && (
                  <div className="client-rejection-reason" role="status">
                    <span
                      className="client-rejection-symbol"
                      aria-hidden="true"
                    >
                      !
                    </span>

                    <div>
                      <h4>Reason for rejection</h4>
                      <p>
                        {intakeStatus.rejectionReason?.trim() ||
                          "No rejection reason was provided. Please contact the counselling team for more information."}
                      </p>
                    </div>
                  </div>
                )}
              </div>
            )}
          </section>

          <div className="client-lower-grid">
            <section
              id="client-testimonials"
              className="client-panel client-feature-panel"
            >
              <div className="client-panel-heading">
                <div className="client-panel-title">
                  <span className="client-panel-icon">❝</span>

                  <div>
                    <h2>My Testimonials</h2>
                    <p>
                      Manage testimonials you've submitted and see their status.
                    </p>
                  </div>
                </div>
              </div>

              <ClientTestimonials />
            </section>

            <section
              id="client-newsletter"
              className="client-panel client-feature-panel"
            >
              <div className="client-panel-heading">
                <div className="client-panel-title">
                  <span className="client-panel-icon">✉</span>

                  <div>
                    <h2>Newsletter</h2>
                    <p>
                      Stay in the loop with updates, resources and helpful tips.
                    </p>
                  </div>
                </div>
              </div>

              <div className="client-coming-soon client-newsletter-state">
                <span aria-hidden="true">♡</span>

                <div>
                  <strong>Stay connected</strong>
                  <p>
                    Newsletter subscription management will appear here once
                    it is available.
                  </p>
                </div>
              </div>
            </section>
          </div>

          <div className="client-secondary-grid">
            <section
              id="client-resources"
              className="client-panel client-small-panel"
            >
              <span className="client-panel-icon">▱</span>

              <div>
                <h2>Saved Resources</h2>
                <p>
                  Helpful guides and resources will be available here.
                </p>
              </div>
            </section>

            <section
              id="client-updates"
              className="client-panel client-small-panel"
            >
              <span className="client-panel-icon">♧</span>

              <div>
                <h2>Latest Updates</h2>
                <p>
                  News and announcements from Essential Butterfly will appear
                  here.
                </p>
              </div>
            </section>

            <section
              id="client-account"
              className="client-panel client-small-panel"
            >
              <span className="client-panel-icon">⚙</span>

              <div>
                <h2>Account Settings</h2>
                <p>
                  Need to update your account? Contact the counselling team.
                </p>
                <Link to="/contact">Get in touch →</Link>
              </div>
            </section>
          </div>

          <footer className="client-portal-footer">
            <span>Essential Butterfly Support &amp; Counselling</span>
            <span>Support • Growth • Brighter Tomorrows</span>
          </footer>
        </div>
      </div>
    </main>
  );
}

export default ClientDashboard;