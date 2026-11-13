/*
 * Appointment times arrive as the clinic's local wall-clock time
 * ("2026-11-16T10:00:00", no time zone). They are shown exactly as written.
 */

export const WEEKDAYS = [
  "Monday",
  "Tuesday",
  "Wednesday",
  "Thursday",
  "Friday",
  "Saturday",
  "Sunday",
];

export function parseLocal(value: string): Date {
  const [datePart, timePart = "00:00"] = value.split("T");
  const [year, month, day] = datePart.split("-").map(Number);
  const [hour, minute] = timePart.split(":").map(Number);

  return new Date(year, month - 1, day, hour, minute);
}

export function formatDay(value: string): string {
  return parseLocal(value).toLocaleDateString("en-CA", {
    weekday: "long",
    month: "long",
    day: "numeric",
    year: "numeric",
  });
}

export function formatTime(value: string): string {
  return parseLocal(value).toLocaleTimeString("en-CA", {
    hour: "numeric",
    minute: "2-digit",
  });
}

export function formatRange(startsAt: string, endsAt: string): string {
  return `${formatTime(startsAt)} to ${formatTime(endsAt)}`;
}

export function formatDateTime(value: string): string {
  return `${formatDay(value)}, ${formatTime(value)}`;
}

// "2026-11-16" for grouping slots by day.
export function dayKey(value: string): string {
  return value.split("T")[0];
}

export function minutesLabel(minutes: number): string {
  if (minutes % 60 === 0) {
    const hours = minutes / 60;
    return hours === 1 ? "1 hour" : `${hours} hours`;
  }

  return `${minutes} minutes`;
}

/*
 * The status is always written in words (never colour alone), with a small
 * symbol that screen readers skip.
 */
export const STATUS_LABELS: Record<string, { label: string; symbol: string }> = {
  REQUESTED: { label: "Requested", symbol: "◔" },
  CONFIRMED: { label: "Confirmed", symbol: "✓" },
  DECLINED: { label: "Declined", symbol: "✕" },
  CANCELLED: { label: "Cancelled", symbol: "⊘" },
  COMPLETED: { label: "Completed", symbol: "★" },
};
