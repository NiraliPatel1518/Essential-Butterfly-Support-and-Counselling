import { useCallback, useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router-dom";
import {
  ApiError,
  fetchAdminAppointments,
  fetchRules,
  fetchSettings,
  removeRule,
  saveRule,
  saveSettings,
} from "../api/appointmentApi";
import type {
  ActionResult,
  AdminAppointment,
  AppointmentSettings,
  AvailabilityRule,
} from "../api/appointmentApi";
import { useAdminSession } from "../hooks/useAdminSession";
import { FRIENDLY_SERVER_ERROR } from "../api/contentApi";
import { WEEKDAYS, formatDay, minutesLabel } from "../utils/appointmentFormat";
import AdminAppointmentCard from "./AdminAppointmentCard";
import "./Dashboard.css";
import "./AdminAppointments.css";

const LENGTHS = [30, 45, 60, 75, 90, 105, 120];

function clock(time: string): string {
  const [hour, minute] = time.split(":").map(Number);

  return new Date(2000, 0, 1, hour, minute).toLocaleTimeString("en-CA", {
    hour: "numeric",
    minute: "2-digit",
  });
}

function messageOf(error: unknown): string {
  return error instanceof ApiError ? error.message : FRIENDLY_SERVER_ERROR;
}

function AdminAppointments() {
  const handleExpiredSession = useAdminSession();

  const [rules, setRules] = useState<AvailabilityRule[]>([]);
  const [settings, setSettings] = useState<AppointmentSettings | null>(null);
  const [hideHours, setHideHours] = useState("3");
  const [pending, setPending] = useState<AdminAppointment[]>([]);
  const [all, setAll] = useState<AdminAppointment[]>([]);

  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");

  const [loading, setLoading] = useState(true);
  const [notice, setNotice] = useState("");
  const [warning, setWarning] = useState("");
  const [error, setError] = useState("");

  // weekly slot form
  const [editingId, setEditingId] = useState<number | null>(null);
  const [mode, setMode] = useState<"week" | "date">("week");
  const [weekday, setWeekday] = useState(1);
  const [slotDate, setSlotDate] = useState("");
  const [startTime, setStartTime] = useState("");
  const [slotMinutes, setSlotMinutes] = useState(60);
  const [ruleError, setRuleError] = useState("");
  const [removingId, setRemovingId] = useState<number | null>(null);
  const [savingRule, setSavingRule] = useState(false);

  // settings form
  const [hoursError, setHoursError] = useState("");
  const [savingHours, setSavingHours] = useState(false);

  const startRef = useRef<HTMLInputElement>(null);
  const dateRef = useRef<HTMLInputElement>(null);
  const hoursRef = useRef<HTMLInputElement>(null);

  const fail = useCallback(
    (caught: unknown) => {
      if (handleExpiredSession(caught)) return;

      setNotice("");
      setError(messageOf(caught));
    },
    [handleExpiredSession]
  );

  const loadAppointments = useCallback(
    async (searchText: string, status: string) => {
      const [waiting, everything] = await Promise.all([
        fetchAdminAppointments("", "REQUESTED"),
        fetchAdminAppointments(searchText, status),
      ]);

      setPending(waiting);
      setAll(everything);
    },
    []
  );

  const loadAll = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      const [loadedRules, loadedSettings] = await Promise.all([
        fetchRules(),
        fetchSettings(),
      ]);

      setRules(loadedRules);
      setSettings(loadedSettings);
      setHideHours(String(loadedSettings.hideHoursBeforeStart));

      await loadAppointments("", "");
    } catch (caught) {
      fail(caught);
    } finally {
      setLoading(false);
    }
  }, [fail, loadAppointments]);

  useEffect(() => {
    void loadAll();
  }, [loadAll]);

  // --------------------------------------------------- weekly availability

  const resetRuleForm = () => {
    setEditingId(null);
    setMode("week");
    setWeekday(1);
    setSlotDate("");
    setStartTime("");
    setSlotMinutes(60);
    setRuleError("");
  };

  const submitRule = async (event: FormEvent) => {
    event.preventDefault();

    if (mode === "date" && !slotDate) {
      setRuleError("Choose the date.");
      dateRef.current?.focus();
      return;
    }

    if (!startTime) {
      setRuleError("Choose the start time.");
      startRef.current?.focus();
      return;
    }

    setRuleError("");
    setSavingRule(true);
    setError("");
    setNotice("");

    try {
      await saveRule(editingId, {
        weekday: mode === "week" ? weekday : null,
        date: mode === "date" ? slotDate : null,
        startTime,
        slotMinutes,
      });

      setRules(await fetchRules());

      setNotice(
        editingId === null
          ? mode === "week"
            ? "Weekly slot added. It now repeats every week."
            : "Slot added for that date."
          : "Slot updated."
      );

      resetRuleForm();
    } catch (caught) {
      if (handleExpiredSession(caught)) return;

      setRuleError(messageOf(caught));
      startRef.current?.focus();
    } finally {
      setSavingRule(false);
    }
  };

  const editRule = (rule: AvailabilityRule) => {
    setEditingId(rule.id);
    setMode(rule.date ? "date" : "week");
    setWeekday(rule.weekday ?? 1);
    setSlotDate(rule.date ?? "");
    setStartTime(rule.startTime);
    setSlotMinutes(rule.slotMinutes);
    setRuleError("");
    setRemovingId(null);

    window.setTimeout(() => startRef.current?.focus(), 0);
  };

  const confirmRemove = async (rule: AvailabilityRule) => {
    setError("");
    setNotice("");

    try {
      await removeRule(rule.id);

      setRules(await fetchRules());
      setRemovingId(null);

      if (editingId === rule.id) resetRuleForm();

      setNotice(
        "Slot removed. Requests already made for it are not affected."
      );
    } catch (caught) {
      fail(caught);
    }
  };

  // ------------------------------------------------------------- settings

  const submitHours = async (event: FormEvent) => {
    event.preventDefault();

    const hours = Number(hideHours);

    if (hideHours.trim() === "" || !Number.isInteger(hours) || hours < 0 || hours > 168) {
      setHoursError("Enter a whole number of hours from 0 to 168.");
      hoursRef.current?.focus();
      return;
    }

    setHoursError("");
    setSavingHours(true);
    setError("");
    setNotice("");

    try {
      const saved = await saveSettings(hours);

      setSettings(saved);
      setHideHours(String(saved.hideHoursBeforeStart));
      setNotice(
        `Unbooked slots now disappear ${saved.hideHoursBeforeStart} hour(s) before they start.`
      );
    } catch (caught) {
      if (handleExpiredSession(caught)) return;

      setHoursError(messageOf(caught));
      hoursRef.current?.focus();
    } finally {
      setSavingHours(false);
    }
  };

  // --------------------------------------------------------- appointments

  const searchBookings = async (event?: FormEvent) => {
    event?.preventDefault();

    setError("");

    try {
      setAll(await fetchAdminAppointments(search, statusFilter));
    } catch (caught) {
      fail(caught);
    }
  };

  const changed = async (result: ActionResult) => {
    setError("");
    setNotice("Saved.");
    setWarning(result.warning ?? "");

    try {
      await loadAppointments(search, statusFilter);
    } catch (caught) {
      fail(caught);
    }
  };

  const rulesByDay = WEEKDAYS.map((name, index) => ({
    name,
    rules: rules.filter((rule) => rule.weekday === index + 1),
  })).filter((day) => day.rules.length > 0);

  const datedRules = rules.filter((rule) => rule.date !== null);

  const today = (() => {
    const now = new Date();
    const month = String(now.getMonth() + 1).padStart(2, "0");
    const day = String(now.getDate()).padStart(2, "0");

    return `${now.getFullYear()}-${month}-${day}`;
  })();

  return (
    <main className="dashboard-page appt-admin-page">
      <div className="dashboard-container">
        <p>
          <Link to="/admin-dashboard">← Back to the admin dashboard</Link>
        </p>

        <header className="dashboard-header">
          <div>
            <p className="dashboard-eyebrow">ESSENTIAL BUTTERFLY</p>
            <h1>Manage Appointments</h1>
            <p className="dashboard-description">
              Set your weekly availability, review booking requests, send
              meeting invitations and write feedback.
            </p>
          </div>

          <button
            type="button"
            className="dashboard-refresh"
            onClick={() => void loadAll()}
            disabled={loading}
          >
            {loading ? "Refreshing…" : "Refresh"}
          </button>
        </header>

        <div aria-live="polite" className="appt-admin-live">
          {notice && (
            <p className="appt-admin-banner appt-admin-banner-ok" role="status">
              <span aria-hidden="true">✓ </span>
              {notice}
            </p>
          )}

          {warning && (
            <p className="appt-admin-banner appt-admin-banner-warn" role="status">
              <span aria-hidden="true">⚠ </span>
              {warning}
            </p>
          )}
        </div>

        {error && (
          <p className="appt-admin-banner appt-admin-banner-error" role="alert">
            <span aria-hidden="true">✕ </span>
            {error}
          </p>
        )}

        {/* ------------------------------------------- weekly availability */}
        <section className="appt-admin-panel" aria-labelledby="appt-rules-title">
          <h2 id="appt-rules-title">Availability</h2>

          <p className="appt-admin-muted">
            A weekly slot repeats every week; a one-date slot appears only on
            the date you choose (today is fine, even a few minutes from now).
            The website shows the next{" "}
            {settings?.bookingWindowDays ?? 7} days
            {settings ? ` (clinic time: ${settings.timeZone})` : ""}.
          </p>

          <form className="appt-admin-form" onSubmit={(event) => void submitRule(event)} noValidate>
            <div>
              <label htmlFor="appt-mode">Repeat</label>
              <select
                id="appt-mode"
                value={mode}
                onChange={(event) =>
                  setMode(event.target.value === "date" ? "date" : "week")
                }
              >
                <option value="week">Every week</option>
                <option value="date">One date only</option>
              </select>
            </div>

            {mode === "week" ? (
              <div>
                <label htmlFor="appt-weekday">Day of the week</label>
                <select
                  id="appt-weekday"
                  value={weekday}
                  onChange={(event) => setWeekday(Number(event.target.value))}
                >
                  {WEEKDAYS.map((name, index) => (
                    <option key={name} value={index + 1}>
                      {name}
                    </option>
                  ))}
                </select>
              </div>
            ) : (
              <div>
                <label htmlFor="appt-date">Date</label>
                <input
                  id="appt-date"
                  ref={dateRef}
                  type="date"
                  min={today}
                  value={slotDate}
                  aria-invalid={ruleError ? true : undefined}
                  aria-describedby={ruleError ? "appt-rule-error" : undefined}
                  onChange={(event) => setSlotDate(event.target.value)}
                />
              </div>
            )}

            <div>
              <label htmlFor="appt-start">Start time</label>
              <input
                id="appt-start"
                ref={startRef}
                type="time"
                step={60}
                value={startTime}
                aria-invalid={ruleError ? true : undefined}
                aria-describedby={ruleError ? "appt-rule-error" : undefined}
                onChange={(event) => setStartTime(event.target.value)}
              />
            </div>

            <div>
              <label htmlFor="appt-length">Slot length</label>
              <select
                id="appt-length"
                value={slotMinutes}
                onChange={(event) => setSlotMinutes(Number(event.target.value))}
              >
                {LENGTHS.map((minutes) => (
                  <option key={minutes} value={minutes}>
                    {minutesLabel(minutes)}
                  </option>
                ))}
              </select>
            </div>

            <div className="appt-admin-form-actions">
              <button type="submit" className="appt-admin-primary" disabled={savingRule}>
                {savingRule
                  ? "Saving…"
                  : editingId === null
                    ? mode === "week"
                      ? "Add weekly slot"
                      : "Add slot for this date"
                    : "Save changes"}
              </button>

              {editingId !== null && (
                <button type="button" className="appt-admin-secondary" onClick={resetRuleForm}>
                  Cancel editing
                </button>
              )}
            </div>
          </form>

          {ruleError && (
            <p id="appt-rule-error" className="appt-admin-error" role="alert">
              {ruleError}
            </p>
          )}

          {rulesByDay.length === 0 && datedRules.length === 0 ? (
            <p className="appt-admin-muted">
              No slots yet. Add one above to open appointments on the
              website.
            </p>
          ) : (
            <div className="appt-admin-rules">
              {rulesByDay.map((day) => (
                <div key={day.name}>
                  <h3>{day.name}</h3>
                  <ul>
                    {day.rules.map((rule) => (
                      <li key={rule.id}>
                        <span>
                          {clock(rule.startTime)} to {clock(rule.endTime)} (
                          {minutesLabel(rule.slotMinutes)})
                        </span>

                        <span className="appt-admin-rule-actions">
                          <button
                            type="button"
                            className="appt-admin-secondary"
                            aria-label={`Edit ${day.name} ${clock(rule.startTime)} slot`}
                            onClick={() => editRule(rule)}
                          >
                            Edit
                          </button>

                          {removingId === rule.id ? (
                            <>
                              <button
                                type="button"
                                className="appt-admin-danger"
                                onClick={() => void confirmRemove(rule)}
                              >
                                Yes, remove
                              </button>
                              <button
                                type="button"
                                className="appt-admin-secondary"
                                onClick={() => setRemovingId(null)}
                              >
                                Keep
                              </button>
                            </>
                          ) : (
                            <button
                              type="button"
                              className="appt-admin-secondary"
                              aria-label={`Remove ${day.name} ${clock(rule.startTime)} slot`}
                              onClick={() => setRemovingId(rule.id)}
                            >
                              Remove
                            </button>
                          )}
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>
              ))}

              {datedRules.length > 0 && (
                <div>
                  <h3>On specific dates</h3>
                  <ul>
                    {datedRules.map((rule) => (
                      <li key={rule.id}>
                        <span>
                          {formatDay(`${rule.date}T00:00:00`)},{" "}
                          {clock(rule.startTime)} to {clock(rule.endTime)} (
                          {minutesLabel(rule.slotMinutes)})
                        </span>

                        <span className="appt-admin-rule-actions">
                          <button
                            type="button"
                            className="appt-admin-secondary"
                            aria-label={`Edit ${rule.date} ${clock(rule.startTime)} slot`}
                            onClick={() => editRule(rule)}
                          >
                            Edit
                          </button>

                          {removingId === rule.id ? (
                            <>
                              <button
                                type="button"
                                className="appt-admin-danger"
                                onClick={() => void confirmRemove(rule)}
                              >
                                Yes, remove
                              </button>
                              <button
                                type="button"
                                className="appt-admin-secondary"
                                onClick={() => setRemovingId(null)}
                              >
                                Keep
                              </button>
                            </>
                          ) : (
                            <button
                              type="button"
                              className="appt-admin-secondary"
                              aria-label={`Remove ${rule.date} ${clock(rule.startTime)} slot`}
                              onClick={() => setRemovingId(rule.id)}
                            >
                              Remove
                            </button>
                          )}
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}

          <h3 className="appt-admin-subhead">When unbooked slots disappear</h3>

          <form className="appt-admin-form" onSubmit={(event) => void submitHours(event)} noValidate>
            <div>
              <label htmlFor="appt-hours">
                Hide an unbooked slot this many hours before it starts
              </label>
              <input
                id="appt-hours"
                ref={hoursRef}
                type="number"
                min={0}
                max={168}
                inputMode="numeric"
                value={hideHours}
                aria-invalid={hoursError ? true : undefined}
                aria-describedby={hoursError ? "appt-hours-error" : undefined}
                onChange={(event) => setHideHours(event.target.value)}
              />
            </div>

            <div className="appt-admin-form-actions">
              <button type="submit" className="appt-admin-primary" disabled={savingHours}>
                {savingHours ? "Saving…" : "Save"}
              </button>
            </div>
          </form>

          {hoursError && (
            <p id="appt-hours-error" className="appt-admin-error" role="alert">
              {hoursError}
            </p>
          )}
        </section>

        {/* ------------------------------------------------ waiting requests */}
        <section className="appt-admin-panel" aria-labelledby="appt-pending-title">
          <h2 id="appt-pending-title">
            Requests waiting for you ({pending.length})
          </h2>

          {pending.length === 0 ? (
            <p className="appt-admin-muted">No requests are waiting.</p>
          ) : (
            <div className="appt-admin-list">
              {pending.map((appointment) => (
                <AdminAppointmentCard
                  key={appointment.id}
                  scope="waiting"
                  appointment={appointment}
                  onChanged={(result) => void changed(result)}
                  onFailure={fail}
                />
              ))}
            </div>
          )}
        </section>

        {/* ------------------------------------------------- all bookings */}
        <section className="appt-admin-panel" aria-labelledby="appt-all-title">
          <h2 id="appt-all-title">All bookings and feedback</h2>

          <form
            className="appt-admin-form"
            role="search"
            onSubmit={(event) => void searchBookings(event)}
          >
            <div>
              <label htmlFor="appt-search">
                Search customers (name, email or phone)
              </label>
              <input
                id="appt-search"
                type="search"
                value={search}
                maxLength={80}
                onChange={(event) => setSearch(event.target.value)}
              />
            </div>

            <div>
              <label htmlFor="appt-status">Status</label>
              <select
                id="appt-status"
                value={statusFilter}
                onChange={(event) => setStatusFilter(event.target.value)}
              >
                <option value="">All statuses</option>
                <option value="REQUESTED">Requested</option>
                <option value="CONFIRMED">Confirmed</option>
                <option value="DECLINED">Declined</option>
                <option value="CANCELLED">Cancelled</option>
                <option value="COMPLETED">Completed</option>
              </select>
            </div>

            <div className="appt-admin-form-actions">
              <button type="submit" className="appt-admin-primary">
                Search
              </button>
            </div>
          </form>

          <p role="status" className="appt-admin-muted">
            {all.length === 1 ? "1 booking found." : `${all.length} bookings found.`}
          </p>

          <div className="appt-admin-list">
            {all.map((appointment) => (
              <AdminAppointmentCard
                key={appointment.id}
                scope="all"
                appointment={appointment}
                onChanged={(result) => void changed(result)}
                onFailure={fail}
              />
            ))}
          </div>
        </section>
      </div>
    </main>
  );
}

export default AdminAppointments;
