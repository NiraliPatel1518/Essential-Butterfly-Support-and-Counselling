import { describe, it, expect, beforeEach } from "vitest";
import {
  ApiError,
  addToGoogleCalendar,
  adminSignedIn,
  cancelAppointment,
  clientSignedIn,
  confirmAppointment,
  declineAppointment,
  fetchAdminAppointments,
  fetchClientProfile,
  fetchMyAppointments,
  fetchOpenSlots,
  fetchRules,
  fetchSettings,
  removeRule,
  requestAppointment,
  resendInvitation,
  saveFeedback,
  saveRule,
  saveSettings,
  sendInvitation,
} from "./appointmentApi";
import { FRIENDLY_SERVER_ERROR, SESSION_EXPIRED_MESSAGE } from "./contentApi";
import { mockFetch, mockFetchNetworkError, mockFetchSequence } from "../test/helpers";

// A sign-in token only needs a readable "exp" (seconds) to count as live.
function token(expiresInSeconds: number): string {
  const payload = btoa(
    JSON.stringify({ exp: Math.floor(Date.now() / 1000) + expiresInSeconds })
  )
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");

  return `header.${payload}.signature`;
}

function lastCall(fetchMock: ReturnType<typeof mockFetch>) {
  const [url, init] = fetchMock.mock.calls[fetchMock.mock.calls.length - 1];
  return { url: String(url), init: (init ?? {}) as RequestInit };
}

function headersOf(init: RequestInit) {
  return init.headers as Record<string, string>;
}

beforeEach(() => {
  localStorage.setItem("authToken", "client-token");
  localStorage.setItem("adminToken", "admin-token");
});

// ---------------------------------------------------------------- sessions

describe("clientSignedIn", () => {
  it("is true for a token that has not expired", () => {
    localStorage.setItem("authToken", token(3600));

    expect(clientSignedIn()).toBe(true);
  });

  it("is false for an expired token", () => {
    localStorage.setItem("authToken", token(-60));

    expect(clientSignedIn()).toBe(false);
  });

  it("is false when there is no token", () => {
    localStorage.removeItem("authToken");

    expect(clientSignedIn()).toBe(false);
  });

  it("is false for a token that cannot be read", () => {
    localStorage.setItem("authToken", "not-a-token");

    expect(clientSignedIn()).toBe(false);
  });

  it("is false when the token has no expiry", () => {
    const payload = btoa(JSON.stringify({ sub: "ava" }));
    localStorage.setItem("authToken", `h.${payload}.s`);

    expect(clientSignedIn()).toBe(false);
  });

  it("does not look at the administrator token", () => {
    localStorage.removeItem("authToken");
    localStorage.setItem("adminToken", token(3600));

    expect(clientSignedIn()).toBe(false);
  });
});

describe("adminSignedIn", () => {
  it("is true for a token that has not expired", () => {
    localStorage.setItem("adminToken", token(3600));

    expect(adminSignedIn()).toBe(true);
  });

  it("is false for an expired or missing token", () => {
    localStorage.setItem("adminToken", token(-1));
    expect(adminSignedIn()).toBe(false);

    localStorage.removeItem("adminToken");
    expect(adminSignedIn()).toBe(false);
  });

  it("does not look at the client token", () => {
    localStorage.removeItem("adminToken");
    localStorage.setItem("authToken", token(3600));

    expect(adminSignedIn()).toBe(false);
  });
});

// ------------------------------------------------------------------ public

describe("fetchOpenSlots", () => {
  const slots = [{ startsAt: "2026-11-16T10:00", endsAt: "2026-11-16T11:00", minutes: 60 }];

  it("returns the open slots without sending a sign-in token", async () => {
    const fetchMock = mockFetch(true, JSON.stringify(slots));

    await expect(fetchOpenSlots()).resolves.toEqual(slots);

    const { url, init } = lastCall(fetchMock);
    expect(url).toMatch(/\/api\/appointments\/availability$/);
    expect(init.headers).toBeUndefined();
  });

  it("throws a friendly error when the server fails", async () => {
    mockFetchSequence({ ok: false, status: 500, body: "boom" });

    await expect(fetchOpenSlots()).rejects.toMatchObject({
      message: FRIENDLY_SERVER_ERROR,
      status: 500,
    });
  });

  it("throws a friendly error when the server cannot be reached", async () => {
    mockFetchNetworkError();

    await expect(fetchOpenSlots()).rejects.toMatchObject({
      message: FRIENDLY_SERVER_ERROR,
      status: 0,
    });
  });
});

