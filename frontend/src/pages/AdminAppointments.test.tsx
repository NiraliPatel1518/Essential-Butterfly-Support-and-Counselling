import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AdminAppointments from "./AdminAppointments";
import { renderPage } from "../test/helpers";
import { jsonResponse } from "../test/render";
import type {
  AdminAppointment,
  AppointmentSettings,
  AvailabilityRule,
} from "../api/appointmentApi";

function booking(overrides: Partial<AdminAppointment> = {}): AdminAppointment {
  return {
    id: 1,
    status: "REQUESTED",
    startsAt: "2026-11-16T10:00:00",
    endsAt: "2026-11-16T11:00:00",
    clientName: "Ava Client",
    clientEmail: "ava@test.com",
    clientPhone: null,
    statusReason: null,
    meetingLink: null,
    invitationSentAt: null,
    calendarEventAdded: false,
    feedback: null,
    feedbackUpdatedAt: null,
    privateNotes: null,
    requestedAt: "2026-11-10T09:00:00",
    started: false,
    canCancel: true,
    feedbackDue: false,
    ...overrides,
  };
}

function weekly(overrides: Partial<AvailabilityRule> = {}): AvailabilityRule {
  return {
    id: 1,
    weekday: 1,
    date: null,
    startTime: "10:00",
    endTime: "11:00",
    slotMinutes: 60,
    ...overrides,
  };
}

function dated(overrides: Partial<AvailabilityRule> = {}): AvailabilityRule {
  return weekly({ id: 2, weekday: null, date: "2026-11-20", startTime: "14:00", endTime: "15:30", slotMinutes: 90, ...overrides });
}

const SETTINGS: AppointmentSettings = {
  hideHoursBeforeStart: 3,
  bookingWindowDays: 7,
  timeZone: "America/Toronto",
};

type World = {
  rules: AvailabilityRule[];
  settings: AppointmentSettings;
  waiting: AdminAppointment[];
  everything: AdminAppointment[];
  // Replies that override the normal ones, keyed "METHOD path-part".
  fail: Record<string, () => Promise<unknown>>;
};

function world(overrides: Partial<World> = {}): World {
  return { rules: [], settings: SETTINGS, waiting: [], everything: [], fail: {}, ...overrides };
}

function stubApi(state: World) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    const path = String(url);
    const method = init?.method ?? "GET";

    for (const [key, reply] of Object.entries(state.fail)) {
      const [m, part] = key.split(" ");
      if (m === method && path.includes(part)) return reply();
    }

    if (path.includes("/availability")) {
      if (method === "GET") return jsonResponse(state.rules);
      if (method === "DELETE") {
        return Promise.resolve({ ok: true, status: 204, json: () => Promise.resolve(null), text: () => Promise.resolve("") });
      }
      return jsonResponse(weekly());
    }

    if (path.includes("/settings")) {
      if (method === "PUT") {
        const hours = JSON.parse(String(init?.body)).hideHoursBeforeStart;
        return jsonResponse({ ...state.settings, hideHoursBeforeStart: hours });
      }
      return jsonResponse(state.settings);
    }

    if (path.includes("/api/admin/appointments")) {
      return jsonResponse(path.includes("status=REQUESTED") && !path.includes("search") ? state.waiting : state.everything);
    }

    return jsonResponse([]);
  });

  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function callsTo(fetchMock: ReturnType<typeof stubApi>, method: string, part: string) {
  return fetchMock.mock.calls.filter(
    ([url, init]) => String(url).includes(part) && (init?.method ?? "GET") === method
  );
}

async function show(state: World = world()) {
  const fetchMock = stubApi(state);
  renderPage("/admin-appointments", <AdminAppointments />);
  await waitFor(() => expect(screen.queryByText("Refreshing…")).not.toBeInTheDocument());
  return fetchMock;
}

beforeEach(() => {
  localStorage.setItem("adminToken", "admin-token");

  HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
    this.setAttribute("open", "");
  };
  HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
    this.removeAttribute("open");
    this.dispatchEvent(new Event("close"));
  };
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  localStorage.clear();
});

