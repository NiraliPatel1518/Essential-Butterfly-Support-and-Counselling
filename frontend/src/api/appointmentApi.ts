import { ApiError, requestJson, send, toApiError } from "./contentApi";

/*
 * Everything the appointment screens say to the backend (UC-11).
 * Addresses go through apiUrl() inside the shared helpers of contentApi.ts,
 * so no server address is written here. Errors are ApiError objects whose
 * message is already safe to show (see contentApi.ts).
 */

export type AppointmentStatus =
  | "REQUESTED"
  | "CONFIRMED"
  | "DECLINED"
  | "CANCELLED"
  | "COMPLETED";

export type OpenSlot = {
  startsAt: string;
  endsAt: string;
  minutes: number;
};

export type AvailabilityRule = {
  id: number;
  // 1 = Monday ... 7 = Sunday; null for a slot on one specific date
  weekday: number | null;
  // yyyy-MM-dd for a one-date slot; null for a weekly slot
  date: string | null;
  startTime: string;
  endTime: string;
  slotMinutes: number;
};

export type AvailabilityFields = {
  weekday: number | null;
  date: string | null;
  startTime: string;
  slotMinutes: number;
};

export type AppointmentSettings = {
  hideHoursBeforeStart: number;
  bookingWindowDays: number;
  timeZone: string;
};

export type ClientProfile = {
  fullName: string;
  email: string;
  phoneNumber: string | null;
};

export type ClientAppointment = {
  id: number;
  status: AppointmentStatus;
  startsAt: string;
  endsAt: string;
  statusReason: string | null;
  meetingLink: string | null;
  feedback: string | null;
  feedbackUpdatedAt: string | null;
  requestedAt: string;
};

export type AdminAppointment = {
  id: number;
  status: AppointmentStatus;
  startsAt: string;
  endsAt: string;
  clientName: string;
  clientEmail: string | null;
  clientPhone: string | null;
  statusReason: string | null;
  meetingLink: string | null;
  invitationSentAt: string | null;
  calendarEventAdded: boolean;
  feedback: string | null;
  feedbackUpdatedAt: string | null;
  privateNotes: string | null;
  requestedAt: string;
  started: boolean;
  canCancel: boolean;
  feedbackDue: boolean;
};

export type ActionResult = {
  appointment: AdminAppointment;
  // Set when a follow-up step (calendar or e-mail) still needs another try.
  warning: string | null;
};

// ---------------------------------------------------------------- sessions

function tokenIsLive(token: string | null): boolean {
  if (!token) return false;

  try {
    const payload = token.split(".")[1]
      .replace(/-/g, "+")
      .replace(/_/g, "/");

    const data = JSON.parse(
      atob(payload.padEnd(Math.ceil(payload.length / 4) * 4, "="))
    ) as { exp?: number };

    return typeof data.exp === "number" && data.exp * 1000 > Date.now();
  } catch {
    return false;
  }
}

export function clientSignedIn(): boolean {
  return tokenIsLive(localStorage.getItem("authToken"));
}

export function adminSignedIn(): boolean {
  return tokenIsLive(localStorage.getItem("adminToken"));
}

function clientHeaders(extra: Record<string, string> = {}) {
  return {
    Authorization: `Bearer ${localStorage.getItem("authToken")}`,
    ...extra,
  };
}

function adminHeaders(extra: Record<string, string> = {}) {
  return {
    Authorization: `Bearer ${localStorage.getItem("adminToken")}`,
    ...extra,
  };
}

const JSON_TYPE = { "Content-Type": "application/json" };

// ------------------------------------------------------------------ public

export function fetchOpenSlots(): Promise<OpenSlot[]> {
  return requestJson<OpenSlot[]>("/api/appointments/availability");
}

// ------------------------------------------------------------------ client

// The signed-in client's own details, used to fill the booking pop-up.
export function fetchClientProfile(): Promise<ClientProfile> {
  return requestJson<ClientProfile>("/api/auth/me", {
    headers: clientHeaders(),
  });
}

export function requestAppointment(startsAt: string): Promise<ClientAppointment> {
  return requestJson<ClientAppointment>("/api/appointments", {
    method: "POST",
    headers: clientHeaders(JSON_TYPE),
    body: JSON.stringify({ startsAt }),
  });
}

export function fetchMyAppointments(): Promise<ClientAppointment[]> {
  return requestJson<ClientAppointment[]>("/api/appointments/mine", {
    headers: clientHeaders(),
  });
}

// ------------------------------------------------------------------- admin

export function fetchRules(): Promise<AvailabilityRule[]> {
  return requestJson<AvailabilityRule[]>("/api/admin/appointments/availability", {
    headers: adminHeaders(),
  });
}

export function saveRule(
  id: number | null,
  fields: AvailabilityFields
): Promise<AvailabilityRule> {
  return requestJson<AvailabilityRule>(
    id === null
      ? "/api/admin/appointments/availability"
      : `/api/admin/appointments/availability/${id}`,
    {
      method: id === null ? "POST" : "PUT",
      headers: adminHeaders(JSON_TYPE),
      body: JSON.stringify(fields),
    }
  );
}

export async function removeRule(id: number): Promise<void> {
  const response = await send(`/api/admin/appointments/availability/${id}`, {
    method: "DELETE",
    headers: adminHeaders(),
  });

  if (!response.ok) {
    throw await toApiError(response);
  }
}

export function fetchSettings(): Promise<AppointmentSettings> {
  return requestJson<AppointmentSettings>("/api/admin/appointments/settings", {
    headers: adminHeaders(),
  });
}

export function saveSettings(
  hideHoursBeforeStart: number
): Promise<AppointmentSettings> {
  return requestJson<AppointmentSettings>("/api/admin/appointments/settings", {
    method: "PUT",
    headers: adminHeaders(JSON_TYPE),
    body: JSON.stringify({ hideHoursBeforeStart }),
  });
}

export function fetchAdminAppointments(
  search: string,
  status: string
): Promise<AdminAppointment[]> {
  const query = new URLSearchParams();

  if (search.trim()) query.set("search", search.trim());
  if (status) query.set("status", status);

  const suffix = query.toString() ? `?${query.toString()}` : "";

  return requestJson<AdminAppointment[]>(
    `/api/admin/appointments${suffix}`,
    { headers: adminHeaders() }
  );
}

function action(
  id: number,
  step: string,
  method: "POST" | "PUT",
  body?: unknown
): Promise<ActionResult> {
  return requestJson<ActionResult>(`/api/admin/appointments/${id}/${step}`, {
    method,
    headers: adminHeaders(body === undefined ? {} : JSON_TYPE),
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

export const confirmAppointment = (id: number) =>
  action(id, "confirm", "POST");

export const declineAppointment = (id: number, reason: string) =>
  action(id, "decline", "POST", { reason });

export const cancelAppointment = (id: number, reason: string) =>
  action(id, "cancel", "POST", { reason });

export const addToGoogleCalendar = (id: number) =>
  action(id, "calendar", "POST");

export const sendInvitation = (id: number, meetingLink: string) =>
  action(id, "invitation", "PUT", { meetingLink });

export const resendInvitation = (id: number) =>
  action(id, "invitation/resend", "POST");

export function saveFeedback(
  id: number,
  // Only the field that is given is changed; the other one is kept.
  fields: { feedback?: string; privateNotes?: string }
): Promise<AdminAppointment> {
  return requestJson<AdminAppointment>(
    `/api/admin/appointments/${id}/feedback`,
    {
      method: "PUT",
      headers: adminHeaders(JSON_TYPE),
      body: JSON.stringify(fields),
    }
  );
}

export { ApiError };