// ------------------------------------------------------------------ client

describe("fetchClientProfile", () => {
  it("asks for the signed-in client with their token", async () => {
    const profile = { fullName: "Ava", email: "ava@test.com", phoneNumber: null };
    const fetchMock = mockFetch(true, JSON.stringify(profile));

    await expect(fetchClientProfile()).resolves.toEqual(profile);

    const { url, init } = lastCall(fetchMock);
    expect(url).toMatch(/\/api\/auth\/me$/);
    expect(headersOf(init).Authorization).toBe("Bearer client-token");
  });
});

describe("requestAppointment", () => {
  it("posts the chosen start time with the client token", async () => {
    const booking = { id: 4, status: "REQUESTED" };
    const fetchMock = mockFetch(true, JSON.stringify(booking));

    await expect(requestAppointment("2026-11-16T10:00")).resolves.toEqual(booking);

    const { url, init } = lastCall(fetchMock);
    expect(url).toMatch(/\/api\/appointments$/);
    expect(init.method).toBe("POST");
    expect(headersOf(init).Authorization).toBe("Bearer client-token");
    expect(headersOf(init)["Content-Type"]).toBe("application/json");
    expect(JSON.parse(String(init.body))).toEqual({ startsAt: "2026-11-16T10:00" });
  });

  it("shows the server message when the slot was taken", async () => {
    mockFetchSequence({
      ok: false,
      status: 409,
      body: { message: "This appointment time is no longer available. Please select another time." },
    });

    const error = await requestAppointment("2026-11-16T10:00").catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(409);
    expect(error.message).toBe(
      "This appointment time is no longer available. Please select another time."
    );
  });

  it("says the session expired on a 401", async () => {
    mockFetchSequence({ ok: false, status: 401, body: "" });

    await expect(requestAppointment("2026-11-16T10:00")).rejects.toMatchObject({
      message: SESSION_EXPIRED_MESSAGE,
      status: 401,
    });
  });
});

describe("fetchMyAppointments", () => {
  it("reads the client's own bookings with their token", async () => {
    const fetchMock = mockFetch(true, JSON.stringify([]));

    await expect(fetchMyAppointments()).resolves.toEqual([]);

    const { url, init } = lastCall(fetchMock);
    expect(url).toMatch(/\/api\/appointments\/mine$/);
    expect(headersOf(init).Authorization).toBe("Bearer client-token");
  });
});

// ------------------------------------------------------------------- admin

describe("availability rules", () => {
  it("fetchRules reads the rules with the admin token", async () => {
    const fetchMock = mockFetch(true, JSON.stringify([]));

    await fetchRules();

    const { url, init } = lastCall(fetchMock);
    expect(url).toMatch(/\/api\/admin\/appointments\/availability$/);
    expect(headersOf(init).Authorization).toBe("Bearer admin-token");
  });

  it("saveRule with no id adds a rule using POST", async () => {
    const fetchMock = mockFetch(true, JSON.stringify({ id: 1 }));
    const fields = { weekday: 1, date: null, startTime: "10:00", slotMinutes: 60 };

    await saveRule(null, fields);

    const { url, init } = lastCall(fetchMock);
    expect(url).toMatch(/\/api\/admin\/appointments\/availability$/);
    expect(init.method).toBe("POST");
    expect(JSON.parse(String(init.body))).toEqual(fields);
  });

  it("saveRule with an id changes that rule using PUT", async () => {
    const fetchMock = mockFetch(true, JSON.stringify({ id: 7 }));
    const fields = { weekday: null, date: "2026-11-16", startTime: "14:00", slotMinutes: 90 };

    await saveRule(7, fields);

    const { url, init } = lastCall(fetchMock);
    expect(url).toMatch(/\/api\/admin\/appointments\/availability\/7$/);
    expect(init.method).toBe("PUT");
    expect(JSON.parse(String(init.body))).toEqual(fields);
  });

  it("saveRule shows the server message for an overlap", async () => {
    mockFetchSequence({
      ok: false,
      status: 409,
      body: { message: "This time overlaps another slot on the same day." },
    });

    await expect(
      saveRule(null, { weekday: 1, date: null, startTime: "10:00", slotMinutes: 60 })
    ).rejects.toMatchObject({
      message: "This time overlaps another slot on the same day.",
      status: 409,
    });
  });

  it("removeRule sends DELETE and returns nothing", async () => {
    const fetchMock = mockFetchSequence({ ok: true, status: 204, body: "" });

    await expect(removeRule(7)).resolves.toBeUndefined();

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toMatch(/\/api\/admin\/appointments\/availability\/7$/);
    expect((init as RequestInit).method).toBe("DELETE");
  });

  it("removeRule throws when the server refuses", async () => {
    mockFetchSequence({ ok: false, status: 404, body: { message: "Not found" } });

    await expect(removeRule(7)).rejects.toMatchObject({ message: "Not found", status: 404 });
  });
});

