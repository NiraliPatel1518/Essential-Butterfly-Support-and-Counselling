import { useCallback, useEffect, useState } from "react";
import { apiUrl } from "../api/api";
import FeatureVisibility from "./FeatureVisibility";
import AdminUpdates from "./AdminUpdates";
import AdminTestimonials from "./AdminTestimonials";
import "./Dashboard.css";

type ContactMessage = {
  id: number;
  fullName: string;
  email: string;
  subject: string;
  message: string;
  status: "NEW" | "VIEWED" | string;
  createdAt: string;
};

type IntakeSubmission = {
  id: number;
  clientFullName: string;
  parentGuardianName?: string;
  contactInfo: string;
  preferredContactMethod: string;
  overview: string;
  supportNeeds: string;
  respiteGoals?: string;
  subjectFocus?: string;
  additionalNotes?: string;
  status: "SUBMITTED" | "IN_PROGRESS" | "ACCEPTED" | "REJECTED" | string;
  rejectionReason?: string;
  createdAt: string;
};

type Section = "contacts" | "intakes" | "features" | "updates" | "testimonials";

async function readError(response: Response): Promise<string> {
  const body = await response.text();

  if (!body) {
    return `Request failed (${response.status}).`;
  }

  try {
    const parsed = JSON.parse(body) as {
      message?: string;
      error?: string;
    };

    return parsed.message || parsed.error || body;
  } catch {
    return body;
  }
}

function formatDate(value: string): string {
  if (!value) return "Date unavailable";

  const date = new Date(value);

  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleString();
}

function statusLabel(status: string): string {
  const labels: Record<string, string> = {
    NEW: "New",
    VIEWED: "Viewed",
    SUBMITTED: "Submitted",
    IN_PROGRESS: "Under Review",
    ACCEPTED: "Accepted",
    REJECTED: "Rejected",
  };

  return labels[status] || status.replaceAll("_", " ");
}

