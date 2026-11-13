import { useEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import {
  ApiError,
  addToGoogleCalendar,
  cancelAppointment,
  confirmAppointment,
  declineAppointment,
  resendInvitation,
  saveFeedback,
  sendInvitation,
} from "../api/appointmentApi";
import type { ActionResult, AdminAppointment } from "../api/appointmentApi";
import {
  STATUS_LABELS,
  formatDateTime,
  formatRange,
} from "../utils/appointmentFormat";

type Props = {
  appointment: AdminAppointment;
  // Keeps element ids unique when the same booking is listed twice on a page.
  scope: string;
  // Called after a change: the updated appointment and any warning to show.
  onChanged: (result: ActionResult) => void;
  // Called with any error (the page decides what to do about an expired sign-in).
  onFailure: (error: unknown) => void;
};

type Popup = "none" | "reason" | "invite" | "feedback" | "notes";

// A native <dialog>: focus stays inside, Escape closes it, and focus goes back
// to the link that opened it. Closing never saves anything.
function Popup({
  title,
  labelId,
  onClose,
  children,
}: {
  title: string;
  labelId: string;
  onClose: () => void;
  children: ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;

    if (dialog && !dialog.open) {
      dialog.showModal();
    }
  }, []);

  return (
    <dialog
      ref={ref}
      className="appt-popup"
      aria-labelledby={labelId}
      onClose={onClose}
    >
      <div className="appt-popup-head">
        <h3 id={labelId}>{title}</h3>

        <button
          type="button"
          className="appt-admin-secondary"
          onClick={() => ref.current?.close()}
        >
          Close
        </button>
      </div>

      {children}
    </dialog>
  );
}