describe("settings", () => {
  it("fetchSettings reads the settings", async () => {
    const settings = { hideHoursBeforeStart: 2, bookingWindowDays: 7, timeZone: "America/Toronto" };
    const fetchMock = mockFetch(true, JSON.stringify(settings));

    await expect(fetchSettings()).resolves.toEqual(settings);
    expect(lastCall(fetchMock).url).toMatch(/\/api\/admin\/appointments\/settings$/);
  });

  it("saveSettings sends the hours with PUT", async () => {
    const fetchMock = mockFetch(true, JSON.stringify({ hideHoursBeforeStart: 24 }));

    await saveSettings(24);

    const { init } = lastCall(fetchMock);
    expect(init.method).toBe("PUT");
    expect(JSON.parse(String(init.body))).toEqual({ hideHoursBeforeStart: 24 });
  });
});

describe("fetchAdminAppointments", () => {
  it("asks for everything when there is no search and no status", async () => {
    const fetchMock = mockFetch(true, JSON.stringify([]));

    await fetchAdminAppointments("", "");

    expect(lastCall(fetchMock).url).toMatch(/\/api\/admin\/appointments$/);
  });

  it("adds the search text (trimmed) and the status", async () => {
    const fetchMock = mockFetch(true, JSON.stringify([]));

    await fetchAdminAppointments("  ava  ", "CONFIRMED");

    const url = lastCall(fetchMock).url;
    expect(url).toContain("search=ava");
    expect(url).toContain("status=CONFIRMED");
  });

  it("leaves out a search that is only spaces", async () => {
    const fetchMock = mockFetch(true, JSON.stringify([]));

    await fetchAdminAppointments("   ", "DECLINED");

    const url = lastCall(fetchMock).url;
    expect(url).not.toContain("search");
    expect(url).toContain("status=DECLINED");
  });

  it("encodes special characters in the search", async () => {
    const fetchMock = mockFetch(true, JSON.stringify([]));

    await fetchAdminAppointments("a&b c", "");

    expect(lastCall(fetchMock).url).toContain("search=a%26b+c");
  });

  it("sends the admin token", async () => {
    const fetchMock = mockFetch(true, JSON.stringify([]));

    await fetchAdminAppointments("", "");

    expect(headersOf(lastCall(fetchMock).init).Authorization).toBe("Bearer admin-token");
  });
});