function AdminDashboard() {
  const [section, setSection] = useState<Section>("contacts");

  const [contacts, setContacts] = useState<ContactMessage[]>([]);
  const [intakes, setIntakes] = useState<IntakeSubmission[]>([]);

  const [selectedContact, setSelectedContact] =
    useState<ContactMessage | null>(null);

  const [selectedIntake, setSelectedIntake] =
    useState<IntakeSubmission | null>(null);

  const [rejectionReason, setRejectionReason] = useState("");
  const [showRejectForm, setShowRejectForm] = useState(false);

  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const token = localStorage.getItem("adminToken");

  const adminHeaders = {
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json",
  };

  const loadContacts = useCallback(async () => {
    const response = await fetch(
      apiUrl("/api/admin/contact-messages"),
      {
        headers: adminHeaders,
      }
    );

    if (!response.ok) {
      throw new Error(await readError(response));
    }

    const data = (await response.json()) as ContactMessage[];
    setContacts(data);
  }, [token]);

  const loadIntakes = useCallback(async () => {
    const response = await fetch(
      apiUrl("/api/admin/intake-submissions"),
      {
        headers: adminHeaders,
      }
    );

    if (!response.ok) {
      throw new Error(await readError(response));
    }

    const data = (await response.json()) as IntakeSubmission[];
    setIntakes(data);
  }, [token]);

  const refreshCurrentSection = useCallback(async () => {
    if (section === "features" || section === "updates" || section === "testimonials") {
      return;
    }

    setLoading(true);
    setError("");

    try {
      if (section === "contacts") {
        await loadContacts();
      } else {
        await loadIntakes();
      }
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "Unable to load dashboard data."
      );
    } finally {
      setLoading(false);
    }
  }, [section, loadContacts, loadIntakes]);

  useEffect(() => {
    void refreshCurrentSection();
  }, [refreshCurrentSection]);

  const openContact = async (id: number) => {
    setError("");
    setNotice("");
    setSelectedContact(null);

    try {
      const response = await fetch(
        apiUrl(`/api/admin/contact-messages/${id}`),
        {
          headers: adminHeaders,
        }
      );

      if (!response.ok) {
        throw new Error(await readError(response));
      }

      const message = (await response.json()) as ContactMessage;

      setSelectedContact(message);

      setContacts((previous) =>
        previous.map((item) =>
          item.id === message.id ? message : item
        )
      );
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "Unable to open contact message."
      );
    }
  };

  const openIntake = async (id: number) => {
    setError("");
    setNotice("");
    setSelectedIntake(null);
    setShowRejectForm(false);
    setRejectionReason("");

    try {
      const response = await fetch(
        apiUrl(`/api/admin/intake-submissions/${id}`),
        {
          headers: adminHeaders,
        }
      );

      if (!response.ok) {
        throw new Error(await readError(response));
      }

      const submission = (await response.json()) as IntakeSubmission;

      setSelectedIntake(submission);

      setIntakes((previous) =>
        previous.map((item) =>
          item.id === submission.id ? submission : item
        )
      );
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "Unable to open intake request."
      );
    }
  };

  const acceptIntake = async () => {
    if (!selectedIntake) return;

    setActionLoading(true);
    setError("");
    setNotice("");

    try {
      const response = await fetch(
        apiUrl(
          `/api/admin/intake-submissions/${selectedIntake.id}/accept`
        ),
        {
          method: "POST",
          headers: adminHeaders,
        }
      );

      if (!response.ok) {
        throw new Error(await readError(response));
      }

      const updated = (await response.json()) as IntakeSubmission;

      setSelectedIntake(updated);
      setIntakes((previous) =>
        previous.map((item) =>
          item.id === updated.id ? updated : item
        )
      );

      setNotice("Intake request accepted successfully.");
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "Unable to accept intake request."
      );
    } finally {
      setActionLoading(false);
    }
  };

  const rejectIntake = async () => {
    if (!selectedIntake) return;

    if (!rejectionReason.trim()) {
      setError("Please enter a rejection reason.");
      return;
    }

    setActionLoading(true);
    setError("");
    setNotice("");

    try {
      const response = await fetch(
        apiUrl(
          `/api/admin/intake-submissions/${selectedIntake.id}/reject`
        ),
        {
          method: "POST",
          headers: adminHeaders,
          body: JSON.stringify({
            rejectionReason: rejectionReason.trim(),
          }),
        }
      );

      if (!response.ok) {
        throw new Error(await readError(response));
      }

      const updated = (await response.json()) as IntakeSubmission;

      setSelectedIntake(updated);
      setIntakes((previous) =>
        previous.map((item) =>
          item.id === updated.id ? updated : item
        )
      );

      setShowRejectForm(false);
      setRejectionReason("");
      setNotice("Intake request rejected successfully.");
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "Unable to reject intake request."
      );
    } finally {
      setActionLoading(false);
    }
  };

  const changeSection = (nextSection: Section) => {
    setSection(nextSection);
    setSelectedContact(null);
    setSelectedIntake(null);
    setShowRejectForm(false);
    setRejectionReason("");
    setError("");
    setNotice("");
  };

  const pendingIntakes = intakes.filter(
    (item) =>
      item.status === "SUBMITTED" ||
      item.status === "IN_PROGRESS"
  ).length;

  const newContacts = contacts.filter(
    (item) => item.status === "NEW"
  ).length;

  return (
    <main className="dashboard-page">
      <div className="dashboard-container">
        <header className="dashboard-header">
          <div>
            <p className="dashboard-eyebrow">ESSENTIAL BUTTERFLY</p>
            <h1>Admin Dashboard</h1>
            <p className="dashboard-description">
              Manage contact messages, client intake requests and
              public website features.
            </p>
          </div>

          {section !== "features" && section !== "updates" && (
            <button
              type="button"
              className="dashboard-refresh"
              onClick={() => void refreshCurrentSection()}
              disabled={loading}
            >
              {loading ? "Refreshing..." : "Refresh"}
            </button>
          )}
        </header>

        <section
          className="dashboard-summary"
          aria-label="Dashboard overview"
        >
          <article className="dashboard-summary-card">
            <span>New Contact Messages</span>
            <strong>{newContacts}</strong>
          </article>

          <article className="dashboard-summary-card">
            <span>Pending Intake Requests</span>
            <strong>{pendingIntakes}</strong>
          </article>
        </section>

        <nav
          className="dashboard-tabs"
          aria-label="Admin dashboard sections"
        >
          <button
            type="button"
            className={section === "contacts" ? "active" : ""}
            aria-current={section === "contacts" ? "page" : undefined}
            onClick={() => changeSection("contacts")}
          >
            Contact Messages
          </button>

          <button
            type="button"
            className={section === "intakes" ? "active" : ""}
            aria-current={section === "intakes" ? "page" : undefined}
            onClick={() => changeSection("intakes")}
          >
            Intake Requests
          </button>

          <button
            type="button"
            className={section === "features" ? "active" : ""}
            aria-current={section === "features" ? "page" : undefined}
            onClick={() => changeSection("features")}
          >
            Feature Controls
          </button>

          <button
            type="button"
            className={section === "updates" ? "active" : ""}
            aria-current={section === "updates" ? "page" : undefined}
            onClick={() => changeSection("updates")}
          >
            Updates
          </button>
          <button
            type="button"
            className={section === "testimonials" ? "active" : ""}
            aria-current={section === "testimonials" ? "page" : undefined}
            onClick={() => changeSection("testimonials")}
          >
            Testimonials
          </button>
        </nav>

        {error && (
          <div
            className="dashboard-alert dashboard-alert-error"
            role="alert"
          >
            {error}
          </div>
        )}

        {notice && (
          <div
            className="dashboard-alert dashboard-alert-success"
            role="status"
          >
            {notice}
          </div>
        )}

        {loading && (
          <p className="dashboard-loading" role="status">
            Loading dashboard information...
          </p>
        )}

        {!loading && section === "contacts" && (
          <section className="dashboard-section">
            <div className="dashboard-section-heading">
              <div>
                <h2>Contact Messages</h2>
                <p>
                  Messages submitted through the public contact form.
                  Opening a message marks it as viewed.
                </p>
              </div>

              <span className="dashboard-count">
                {contacts.length} total
              </span>
            </div>

            {contacts.length === 0 ? (
              <div className="dashboard-empty">
                No contact messages have been received yet.
              </div>
            ) : (
              <div className="dashboard-table-wrapper">
                <table className="dashboard-table">
                  <thead>
                    <tr>
                      <th scope="col">Name</th>
                      <th scope="col">Subject</th>
                      <th scope="col">Submitted</th>
                      <th scope="col">Status</th>
                      <th scope="col">Action</th>
                    </tr>
                  </thead>

                  <tbody>
                    {contacts.map((message) => (
                      <tr key={message.id}>
                        <td>{message.fullName}</td>
                        <td>{message.subject}</td>
                        <td>{formatDate(message.createdAt)}</td>
                        <td>
                          <span
                            className={`dashboard-status status-${message.status.toLowerCase()}`}
                          >
                            {statusLabel(message.status)}
                          </span>
                        </td>
                        <td>
                          <button
                            type="button"
                            className="dashboard-text-button"
                            onClick={() => void openContact(message.id)}
                          >
                            View message
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {selectedContact && (
              <section
                className="dashboard-detail"
                aria-labelledby="contact-detail-title"
              >
                <div className="dashboard-detail-header">
                  <div>
                    <p className="dashboard-eyebrow">
                      CONTACT MESSAGE
                    </p>
                    <h3 id="contact-detail-title">
                      {selectedContact.subject}
                    </h3>
                  </div>

                  <button
                    type="button"
                    className="dashboard-close"
                    onClick={() => setSelectedContact(null)}
                  >
                    Close
                  </button>
                </div>

                <dl className="dashboard-detail-grid">
                  <div>
                    <dt>Name</dt>
                    <dd>{selectedContact.fullName}</dd>
                  </div>

                  <div>
                    <dt>Email</dt>
                    <dd>
                      <a href={`mailto:${selectedContact.email}`}>
                        {selectedContact.email}
                      </a>
                    </dd>
                  </div>

                  <div>
                    <dt>Submitted</dt>
                    <dd>{formatDate(selectedContact.createdAt)}</dd>
                  </div>

                  <div>
                    <dt>Admin status</dt>
                    <dd>{statusLabel(selectedContact.status)}</dd>
                  </div>
                </dl>

                <div className="dashboard-message-content">
                  <h4>Message</h4>
                  <p>{selectedContact.message}</p>
                </div>
              </section>
            )}
          </section>
        )}

        {!loading && section === "intakes" && (
          <section className="dashboard-section">
            <div className="dashboard-section-heading">
              <div>
                <h2>Intake Requests</h2>
                <p>
                  Review submissions and record an acceptance or rejection
                  decision.
                </p>
              </div>

              <span className="dashboard-count">
                {intakes.length} total
              </span>
            </div>

            {intakes.length === 0 ? (
              <div className="dashboard-empty">
                No intake requests have been submitted yet.
              </div>
            ) : (
              <div className="dashboard-table-wrapper">
                <table className="dashboard-table">
                  <thead>
                    <tr>
                      <th scope="col">Client</th>
                      <th scope="col">Contact</th>
                      <th scope="col">Submitted</th>
                      <th scope="col">Status</th>
                      <th scope="col">Action</th>
                    </tr>
                  </thead>

                  <tbody>
                    {intakes.map((submission) => (
                      <tr key={submission.id}>
                        <td>{submission.clientFullName}</td>
                        <td>{submission.contactInfo}</td>
                        <td>{formatDate(submission.createdAt)}</td>
                        <td>
                          <span
                            className={`dashboard-status status-${submission.status.toLowerCase()}`}
                          >
                            {statusLabel(submission.status)}
                          </span>
                        </td>
                        <td>
                          <button
                            type="button"
                            className="dashboard-text-button"
                            onClick={() => void openIntake(submission.id)}
                          >
                            Review request
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {selectedIntake && (
              <section
                className="dashboard-detail"
                aria-labelledby="intake-detail-title"
              >
                <div className="dashboard-detail-header">
                  <div>
                    <p className="dashboard-eyebrow">INTAKE REQUEST</p>
                    <h3 id="intake-detail-title">
                      {selectedIntake.clientFullName}
                    </h3>
                    <span
                      className={`dashboard-status status-${selectedIntake.status.toLowerCase()}`}
                    >
                      {statusLabel(selectedIntake.status)}
                    </span>
                  </div>

                  <button
                    type="button"
                    className="dashboard-close"
                    onClick={() => {
                      setSelectedIntake(null);
                      setShowRejectForm(false);
                    }}
                  >
                    Close
                  </button>
                </div>

                <dl className="dashboard-detail-grid">
                  <div>
                    <dt>Parent / Guardian</dt>
                    <dd>
                      {selectedIntake.parentGuardianName || "Not provided"}
                    </dd>
                  </div>

                  <div>
                    <dt>Contact information</dt>
                    <dd>{selectedIntake.contactInfo}</dd>
                  </div>

                  <div>
                    <dt>Preferred contact method</dt>
                    <dd>{selectedIntake.preferredContactMethod}</dd>
                  </div>

                  <div>
                    <dt>Submitted</dt>
                    <dd>{formatDate(selectedIntake.createdAt)}</dd>
                  </div>
                </dl>

                <div className="dashboard-message-content">
                  <h4>Overview</h4>
                  <p>{selectedIntake.overview}</p>

                  <h4>Support needs</h4>
                  <p>{selectedIntake.supportNeeds}</p>

                  <h4>Respite goals</h4>
                  <p>{selectedIntake.respiteGoals || "Not provided"}</p>

                  <h4>Subject focus</h4>
                  <p>{selectedIntake.subjectFocus || "Not provided"}</p>

                  <h4>Additional notes</h4>
                  <p>{selectedIntake.additionalNotes || "Not provided"}</p>
                </div>

                {(selectedIntake.status === "SUBMITTED" ||
                  selectedIntake.status === "IN_PROGRESS") && (
                  <div className="dashboard-actions">
                    <button
                      type="button"
                      className="dashboard-primary-button"
                      onClick={() => void acceptIntake()}
                      disabled={actionLoading}
                    >
                      {actionLoading ? "Processing..." : "Accept Request"}
                    </button>

                    <button
                      type="button"
                      className="dashboard-danger-button"
                      onClick={() => {
                        setShowRejectForm((previous) => !previous);
                        setError("");
                      }}
                      disabled={actionLoading}
                    >
                      Reject Request
                    </button>
                  </div>
                )}

                {showRejectForm && (
                  <form
                    className="dashboard-rejection-form"
                    onSubmit={(event) => {
                      event.preventDefault();
                      void rejectIntake();
                    }}
                  >
                    <label htmlFor="rejection-reason">
                      Rejection reason (private admin record)
                    </label>

                    <textarea
                      id="rejection-reason"
                      value={rejectionReason}
                      onChange={(event) =>
                        setRejectionReason(event.target.value)
                      }
                      rows={4}
                      maxLength={2000}
                      required
                      placeholder="Enter the reason for rejecting this request..."
                    />

                    <div className="dashboard-actions">
                      <button
                        type="submit"
                        className="dashboard-danger-button"
                        disabled={
                          actionLoading || !rejectionReason.trim()
                        }
                      >
                        {actionLoading
                          ? "Processing..."
                          : "Confirm Rejection"}
                      </button>

                      <button
                        type="button"
                        className="dashboard-secondary-button"
                        onClick={() => {
                          setShowRejectForm(false);
                          setRejectionReason("");
                        }}
                      >
                        Cancel
                      </button>
                    </div>
                  </form>
                )}

                {selectedIntake.status === "REJECTED" &&
                  selectedIntake.rejectionReason && (
                    <div className="dashboard-private-note">
                      <h4>Private rejection reason — Admin only</h4>
                      <p>{selectedIntake.rejectionReason}</p>
                    </div>
                  )}
              </section>
            )}
          </section>
        )}

        {!loading && section === "features" && (
          <section className="dashboard-section">
            <FeatureVisibility />
          </section>
        )}

        {!loading && section === "updates" && (
          <section className="dashboard-section">
            <AdminUpdates />
          </section>
        )}

        {!loading && section === "testimonials" && (
          <section className="dashboard-section">
            <AdminTestimonials />
          </section>
        )}
      </div>
    </main>
  );
}

export default AdminDashboard;