function AdminAppointmentCard({
  appointment,
  scope,
  onChanged,
  onFailure,
}: Props) {
  const [popup, setPopup] = useState<Popup>("none");
  const [reasonKind, setReasonKind] = useState<"decline" | "cancel">("decline");
  const [reason, setReason] = useState("");
  const [reasonError, setReasonError] = useState("");

  const [link, setLink] = useState(appointment.meetingLink ?? "");
  const [linkError, setLinkError] = useState("");

  const [text, setText] = useState("");
  const [textError, setTextError] = useState("");

  const [busy, setBusy] = useState(false);

  const openerRef = useRef<HTMLElement | null>(null);

  const status = STATUS_LABELS[appointment.status];
  const id = appointment.id;
  const uid = `${scope}-${appointment.id}`;

  const confirmed = appointment.status === "CONFIRMED";
  const canWrite =
    appointment.status === "CONFIRMED" || appointment.status === "COMPLETED";

  const open = (which: Popup, button: HTMLElement) => {
    openerRef.current = button;

    setReasonError("");
    setLinkError("");
    setTextError("");
    setReason("");
    setLink(appointment.meetingLink ?? "");

    if (which === "feedback") setText(appointment.feedback ?? "");
    if (which === "notes") setText(appointment.privateNotes ?? "");

    setPopup(which);
  };

  // Nothing typed in a popup is kept unless it was saved.
  const closed = () => {
    setPopup("none");
    openerRef.current?.focus();
  };

  const run = async (step: () => Promise<ActionResult>) => {
    setBusy(true);

    try {
      const result = await step();

      setPopup("none");
      setReason("");
      setLink(result.appointment.meetingLink ?? "");

      onChanged(result);
    } catch (error) {
      onFailure(error);
    } finally {
      setBusy(false);
    }
  };

  const submitReason = () => {
    const value = reason.trim();

    if (reasonKind === "decline" && !value) {
      setReasonError("Enter a short reason for declining this request.");
      return;
    }

    setReasonError("");

    void run(() =>
      reasonKind === "decline"
        ? declineAppointment(id, value)
        : cancelAppointment(id, value)
    );
  };

  const submitLink = () => {
    const value = link.trim();

    if (!/^https:\/\/\S+$/i.test(value)) {
      setLinkError(
        "Paste a meeting link that starts with https:// (Google Meet, Teams or any other)."
      );
      return;
    }

    setLinkError("");

    void run(() => sendInvitation(id, value));
  };

  const submitText = async () => {
    const value = text.trim();

    if (!value) {
      setTextError(
        popup === "feedback"
          ? "Write some feedback before saving."
          : "Write a private note before saving."
      );
      return;
    }

    setTextError("");
    setBusy(true);

    try {
      const saved = await saveFeedback(
        id,
        popup === "feedback" ? { feedback: value } : { privateNotes: value }
      );

      setPopup("none");
      onChanged({ appointment: saved, warning: null });
    } catch (error) {
      if (error instanceof ApiError && error.status < 500 && error.status !== 401) {
        setTextError(error.message);
      } else {
        onFailure(error);
      }
    } finally {
      setBusy(false);
    }
  };

  const invitationNote = appointment.invitationSentAt
    ? `Invitation emailed on ${formatDateTime(appointment.invitationSentAt)}.`
    : appointment.meetingLink
      ? "The link is saved but has not been emailed yet."
      : "No invitation has been sent yet.";

  return (
    <article className="appt-admin-card" aria-labelledby={`appt-card-${uid}`}>
      <div className="appt-row">
        <div className="appt-cell appt-cell-wide">
          <span className="appt-cell-label">Client</span>
          <h3 id={`appt-card-${uid}`}>{appointment.clientName}</h3>
        </div>

        <div className="appt-cell appt-cell-wide">
          <span className="appt-cell-label">Appointment</span>
          <span>
            {formatDateTime(appointment.startsAt)}
            <span className="appt-admin-muted">
              {" "}
              ({formatRange(appointment.startsAt, appointment.endsAt)})
            </span>
          </span>
        </div>

        <div className="appt-cell">
          <span className="appt-cell-label">Status</span>
          <span className={`appt-admin-status appt-status-${appointment.status}`}>
            <span aria-hidden="true">{status?.symbol} </span>
            <span className="sr-only">Status: </span>
            {status?.label ?? appointment.status}
          </span>
        </div>

        <div className="appt-cell">
          <span className="appt-cell-label">Email</span>
          <span>{appointment.clientEmail ?? "Not available"}</span>
        </div>

        <div className="appt-cell">
          <span className="appt-cell-label">Phone</span>
          <span>{appointment.clientPhone ?? "Not added"}</span>
        </div>

        <div className="appt-cell">
          <span className="appt-cell-label">Requested</span>
          <span>{formatDateTime(appointment.requestedAt)}</span>
        </div>

        {appointment.statusReason && (
          <div className="appt-cell appt-cell-wide">
            <span className="appt-cell-label">
              {appointment.status === "DECLINED"
                ? "Reason for declining"
                : "Reason for cancelling"}
            </span>
            <span>{appointment.statusReason}</span>
          </div>
        )}
      </div>

      <div className="appt-links">
        {appointment.status === "REQUESTED" && (
          <>
            <button
              type="button"
              className="appt-admin-primary"
              disabled={busy}
              onClick={() => void run(() => confirmAppointment(id))}
            >
              {busy ? "Working…" : "Accept request"}
            </button>

            <button
              type="button"
              className="appt-admin-secondary"
              disabled={busy}
              onClick={(event) => {
                setReasonKind("decline");
                open("reason", event.currentTarget);
              }}
            >
              Decline request
            </button>
          </>
        )}

        {confirmed && (
          <button
            type="button"
            className="appt-link"
            onClick={(event) => open("invite", event.currentTarget)}
          >
            Send invitation
            {appointment.invitationSentAt && (
              <span className="appt-link-note"> (sent)</span>
            )}
          </button>
        )}

        {canWrite && (
          <>
            <button
              type="button"
              className="appt-link"
              onClick={(event) => open("feedback", event.currentTarget)}
            >
              Feedback
              <span className="appt-link-note">
                {appointment.feedback ? " (added)" : " (empty)"}
              </span>
            </button>

            <button
              type="button"
              className="appt-link"
              onClick={(event) => open("notes", event.currentTarget)}
            >
              Private notes
              <span className="appt-link-note">
                {appointment.privateNotes ? " (added)" : " (empty)"}
              </span>
            </button>
          </>
        )}

        {confirmed &&
          (appointment.calendarEventAdded ? (
            <span className="appt-link-note">
              <span aria-hidden="true">✓ </span>On Google Calendar
            </span>
          ) : (
            <button
              type="button"
              className="appt-link"
              disabled={busy}
              onClick={() => void run(() => addToGoogleCalendar(id))}
            >
              Add to Google Calendar
            </button>
          ))}

        {confirmed && appointment.canCancel && (
          <button
            type="button"
            className="appt-link appt-link-danger"
            disabled={busy}
            onClick={(event) => {
              setReasonKind("cancel");
              open("reason", event.currentTarget);
            }}
          >
            Cancel appointment
          </button>
        )}
      </div>

      {appointment.feedbackDue && (
        <p className="appt-admin-needed">
          <span aria-hidden="true">⚑ </span>
          Feedback needed: this appointment is completed.
        </p>
      )}

      {/* ------------------------------------------------ popups */}
      {popup === "reason" && (
        <Popup
          title={
            reasonKind === "decline"
              ? "Decline this request"
              : "Cancel this appointment"
          }
          labelId={`appt-pop-reason-${uid}`}
          onClose={closed}
        >
          <div className="appt-admin-section">
            <label htmlFor={`appt-reason-${uid}`}>
              {reasonKind === "decline"
                ? "Reason for declining (required, shown to the client)"
                : "Reason for cancelling (optional, shown to the client)"}
            </label>

            <textarea
              id={`appt-reason-${uid}`}
              rows={4}
              maxLength={500}
              value={reason}
              aria-invalid={reasonError ? true : undefined}
              aria-describedby={reasonError ? `appt-reason-error-${uid}` : undefined}
              onChange={(event) => setReason(event.target.value)}
            />

            {reasonError && (
              <p id={`appt-reason-error-${uid}`} className="appt-admin-error" role="alert">
                {reasonError}
              </p>
            )}

            <div className="appt-admin-actions">
              <button
                type="button"
                className="appt-admin-danger"
                disabled={busy}
                onClick={submitReason}
              >
                {reasonKind === "decline" ? "Decline request" : "Cancel appointment"}
              </button>
            </div>
          </div>
        </Popup>
      )}

      {popup === "invite" && (
        <Popup
          title="Meeting invitation"
          labelId={`appt-pop-invite-${uid}`}
          onClose={closed}
        >
          <div className="appt-admin-section">
            <label htmlFor={`appt-link-${uid}`}>
              Meeting link (Google Meet, Teams or any https link)
            </label>

            <input
              id={`appt-link-${uid}`}
              type="url"
              value={link}
              maxLength={2000}
              aria-invalid={linkError ? true : undefined}
              aria-describedby={linkError ? `appt-link-error-${uid}` : undefined}
              onChange={(event) => setLink(event.target.value)}
              placeholder="https://meet.google.com/…"
            />

            {linkError && (
              <p id={`appt-link-error-${uid}`} className="appt-admin-error" role="alert">
                {linkError}
              </p>
            )}

            <p className="appt-admin-muted">{invitationNote}</p>

            <div className="appt-admin-actions">
              <button
                type="button"
                className="appt-admin-primary"
                disabled={busy}
                onClick={submitLink}
              >
                Send invitation
              </button>

              {appointment.meetingLink && (
                <button
                  type="button"
                  className="appt-admin-secondary"
                  disabled={busy}
                  onClick={() => void run(() => resendInvitation(id))}
                >
                  Resend saved link
                </button>
              )}
            </div>
          </div>
        </Popup>
      )}

      {(popup === "feedback" || popup === "notes") && (
        <Popup
          title={popup === "feedback" ? "Feedback" : "Private notes"}
          labelId={`appt-pop-text-${uid}`}
          onClose={closed}
        >
          <div className="appt-admin-section">
            <label htmlFor={`appt-text-${uid}`}>
              {popup === "feedback"
                ? "Feedback (the client can read this)"
                : "Private notes (only you can see these)"}
            </label>

            <textarea
              id={`appt-text-${uid}`}
              rows={8}
              maxLength={4000}
              value={text}
              aria-invalid={textError ? true : undefined}
              aria-describedby={textError ? `appt-text-error-${uid}` : undefined}
              onChange={(event) => setText(event.target.value)}
            />

            {popup === "feedback" && appointment.feedbackUpdatedAt && (
              <p className="appt-admin-muted">
                Last updated {formatDateTime(appointment.feedbackUpdatedAt)}.
                The client sees this date.
              </p>
            )}

            {!appointment.started && (
              <p className="appt-admin-muted">
                This can be saved once the appointment has started.
              </p>
            )}

            {textError && (
              <p id={`appt-text-error-${uid}`} className="appt-admin-error" role="alert">
                {textError}
              </p>
            )}

            <p className="appt-admin-muted">
              Closing this window without saving discards what you typed.
            </p>

            <div className="appt-admin-actions">
              <button
                type="button"
                className="appt-admin-primary"
                disabled={busy}
                onClick={() => void submitText()}
              >
                {busy ? "Saving…" : "Save"}
              </button>
            </div>
          </div>
        </Popup>
      )}
    </article>
  );
}

export default AdminAppointmentCard;
