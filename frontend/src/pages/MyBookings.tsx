import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { ApiError, fetchMyAppointments } from "../api/appointmentApi";
import type { ClientAppointment } from "../api/appointmentApi";
import { usePublicFeatures } from "../context/PublicFeatureContext";
import {
  STATUS_LABELS,
  formatDateTime,
  formatRange,
} from "../utils/appointmentFormat";
import "./ClientAppointments.css";

// The client's own bookings: status, meeting link and feedback. Reached only
// from the client dashboard.
function MyBookings() {
  const { isEnabled } = usePublicFeatures();

  const [items, setItems] = useState<ClientAppointment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [open, setOpen] = useState<ClientAppointment | null>(null);

  const dialogRef = useRef<HTMLDialogElement>(null);
  const openerRef = useRef<HTMLElement | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      setItems(await fetchMyAppointments());
    } catch (caught) {
      setError(
        caught instanceof ApiError
          ? caught.message
          : "We could not load your bookings right now. Please try again."
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    const dialog = dialogRef.current;

    if (open && dialog && !dialog.open) {
      dialog.showModal();
    }
  }, [open]);

  const showFeedback = (item: ClientAppointment, button: HTMLElement) => {
    openerRef.current = button;
    setOpen(item);
  };

  const closeFeedback = () => {
    dialogRef.current?.close();
  };

  const afterClose = () => {
    setOpen(null);
    openerRef.current?.focus();
  };

  return (
    <main className="client-bookings-page">
      <div className="client-bookings-wrap">
        <p>
          <Link to="/client-dashboard">← Back to my dashboard</Link>
        </p>

        <h1>My Bookings</h1>

        <p>
          Your appointment requests, their status, meeting links and feedback.
        </p>

        {isEnabled("APPOINTMENTS") && (
          <p>
            <Link to="/appointments" className="client-bookings-link">
              View available appointments →
            </Link>
          </p>
        )}

        {loading && <p role="status">Loading your bookings…</p>}

        {error && (
          <p className="client-bookings-error" role="alert">
            {error}{" "}
            <button type="button" onClick={() => void load()}>
              Try again
            </button>
          </p>
        )}

        {!loading && !error && items.length === 0 && (
          <p>You have no bookings yet.</p>
        )}

        <ul className="client-bookings-list">
          {items.map((item) => {
            const status = STATUS_LABELS[item.status];
            const hasMeeting =
              item.status === "CONFIRMED" || item.status === "COMPLETED";

            return (
              <li key={item.id} className="client-booking">
                <div className="client-booking-head">
                  <strong>{formatDateTime(item.startsAt)}</strong>

                  <span className={`client-booking-status client-booking-${item.status}`}>
                    <span aria-hidden="true">{status?.symbol} </span>
                    <span className="sr-only">Status: </span>
                    {status?.label ?? item.status}
                  </span>
                </div>

                <p className="client-booking-time">
                  {formatRange(item.startsAt, item.endsAt)}
                </p>

                {item.status === "REQUESTED" && (
                  <p>Your request is waiting for a reply.</p>
                )}

                {hasMeeting && (
                  <p>
                    <strong>Meeting link: </strong>
                    {item.meetingLink ? (
                      <a href={item.meetingLink} target="_blank" rel="noopener noreferrer">
                        Open the meeting link (opens in a new tab)
                      </a>
                    ) : (
                      "Not shared yet. It will be emailed to you."
                    )}
                  </p>
                )}

                {item.statusReason && (
                  <p>
                    <strong>
                      {item.status === "DECLINED"
                        ? "Reason for declining: "
                        : "Reason for cancelling: "}
                    </strong>
                    {item.statusReason}
                  </p>
                )}

                <div className="client-booking-feedback-row">
                  <strong>Feedback: </strong>

                  {item.feedback ? (
                    <button
                      type="button"
                      className="client-booking-feedback-link"
                      onClick={(event) => showFeedback(item, event.currentTarget)}
                    >
                      View feedback
                    </button>
                  ) : (
                    <span className="client-booking-feedback-empty">
                      Not available yet
                    </span>
                  )}
                  {item.feedback && item.feedbackUpdatedAt && (
                    <span className="client-booking-time">
                      {" "}
                      Updated {formatDateTime(item.feedbackUpdatedAt)}
                    </span>
                  )}
                </div>
              </li>
            );
          })}
        </ul>
      </div>

      <dialog
        ref={dialogRef}
        className="client-feedback-dialog"
        aria-labelledby="client-feedback-title"
        onClose={afterClose}
      >
        {open && (
          <div>
            <h2 id="client-feedback-title">Feedback</h2>

            <p className="client-booking-time">
              {formatDateTime(open.startsAt)}
            </p>

            <p className="client-feedback-text">{open.feedback}</p>

            {open.feedbackUpdatedAt && (
              <p className="client-booking-time">
                Last updated {formatDateTime(open.feedbackUpdatedAt)}
              </p>
            )}

            <button
              type="button"
              className="client-feedback-close"
              onClick={closeFeedback}
            >
              Close
            </button>
          </div>
        )}
      </dialog>
    </main>
  );
}

export default MyBookings;