describe("appointment actions", () => {
  const result = { appointment: { id: 3 }, warning: null };

  it("confirmAppointment posts with no body", async () => {
    const fetchMock = mockFetch(true, JSON.stringify(result));

    await expect(confirmAppointment(3)).resolves.toEqual(result);

    const { url, init } = lastCall(fetchMock);
    expect(url).toMatch(/\/api\/admin\/appointments\/3\/confirm$/);
    expect(init.method).toBe("POST");
    expect(init.body).toBeUndefined();
    expect(headersOf(init)["Content-Type"]).toBeUndefined();
    expect(headersOf(init).Authorization).toBe("Bearer admin-token");
  });

  it("declineAppointment posts the reason", async () => {
    const fetchMock = mockFetch(true, JSON.stringify(result));

    await declineAppointment(3, "Fully booked");

    const { url, init } = lastCall(fetchMock);
    expect(url).toMatch(/\/api\/admin\/appointments\/3\/decline$/);
    expect(init.method).toBe("POST");
    expect(JSON.parse(String(init.body))).toEqual({ reason: "Fully booked" });
  });

  it("cancelAppointment posts the reason", async () => {
    const fetchMock = mockFetch(true, JSON.stringify(result));

    await cancelAppointment(3, "Therapist is ill");

    const { url, init } = lastCall(fetchMock);
    expect(url).toMatch(/\/api\/admin\/appointments\/3\/cancel$/);
    expect(JSON.parse(String(init.body))).toEqual({ reason: "Therapist is ill" });
  });

  it("addToGoogleCalendar posts to the calendar step", async () => {
    const fetchMock = mockFetch(true, JSON.stringify(result));

    await addToGoogleCalendar(3);

    const { url, init } = lastCall(fetchMock);
    expect(url).toMatch(/\/api\/admin\/appointments\/3\/calendar$/);
    expect(init.method).toBe("POST");
  });

  it("sendInvitation saves the link with PUT", async () => {
    const fetchMock = mockFetch(true, JSON.stringify(result));

    await sendInvitation(3, "https://meet.example.org/a");

    const { url, init } = lastCall(fetchMock);
    expect(url).toMatch(/\/api\/admin\/appointments\/3\/invitation$/);
    expect(init.method).toBe("PUT");
    expect(JSON.parse(String(init.body))).toEqual({ meetingLink: "https://meet.example.org/a" });
  });

  it("resendInvitation posts to the resend step", async () => {
    const fetchMock = mockFetch(true, JSON.stringify(result));

    await resendInvitation(3);

    const { url, init } = lastCall(fetchMock);
    expect(url).toMatch(/\/api\/admin\/appointments\/3\/invitation\/resend$/);
    expect(init.method).toBe("POST");
  });

  it("returns the warning when a follow-up step failed", async () => {
    const withWarning = { appointment: { id: 3 }, warning: "Google Calendar is not connected" };
    mockFetch(true, JSON.stringify(withWarning));

    await expect(confirmAppointment(3)).resolves.toEqual(withWarning);
  });

  it("shows the server message when an action is refused", async () => {
    mockFetchSequence({
      ok: false,
      status: 409,
      body: { message: "Only a requested appointment can be accepted." },
    });

    await expect(confirmAppointment(3)).rejects.toMatchObject({
      message: "Only a requested appointment can be accepted.",
    });
  });

  it("hides a server crash behind the friendly message", async () => {
    mockFetchSequence({ ok: false, status: 500, body: { message: "NullPointerException" } });

    await expect(confirmAppointment(3)).rejects.toMatchObject({ message: FRIENDLY_SERVER_ERROR });
  });
});

describe("saveFeedback", () => {
  it("sends only the field that is given", async () => {
    const fetchMock = mockFetch(true, JSON.stringify({ id: 3 }));

    await saveFeedback(3, { feedback: "Good session" });

    const { url, init } = lastCall(fetchMock);
    expect(url).toMatch(/\/api\/admin\/appointments\/3\/feedback$/);
    expect(init.method).toBe("PUT");
    expect(JSON.parse(String(init.body))).toEqual({ feedback: "Good session" });
  });

  it("can send a private note alone", async () => {
    const fetchMock = mockFetch(true, JSON.stringify({ id: 3 }));

    await saveFeedback(3, { privateNotes: "Follow up" });

    expect(JSON.parse(String(lastCall(fetchMock).init.body))).toEqual({
      privateNotes: "Follow up",
    });
  });

  it("can send both together", async () => {
    const fetchMock = mockFetch(true, JSON.stringify({ id: 3 }));

    await saveFeedback(3, { feedback: "A", privateNotes: "B" });

    expect(JSON.parse(String(lastCall(fetchMock).init.body))).toEqual({
      feedback: "A",
      privateNotes: "B",
    });
  });

  it("shows the server message when it is refused", async () => {
    mockFetchSequence({
      ok: false,
      status: 409,
      body: { message: "Feedback can be written once a confirmed appointment has started." },
    });

    await expect(saveFeedback(3, { feedback: "Early" })).rejects.toMatchObject({
      message: "Feedback can be written once a confirmed appointment has started.",
    });
  });
});