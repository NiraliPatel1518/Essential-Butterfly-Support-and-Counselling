/*
 * Shared display helpers.
 *
 * Timestamps saved by the server (messages, testimonials, audit entries)
 * are stored in UTC and arrive either with a zone ("...Z", "+00:00") or
 * without one ("2026-11-17T20:29:10"). Both are UTC, so a missing zone is
 * treated as UTC and everything is shown in the clinic's time zone,
 * America/Toronto, in one format: "Nov 17, 2026, 3:29 PM".
 *
 * Appointment times are different: they are already clinic wall-clock time
 * and are formatted by appointmentFormat.ts.
 */

const CLINIC_TIME_ZONE = "America/Toronto";

const HAS_ZONE = /T[\d:.]+(Z|[+-]\d{2}(:?\d{2})?)$/i;

const formatter = new Intl.DateTimeFormat("en-US", {
  timeZone: CLINIC_TIME_ZONE,
  year: "numeric",
  month: "short",
  day: "numeric",
  hour: "numeric",
  minute: "2-digit",
});

export function formatServerDateTime(value: string | null | undefined): string {
  if (!value) return "";

  const text = value.trim().replace(" ", "T");
  const date = new Date(HAS_ZONE.test(text) ? text : `${text}Z`);

  return Number.isNaN(date.getTime()) ? value : formatter.format(date);
}

/* The Contact form saves a short code; the admin should read the form's own
 * wording. Unknown codes are shown as readable words instead of raw codes. */
const CONTACT_SUBJECTS: Record<string, string> = {
  "general-inquiry": "General Inquiry",
  services: "Questions About Services",
  respite: "Respite Support",
  counselling: "Counselling",
  coaching: "Coaching",
  "tutoring-assistive-tech": "Tutoring and Assistive Technology",
  "social-skills": "Social Skills Support",
  "booking-help": "Booking Help",
  "accessibility-request": "Accessibility Request",
  other: "Other",
};

export function contactSubjectLabel(code: string | null | undefined): string {
  if (!code) return "";

  const known = CONTACT_SUBJECTS[code];

  if (known) return known;

  const words = code.replace(/[-_]+/g, " ").trim();

  return words.charAt(0).toUpperCase() + words.slice(1);
}
