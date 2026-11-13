import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  ApiError,
  adminSignedIn,
  clientSignedIn,
  fetchClientProfile,
  fetchOpenSlots,
  requestAppointment,
} from "../api/appointmentApi";
import type { ClientProfile, OpenSlot } from "../api/appointmentApi";
import {
  dayKey,
  formatDay,
  formatRange,
  formatTime,
  minutesLabel,
} from "../utils/appointmentFormat";
import "./Appointments.css";

const RETURN_PATH = "/appointments";

type Outcome = {
  kind: "success" | "error";
  title: string;
  message: string;
};

function Appointments() {
  const navigate = useNavigate();

  const [slots, setSlots] = useState<OpenSlot[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");

  const [selected, setSelected] = useState<OpenSlot | null>(null);
  const [profile, setProfile] = useState<ClientProfile | null>(null);
  const [profileLoading, setProfileLoading] = useState(false);
  const [profileError, setProfileError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const [outcome, setOutcome] = useState<Outcome | null>(null);

  const bookingDialog = useRef<HTMLDialogElement>(null);
  const outcomeDialog = useRef<HTMLDialogElement>(null);
  const lastTrigger = useRef<HTMLElement | null>(null);

  const loadSlots = useCallback(async () => {
    setLoading(true);
    setLoadError("");

    try {
      setSlots(await fetchOpenSlots());
    } catch (error) {
      setLoadError(
        error instanceof ApiError
          ? error.message
          : "We could not load the available times right now. Please try again."
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadSlots();
  }, [loadSlots]);

  const days = useMemo(() => {
    const grouped = new Map<string, OpenSlot[]>();

    for (const slot of slots) {
      const key = dayKey(slot.startsAt);
      grouped.set(key, [...(grouped.get(key) ?? []), slot]);
    }

    return Array.from(grouped.entries());
  }, [slots]);

  // ------------------------------------------------- dialog open and close

  useEffect(() => {
    const dialog = bookingDialog.current;

    if (!dialog || typeof dialog.showModal !== "function") return;

    if (selected && !dialog.open) {
      dialog.showModal();
    }

    if (!selected && dialog.open) {
      dialog.close();
    }
  }, [selected]);

  useEffect(() => {
    const dialog = outcomeDialog.current;

    if (!dialog || typeof dialog.showModal !== "function") return;

    if (outcome && !dialog.open) {
      dialog.showModal();
    }

    if (!outcome && dialog.open) {
      dialog.close();
    }
  }, [outcome]);

  const restoreFocus = () => {
    window.setTimeout(() => lastTrigger.current?.focus(), 0);
  };

  const sessionExpired = useCallback(() => {
    localStorage.removeItem("authToken");
    window.dispatchEvent(new Event("auth-changed"));

    navigate(
      `/login?sessionExpired=true&returnTo=${encodeURIComponent(RETURN_PATH)}`,
      { replace: true }
    );
  }, [navigate]);

  // ------------------------------------------------------- choosing a slot

  const chooseSlot = async (slot: OpenSlot, trigger: HTMLElement) => {
    lastTrigger.current = trigger;

    // Not signed in: sign up (or log in), then come back to this page.
    if (!clientSignedIn()) {
      if (adminSignedIn()) {
        setOutcome({
          kind: "error",
          title: "Client account needed",
          message:
            "You are signed in as an administrator. Appointments are booked from a client account.",
        });
        return;
      }

      navigate(`/signup?returnTo=${encodeURIComponent(RETURN_PATH)}`);
      return;
    }

    setSelected(slot);
    setProfileError("");

    if (profile) return;

    setProfileLoading(true);

    try {
      setProfile(await fetchClientProfile());
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        sessionExpired();
        return;
      }

      setProfileError(
        error instanceof ApiError
          ? error.message
          : "We could not load your details. Please try again."
      );
    } finally {
      setProfileLoading(false);
    }
  };

  const closeBooking = () => {
    setSelected(null);
    restoreFocus();
  };

  const closeOutcome = () => {
    setOutcome(null);
    restoreFocus();
  };

  // ------------------------------------------------------- requesting it

  const submitRequest = async () => {
    if (!selected || submitting) return;

    setSubmitting(true);

    try {
      await requestAppointment(selected.startsAt);

      setSelected(null);

      setOutcome({
        kind: "success",
        title: "Request sent",
        message:
          "Your appointment request has been sent. You will see its status, and any message from us, under My Bookings on your dashboard.",
      });

      void loadSlots();
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        sessionExpired();
        return;
      }

      const taken = error instanceof ApiError && error.status === 409;

      if (taken) {
        setSelected(null);
        void loadSlots();
      }

      setOutcome({
        kind: "error",
        title: taken ? "That time is taken" : "We could not send your request",
        message:
          error instanceof ApiError
            ? error.message
            : "Something went wrong. Please try again.",
      });
    } finally {
      setSubmitting(false);
    }
  };

  const phoneText = profile?.phoneNumber ?? "Not added to your account yet";

  return (
    <main className="appt-page">
      <header className="appt-header">
        <p className="appt-eyebrow">ESSENTIAL BUTTERFLY</p>
        <h1>Available appointments</h1>
        <p>
          Choose a time that suits you and send a booking request. Casey
          reviews every request and you will see the answer on your
          dashboard. If you are not signed in yet, you will be asked to create
          an account first.
        </p>
      </header>

      <div role="status" aria-live="polite" className="sr-only">
        {loading
          ? "Loading available appointment times."
          : `${slots.length} appointment times available.`}
      </div>

      {loadError && (
        <div className="appt-message appt-message-error" role="alert">
          <span>{loadError}</span>
          <button type="button" onClick={() => void loadSlots()}>
            Try again
          </button>
        </div>
      )}

      {loading && !loadError && (
        <p className="appt-loading">Loading available times…</p>
      )}

      {!loading && !loadError && days.length === 0 && (
        <div className="appt-empty">
          <h2>No open times right now</h2>
          <p>
            There are no appointment times open at the moment. Please check
            back soon, or <Link to="/contact">send us a message</Link>.
          </p>
        </div>
      )}

      {!loading && days.length > 0 && (
        <div className="appt-days">
          {days.map(([key, daySlots]) => (
            <section
              key={key}
              className="appt-day"
              aria-labelledby={`appt-day-${key}`}
            >
              <h2 id={`appt-day-${key}`}>{formatDay(daySlots[0].startsAt)}</h2>

              <ul className="appt-slot-list">
                {daySlots.map((slot) => (
                  <li key={slot.startsAt}>
                    <button
                      type="button"
                      className="appt-slot"
                      aria-label={`${formatDay(slot.startsAt)}, ${formatRange(
                        slot.startsAt,
                        slot.endsAt
                      )}, ${minutesLabel(slot.minutes)}. Select this time.`}
                      onClick={(event) =>
                        void chooseSlot(slot, event.currentTarget)
                      }
                    >
                      <span className="appt-slot-time">
                        {formatTime(slot.startsAt)}
                      </span>
                      <span className="appt-slot-length">
                        {minutesLabel(slot.minutes)}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>
      )}

      {/* ---------------------------------------------- booking pop-up */}
      <dialog
        ref={bookingDialog}
        className="appt-dialog"
        aria-labelledby="appt-booking-title"
        onClose={() => {
          if (selected) closeBooking();
        }}
      >
        {selected && (
          <form
            onSubmit={(event) => {
              event.preventDefault();
              void submitRequest();
            }}
          >
            <h2 id="appt-booking-title">Request this appointment</h2>

            <p className="appt-dialog-note">
              These details come from your account and cannot be changed
              here.
            </p>

            {profileError && (
              <p className="appt-message appt-message-error" role="alert">
                {profileError}
              </p>
            )}

            <div className="appt-fields">
              <label>
                Date
                <input readOnly value={formatDay(selected.startsAt)} />
              </label>

              <label>
                Time
                <input
                  readOnly
                  value={`${formatRange(selected.startsAt, selected.endsAt)} (${minutesLabel(
                    selected.minutes
                  )})`}
                />
              </label>

              <label>
                Name
                <input
                  readOnly
                  value={profileLoading ? "Loading…" : profile?.fullName ?? ""}
                />
              </label>

              <label>
                Email
                <input
                  readOnly
                  value={profileLoading ? "Loading…" : profile?.email ?? ""}
                />
              </label>

              <label>
                Contact number
                <input
                  readOnly
                  value={profileLoading ? "Loading…" : phoneText}
                />
              </label>
            </div>

            <div className="appt-dialog-actions">
              <button
                type="submit"
                className="appt-primary"
                disabled={submitting || profileLoading || !profile}
              >
                {submitting ? "Sending…" : "Request a booking"}
              </button>

              <button
                type="button"
                className="appt-secondary"
                onClick={closeBooking}
                disabled={submitting}
              >
                Cancel
              </button>
            </div>
          </form>
        )}
      </dialog>

      {/* ------------------------------------------ success / error pop-up */}
      <dialog
        ref={outcomeDialog}
        className="appt-dialog"
        aria-labelledby="appt-outcome-title"
        aria-describedby="appt-outcome-message"
        onClose={() => {
          if (outcome) closeOutcome();
        }}
      >
        {outcome && (
          <div role={outcome.kind === "error" ? "alert" : "status"}>
            <h2 id="appt-outcome-title">
              <span aria-hidden="true">
                {outcome.kind === "success" ? "✓ " : "⚠ "}
              </span>
              {outcome.title}
            </h2>

            <p id="appt-outcome-message">{outcome.message}</p>

            <div className="appt-dialog-actions">
              {outcome.kind === "success" && (
                <Link to="/client-dashboard" className="appt-primary appt-link">
                  Go to My Bookings
                </Link>
              )}

              <button
                type="button"
                className="appt-secondary"
                onClick={closeOutcome}
              >
                Close
              </button>
            </div>
          </div>
        )}
      </dialog>
    </main>
  );
}

export default Appointments;