describe("AdminAppointments: the page", () => {
  it("shows the heading and a link back to the admin dashboard", async () => {
    await show();

    expect(screen.getByRole("heading", { name: "Manage Appointments" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /back to the admin dashboard/i })).toHaveAttribute(
      "href",
      "/admin-dashboard"
    );
  });

  it("loads the rules, settings and both appointment lists", async () => {
    const fetchMock = await show();

    expect(callsTo(fetchMock, "GET", "/availability")).toHaveLength(1);
    expect(callsTo(fetchMock, "GET", "/settings")).toHaveLength(1);
    expect(callsTo(fetchMock, "GET", "status=REQUESTED")).toHaveLength(1);
    expect(fetchMock.mock.calls.some(([url]) => /\/api\/admin\/appointments$/.test(String(url)))).toBe(true);
  });

  it("sends the admin token with every request", async () => {
    const fetchMock = await show();

    for (const [, init] of fetchMock.mock.calls) {
      expect((init?.headers as Record<string, string>).Authorization).toBe("Bearer admin-token");
    }
  });

  it("shows the booking window and the clinic time zone", async () => {
    await show();

    expect(screen.getByText(/next\s+7 days \(clinic time: America\/Toronto\)/)).toBeInTheDocument();
  });

  it("Refresh loads everything again", async () => {
    const fetchMock = await show();

    await userEvent.click(screen.getByRole("button", { name: "Refresh" }));
    await waitFor(() => expect(callsTo(fetchMock, "GET", "/settings")).toHaveLength(2));
  });

  it("shows the problem when loading fails", async () => {
    await show(world({ fail: { "GET /settings": () => jsonResponse({ message: "Settings are broken." }, false, 409) } }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Settings are broken.");
  });

  it("hides a server crash behind the friendly message", async () => {
    await show(world({ fail: { "GET /settings": () => jsonResponse("", false, 500) } }));

    expect(await screen.findByRole("alert")).toHaveTextContent("We could not complete that right now.");
  });

  it("sends the administrator to the login page when the session expired", async () => {
    stubApi(world({ fail: { "GET /settings": () => jsonResponse("", false, 401) } }));
    renderPage("/admin-appointments", <AdminAppointments />);

    expect(await screen.findByText("Landed on /login")).toBeInTheDocument();
    expect(localStorage.getItem("adminToken")).toBeNull();
  });
});

describe("AdminAppointments: availability list", () => {
  it("says there are no slots yet", async () => {
    await show();

    expect(screen.getByText(/No slots yet\. Add one above/)).toBeInTheDocument();
  });

  it("lists weekly slots under their weekday", async () => {
    await show(world({ rules: [weekly(), weekly({ id: 3, weekday: 3, startTime: "13:00", endTime: "13:30", slotMinutes: 30 })] }));

    expect(screen.getByRole("heading", { name: "Monday" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Wednesday" })).toBeInTheDocument();
    expect(screen.getByText(/10:00.* to 11:00.*\(1 hour\)/)).toBeInTheDocument();
    expect(screen.getByText(/1:00.* to 1:30.*\(30 minutes\)/)).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Tuesday" })).not.toBeInTheDocument();
  });

  it("lists one-date slots under a separate heading", async () => {
    await show(world({ rules: [dated()] }));

    expect(screen.getByRole("heading", { name: "On specific dates" })).toBeInTheDocument();
    expect(screen.getByText(/Friday, November 20, 2026/)).toBeInTheDocument();
    expect(screen.getByText(/2:00.* to 3:30.*\(90 minutes\)/)).toBeInTheDocument();
  });
});

describe("AdminAppointments: adding a slot", () => {
  it("starts with an every-week form and sensible defaults", async () => {
    await show();

    expect(screen.getByLabelText("Repeat")).toHaveValue("week");
    expect(screen.getByLabelText("Day of the week")).toHaveValue("1");
    expect(screen.getByLabelText("Slot length")).toHaveValue("60");
    expect(screen.getByRole("button", { name: "Add weekly slot" })).toBeInTheDocument();
  });

  it("offers slot lengths from 30 minutes to 2 hours", async () => {
    await show();

    const options = within(screen.getByLabelText("Slot length")).getAllByRole("option").map((o) => o.textContent);

    expect(options).toEqual(["30 minutes", "45 minutes", "1 hour", "75 minutes", "90 minutes", "105 minutes", "2 hours"]);
  });

  it("switches to a date field for a one-date slot", async () => {
    await show();

    await userEvent.selectOptions(screen.getByLabelText("Repeat"), "date");

    expect(screen.getByLabelText("Date")).toBeInTheDocument();
    expect(screen.queryByLabelText("Day of the week")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add slot for this date" })).toBeInTheDocument();
  });

  it("asks for the start time before saving", async () => {
    const fetchMock = await show();

    await userEvent.click(screen.getByRole("button", { name: "Add weekly slot" }));

    expect(screen.getByRole("alert")).toHaveTextContent("Choose the start time.");
    expect(screen.getByLabelText("Start time")).toHaveFocus();
    expect(callsTo(fetchMock, "POST", "/availability")).toHaveLength(0);
  });

  it("asks for the date before saving a one-date slot", async () => {
    const fetchMock = await show();

    await userEvent.selectOptions(screen.getByLabelText("Repeat"), "date");
    await userEvent.type(screen.getByLabelText("Start time"), "10:00");
    await userEvent.click(screen.getByRole("button", { name: "Add slot for this date" }));

    expect(screen.getByRole("alert")).toHaveTextContent("Choose the date.");
    expect(callsTo(fetchMock, "POST", "/availability")).toHaveLength(0);
  });

  it("adds a weekly slot and says it repeats every week", async () => {
    const fetchMock = await show();

    await userEvent.selectOptions(screen.getByLabelText("Day of the week"), "3");
    await userEvent.type(screen.getByLabelText("Start time"), "09:30");
    await userEvent.selectOptions(screen.getByLabelText("Slot length"), "90");
    await userEvent.click(screen.getByRole("button", { name: "Add weekly slot" }));

    expect(await screen.findByText("Weekly slot added. It now repeats every week.")).toBeInTheDocument();

    const [, init] = callsTo(fetchMock, "POST", "/availability")[0];
    expect(JSON.parse(String(init?.body))).toEqual({
      weekday: 3,
      date: null,
      startTime: "09:30",
      slotMinutes: 90,
    });
  });

  it("adds a one-date slot", async () => {
    const fetchMock = await show();

    await userEvent.selectOptions(screen.getByLabelText("Repeat"), "date");
    await userEvent.type(screen.getByLabelText("Date"), "2099-12-24");
    await userEvent.type(screen.getByLabelText("Start time"), "11:00");
    await userEvent.click(screen.getByRole("button", { name: "Add slot for this date" }));

    expect(await screen.findByText("Slot added for that date.")).toBeInTheDocument();

    const [, init] = callsTo(fetchMock, "POST", "/availability")[0];
    expect(JSON.parse(String(init?.body))).toEqual({
      weekday: null,
      date: "2099-12-24",
      startTime: "11:00",
      slotMinutes: 60,
    });
  });

  it("loads the list again and clears the form after adding", async () => {
    const fetchMock = await show();

    await userEvent.type(screen.getByLabelText("Start time"), "10:00");
    await userEvent.click(screen.getByRole("button", { name: "Add weekly slot" }));
    await screen.findByText(/Weekly slot added/);

    expect(callsTo(fetchMock, "GET", "/availability")).toHaveLength(2);
    expect(screen.getByLabelText("Start time")).toHaveValue("");
  });

  it("shows the server message, for example an overlap, and keeps the form", async () => {
    await show(world({
      fail: { "POST /availability": () => jsonResponse({ message: "This time overlaps another slot on the same day." }, false, 409) },
    }));

    await userEvent.type(screen.getByLabelText("Start time"), "10:00");
    await userEvent.click(screen.getByRole("button", { name: "Add weekly slot" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("This time overlaps another slot on the same day.");
    expect(screen.getByLabelText("Start time")).toHaveValue("10:00");
    expect(screen.getByLabelText("Start time")).toHaveAttribute("aria-invalid", "true");
  });

  it("sends the administrator to the login page when the session expires while saving", async () => {
    stubApi(world({ fail: { "POST /availability": () => jsonResponse("", false, 401) } }));
    renderPage("/admin-appointments", <AdminAppointments />);
    await waitFor(() => expect(screen.queryByText("Refreshing…")).not.toBeInTheDocument());

    await userEvent.type(screen.getByLabelText("Start time"), "10:00");
    await userEvent.click(screen.getByRole("button", { name: "Add weekly slot" }));

    expect(await screen.findByText("Landed on /login")).toBeInTheDocument();
  });
});

describe("AdminAppointments: editing and removing a slot", () => {
  it("fills the form with the slot to edit", async () => {
    await show(world({ rules: [weekly({ weekday: 2, startTime: "15:00", endTime: "16:30", slotMinutes: 90 })] }));

    await userEvent.click(screen.getByRole("button", { name: /Edit Tuesday 3:00/ }));

    expect(screen.getByLabelText("Day of the week")).toHaveValue("2");
    expect(screen.getByLabelText("Start time")).toHaveValue("15:00");
    expect(screen.getByLabelText("Slot length")).toHaveValue("90");
    expect(screen.getByRole("button", { name: "Save changes" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Cancel editing" })).toBeInTheDocument();
  });

  it("opens a one-date slot in date mode", async () => {
    await show(world({ rules: [dated()] }));

    await userEvent.click(screen.getByRole("button", { name: /Edit 2026-11-20/ }));

    expect(screen.getByLabelText("Repeat")).toHaveValue("date");
    expect(screen.getByLabelText("Date")).toHaveValue("2026-11-20");
  });

  it("saves the change with PUT and says it was updated", async () => {
    const fetchMock = await show(world({ rules: [weekly({ id: 9 })] }));

    await userEvent.click(screen.getByRole("button", { name: /Edit Monday/ }));
    const start = screen.getByLabelText("Start time");
    await userEvent.clear(start);
    await userEvent.type(start, "12:00");
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));

    expect(await screen.findByText("Slot updated.")).toBeInTheDocument();

    const [url, init] = callsTo(fetchMock, "PUT", "/availability/9")[0];
    expect(String(url)).toMatch(/\/availability\/9$/);
    expect(JSON.parse(String(init?.body)).startTime).toBe("12:00");
  });

  it("Cancel editing goes back to an empty add form", async () => {
    await show(world({ rules: [weekly()] }));

    await userEvent.click(screen.getByRole("button", { name: /Edit Monday/ }));
    await userEvent.click(screen.getByRole("button", { name: "Cancel editing" }));

    expect(screen.getByRole("button", { name: "Add weekly slot" })).toBeInTheDocument();
    expect(screen.getByLabelText("Start time")).toHaveValue("");
  });

  it("asks to confirm before removing and can be undone with Keep", async () => {
    const fetchMock = await show(world({ rules: [weekly()] }));

    await userEvent.click(screen.getByRole("button", { name: /Remove Monday/ }));

    expect(screen.getByRole("button", { name: "Yes, remove" })).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Keep" }));

    expect(screen.queryByRole("button", { name: "Yes, remove" })).not.toBeInTheDocument();
    expect(callsTo(fetchMock, "DELETE", "/availability")).toHaveLength(0);
  });

  it("removes a slot after confirming and explains existing requests are not affected", async () => {
    const fetchMock = await show(world({ rules: [weekly({ id: 4 })] }));

    await userEvent.click(screen.getByRole("button", { name: /Remove Monday/ }));
    await userEvent.click(screen.getByRole("button", { name: "Yes, remove" }));

    expect(await screen.findByText("Slot removed. Requests already made for it are not affected.")).toBeInTheDocument();
    expect(String(callsTo(fetchMock, "DELETE", "/availability")[0][0])).toMatch(/\/availability\/4$/);
  });

  it("shows the problem when a slot cannot be removed", async () => {
    await show(world({
      rules: [weekly()],
      fail: { "DELETE /availability": () => jsonResponse({ message: "That slot no longer exists." }, false, 404) },
    }));

    await userEvent.click(screen.getByRole("button", { name: /Remove Monday/ }));
    await userEvent.click(screen.getByRole("button", { name: "Yes, remove" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("That slot no longer exists.");
  });
});

describe("AdminAppointments: when unbooked slots disappear", () => {
  it("shows the saved number of hours", async () => {
    await show();

    expect(screen.getByLabelText(/Hide an unbooked slot this many hours/)).toHaveValue(3);
  });

  it("saves a new number of hours", async () => {
    const fetchMock = await show();

    const input = screen.getByLabelText(/Hide an unbooked slot this many hours/);
    await userEvent.clear(input);
    await userEvent.type(input, "24");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText("Unbooked slots now disappear 24 hour(s) before they start.")).toBeInTheDocument();
    expect(JSON.parse(String(callsTo(fetchMock, "PUT", "/settings")[0][1]?.body))).toEqual({ hideHoursBeforeStart: 24 });
  });

  it("accepts the limits 0 and 168", async () => {
    await show();

    const input = screen.getByLabelText(/Hide an unbooked slot this many hours/);

    for (const value of ["0", "168"]) {
      await userEvent.clear(input);
      await userEvent.type(input, value);
      await userEvent.click(screen.getByRole("button", { name: "Save" }));
      expect(await screen.findByText(`Unbooked slots now disappear ${value} hour(s) before they start.`)).toBeInTheDocument();
    }
  });

  it("refuses a number that is out of range, not whole or empty", async () => {
    const fetchMock = await show();

    const input = screen.getByLabelText(/Hide an unbooked slot this many hours/);

    for (const bad of ["169", "-1", "2.5", ""]) {
      await userEvent.clear(input);
      if (bad) await userEvent.type(input, bad);
      await userEvent.click(screen.getByRole("button", { name: "Save" }));

      expect(screen.getByRole("alert")).toHaveTextContent("Enter a whole number of hours from 0 to 168.");
      expect(input).toHaveAttribute("aria-invalid", "true");
    }

    expect(callsTo(fetchMock, "PUT", "/settings")).toHaveLength(0);
  });

  it("shows the server message when saving fails", async () => {
    await show(world({ fail: { "PUT /settings": () => jsonResponse({ message: "Not allowed right now." }, false, 409) } }));

    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Not allowed right now.");
  });
});

describe("AdminAppointments: the booking lists", () => {
  it("says no requests are waiting", async () => {
    await show();

    expect(screen.getByRole("heading", { name: "Requests waiting for you (0)" })).toBeInTheDocument();
    expect(screen.getByText("No requests are waiting.")).toBeInTheDocument();
  });

  it("shows the waiting requests with a count", async () => {
    const waiting = [booking({ id: 1, clientName: "Ava Client" }), booking({ id: 2, clientName: "Ben Other" })];
    await show(world({ waiting, everything: waiting }));

    expect(screen.getByRole("heading", { name: "Requests waiting for you (2)" })).toBeInTheDocument();

    const section = screen.getByRole("heading", { name: /Requests waiting/ }).closest("section")!;
    expect(within(section).getAllByRole("article")).toHaveLength(2);
  });

  it("counts the bookings found", async () => {
    await show(world({ everything: [booking({ id: 1 })] }));
    expect(screen.getByText("1 booking found.")).toBeInTheDocument();
  });

  it("uses the plural for any other number", async () => {
    await show(world({ everything: [booking({ id: 1 }), booking({ id: 2 })] }));
    expect(screen.getByText("2 bookings found.")).toBeInTheDocument();

    await show(world());
    expect(screen.getAllByText("0 bookings found.").length).toBeGreaterThan(0);
  });

  it("searches by text and status", async () => {
    const fetchMock = await show();

    await userEvent.type(screen.getByLabelText(/Search customers/), "  ava ");
    await userEvent.selectOptions(screen.getByLabelText("Status"), "CONFIRMED");
    await userEvent.click(screen.getByRole("button", { name: "Search" }));

    await waitFor(() => {
      const last = String(fetchMock.mock.calls[fetchMock.mock.calls.length - 1][0]);
      expect(last).toContain("search=ava");
      expect(last).toContain("status=CONFIRMED");
    });
  });

  it("limits the search text to 80 characters", async () => {
    await show();

    expect(screen.getByLabelText(/Search customers/)).toHaveAttribute("maxlength", "80");
  });

  it("offers every status in the filter", async () => {
    await show();

    const options = within(screen.getByLabelText("Status")).getAllByRole("option").map((o) => o.textContent);

    expect(options).toEqual(["All statuses", "Requested", "Confirmed", "Declined", "Cancelled", "Completed"]);
  });

  it("shows the server message when the search fails", async () => {
    const state = world();
    await show(state);

    state.fail["GET search="] = () => jsonResponse({ message: "Search text is too long." }, false, 400);
    await userEvent.type(screen.getByLabelText(/Search customers/), "x");
    await userEvent.click(screen.getByRole("button", { name: "Search" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Search text is too long.");
  });

  it("accepting a request shows Saved and reloads both lists", async () => {
    const waiting = [booking({ id: 7 })];
    const state = world({ waiting, everything: waiting });
    const fetchMock = await show(state);

    state.fail["POST /confirm"] = () =>
      jsonResponse({ appointment: booking({ id: 7, status: "CONFIRMED" }), warning: null });

    const section = screen.getByRole("heading", { name: /Requests waiting/ }).closest("section")!;
    await userEvent.click(within(section).getByRole("button", { name: "Accept request" }));

    expect(await screen.findByText("Saved.")).toBeInTheDocument();
    await waitFor(() => expect(callsTo(fetchMock, "GET", "status=REQUESTED").length).toBeGreaterThanOrEqual(2));
  });

  it("shows a warning from a follow-up step in its own banner", async () => {
    const waiting = [booking({ id: 7 })];
    const state = world({ waiting, everything: waiting });
    await show(state);

    state.fail["POST /confirm"] = () =>
      jsonResponse({
        appointment: booking({ id: 7, status: "CONFIRMED" }),
        warning: "The appointment is confirmed, but Google Calendar is not connected, so no calendar event was created.",
      });

    const section = screen.getByRole("heading", { name: /Requests waiting/ }).closest("section")!;
    await userEvent.click(within(section).getByRole("button", { name: "Accept request" }));

    expect(await screen.findByText(/Google Calendar is not connected/)).toBeInTheDocument();
    expect(screen.getByText("Saved.")).toBeInTheDocument();
  });

  it("shows an error when an action is refused", async () => {
    const waiting = [booking({ id: 7 })];
    const state = world({ waiting, everything: waiting });
    await show(state);

    state.fail["POST /confirm"] = () =>
      jsonResponse({ message: "Only a requested appointment can be accepted." }, false, 409);

    const section = screen.getByRole("heading", { name: /Requests waiting/ }).closest("section")!;
    await userEvent.click(within(section).getByRole("button", { name: "Accept request" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Only a requested appointment can be accepted.");
  });

  it("sends the administrator to the login page when an action meets an expired session", async () => {
    const waiting = [booking({ id: 7 })];
    const state = world({ waiting, everything: waiting });
    await show(state);

    state.fail["POST /confirm"] = () => jsonResponse("", false, 401);

    const section = screen.getByRole("heading", { name: /Requests waiting/ }).closest("section")!;
    await userEvent.click(within(section).getByRole("button", { name: "Accept request" }));

    expect(await screen.findByText("Landed on /login")).toBeInTheDocument();
  });
});
