import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Appointments from "./Appointments";
import { renderApp, jsonResponse } from "../test/render";
import type { ClientProfile, OpenSlot } from "../api/appointmentApi";

const TEN: OpenSlot = { startsAt: "2026-11-16T10:00:00", endsAt: "2026-11-16T11:00:00", minutes: 60 };
const ELEVEN: OpenSlot = { startsAt: "2026-11-16T11:00:00", endsAt: "2026-11-16T12:00:00", minutes: 60 };
const NEXT_DAY: OpenSlot = { startsAt: "2026-11-17T14:00:00", endsAt: "2026-11-17T14:30:00", minutes: 30 };

const PROFILE: ClientProfile = { fullName: "Ava Client", email: "ava@test.com", phoneNumber: "+14165550100" };

// A sign-in token only needs a readable "exp" to count as live.
function liveToken() {
  const payload = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }));
  return `header.${payload}.signature`;
}

type Handlers = {
  slots?: () => Promise<unknown>;
  profile?: () => Promise<unknown>;
  book?: () => Promise<unknown>;
};

function stubApi(handlers: Handlers = {}) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    const path = String(url);

    if (path.includes("/api/appointments/availability")) {
      return (handlers.slots ?? (() => jsonResponse([TEN, ELEVEN, NEXT_DAY])))();
    }
    if (path.includes("/api/auth/me")) {
      return (handlers.profile ?? (() => jsonResponse(PROFILE)))();
    }
    if (path.endsWith("/api/appointments") && init?.method === "POST") {
      return (handlers.book ?? (() => jsonResponse({ id: 1, status: "REQUESTED" }, true, 201)))();
    }
    return jsonResponse([]);
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function show(handlers: Handlers = {}) {
  const fetchMock = stubApi(handlers);
  renderApp("/appointments", <Appointments />);
  return fetchMock;
}

async function pickTen() {
  await userEvent.click(await screen.findByRole("button", { name: /10:00.*Select this time/ }));
  return screen.findByRole("dialog", { name: "Request this appointment" });
}

beforeEach(() => {
  // jsdom has no real modal dialog, so a small stand-in opens and closes it.
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

describe("Appointments page: the list of times", () => {
  it("shows the heading and a loading message first", async () => {
    show();

    expect(screen.getByRole("heading", { name: "Available appointments" })).toBeInTheDocument();
    expect(screen.getByText("Loading available times…")).toBeInTheDocument();

    await screen.findByRole("button", { name: /10:00.*Select this time/ });
    expect(screen.queryByText("Loading available times…")).not.toBeInTheDocument();
  });

  it("groups the times by day with a heading for each day", async () => {
    show();

    expect(await screen.findByRole("heading", { name: /Monday, November 16, 2026/ })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /Tuesday, November 17, 2026/ })).toBeInTheDocument();

    const monday = screen.getByRole("heading", { name: /Monday/ }).closest("section")!;
    expect(within(monday).getAllByRole("button")).toHaveLength(2);

    const tuesday = screen.getByRole("heading", { name: /Tuesday/ }).closest("section")!;
    expect(within(tuesday).getAllByRole("button")).toHaveLength(1);
  });

  it("shows the start time and the length on each button", async () => {
    show();

    const button = await screen.findByRole("button", { name: /10:00.*Select this time/ });

    expect(button).toHaveTextContent(/10:00/);
    expect(button).toHaveTextContent("1 hour");
    expect(screen.getByRole("button", { name: /Tuesday.*Select this time/ })).toHaveTextContent("30 minutes");
  });

  it("gives every button a full spoken name with the day, time and length", async () => {
    show();

    const button = await screen.findByRole("button", { name: /10:00.*Select this time/ });
    const name = button.getAttribute("aria-label")!;

    expect(name).toContain("Monday, November 16, 2026");
    expect(name).toContain("1 hour");
    expect(name).toMatch(/to 11:00/);
  });

  it("announces how many times are available", async () => {
    show();

    await screen.findByRole("button", { name: /10:00.*Select this time/ });

    expect(screen.getByRole("status")).toHaveTextContent("3 appointment times available.");
  });

  it("says so when no times are open and offers the contact page", async () => {
    show({ slots: () => jsonResponse([]) });

    expect(await screen.findByRole("heading", { name: "No open times right now" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "send us a message" })).toHaveAttribute("href", "/contact");
  });

  it("shows the problem and a Try again button when loading fails", async () => {
    show({ slots: () => jsonResponse({ message: "Booking is paused." }, false, 409) });

    const alert = await screen.findByRole("alert");

    expect(alert).toHaveTextContent("Booking is paused.");
    expect(screen.queryByText("Loading available times…")).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "No open times right now" })).not.toBeInTheDocument();
  });

  it("loads the times again when Try again is pressed", async () => {
    let attempts = 0;
    show({
      slots: () => {
        attempts += 1;
        return attempts === 1 ? jsonResponse("", false, 500) : jsonResponse([TEN]);
      },
    });

    await userEvent.click(await screen.findByRole("button", { name: "Try again" }));

    expect(await screen.findByRole("button", { name: /10:00.*Select this time/ })).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});

describe("Appointments page: choosing a time without an account", () => {
  it("sends a visitor to sign up and then back to this page", async () => {
    show();

    await userEvent.click(await screen.findByRole("button", { name: /10:00.*Select this time/ }));

    expect(await screen.findByText("Landed on /signup?returnTo=%2Fappointments")).toBeInTheDocument();
  });

  it("explains that an administrator needs a client account", async () => {
    localStorage.setItem("adminToken", liveToken());
    show();

    await userEvent.click(await screen.findByRole("button", { name: /10:00.*Select this time/ }));

    const alert = await screen.findByRole("alert");

    expect(within(alert).getByText("Client account needed")).toBeInTheDocument();
    expect(alert).toHaveTextContent("Appointments are booked from a client account.");
    expect(screen.queryByText(/Landed on/)).not.toBeInTheDocument();
  });

  it("treats an expired client token like a visitor", async () => {
    const expired = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) - 60 }));
    localStorage.setItem("authToken", `h.${expired}.s`);
    show();

    await userEvent.click(await screen.findByRole("button", { name: /10:00.*Select this time/ }));

    expect(await screen.findByText(/Landed on \/signup/)).toBeInTheDocument();
  });
});

describe("Appointments page: the booking pop-up", () => {
  beforeEach(() => {
    localStorage.setItem("authToken", liveToken());
  });

  it("opens with the chosen date and time", async () => {
    show();

    const dialog = await pickTen();

    expect((within(dialog).getByLabelText("Date") as HTMLInputElement).value).toContain(
      "Monday, November 16, 2026"
    );
    expect((within(dialog).getByLabelText("Time") as HTMLInputElement).value).toMatch(
      /10:00.* to 11:00.*\(1 hour\)/
    );
  });

  it("fills in the name, email and phone from the account", async () => {
    show();

    const dialog = await pickTen();

    await waitFor(() => expect(within(dialog).getByLabelText("Name")).toHaveValue("Ava Client"));
    expect(within(dialog).getByLabelText("Email")).toHaveValue("ava@test.com");
    expect(within(dialog).getByLabelText("Contact number")).toHaveValue("+14165550100");
  });

  it("makes every field read only", async () => {
    show();

    const dialog = await pickTen();

    for (const label of ["Date", "Time", "Name", "Email", "Contact number"]) {
      expect(within(dialog).getByLabelText(label)).toHaveAttribute("readonly");
    }
  });

  it("says when no phone number was added", async () => {
    show({ profile: () => jsonResponse({ ...PROFILE, phoneNumber: null }) });

    const dialog = await pickTen();

    await waitFor(() =>
      expect(within(dialog).getByLabelText("Contact number")).toHaveValue("Not added to your account yet")
    );
  });

  it("keeps the request button off until the details have loaded", async () => {
    let finish: (value: unknown) => void = () => {};
    show({ profile: () => new Promise((resolve) => { finish = resolve; }) });

    const dialog = await pickTen();

    expect(within(dialog).getByRole("button", { name: "Request a booking" })).toBeDisabled();
    expect(within(dialog).getByLabelText("Name")).toHaveValue("Loading…");

    finish(await jsonResponse(PROFILE));

    await waitFor(() =>
      expect(within(dialog).getByRole("button", { name: "Request a booking" })).toBeEnabled()
    );
  });

  it("loads the account details only once for several bookings", async () => {
    const fetchMock = show();

    await pickTen();
    await userEvent.click(screen.getByRole("button", { name: "Cancel" }));
    await userEvent.click(screen.getByRole("button", { name: /Monday.*11:00 a\.m\. to 12:00/ }));
    await screen.findByRole("dialog", { name: "Request this appointment" });

    const profileCalls = fetchMock.mock.calls.filter(([url]) => String(url).includes("/api/auth/me"));
    expect(profileCalls).toHaveLength(1);
  });

  it("shows a message and keeps the request button off when the details cannot load", async () => {
    show({ profile: () => jsonResponse({ message: "Your account could not be read." }, false, 409) });

    const dialog = await pickTen();

    expect(await within(dialog).findByRole("alert")).toHaveTextContent("Your account could not be read.");
    expect(within(dialog).getByRole("button", { name: "Request a booking" })).toBeDisabled();
  });

  it("sends a client whose session ran out to the login page", async () => {
    show({ profile: () => jsonResponse("", false, 401) });

    await userEvent.click(await screen.findByRole("button", { name: /10:00.*Select this time/ }));

    expect(
      await screen.findByText("Landed on /login?sessionExpired=true&returnTo=%2Fappointments")
    ).toBeInTheDocument();
    expect(localStorage.getItem("authToken")).toBeNull();
  });

  it("closes without booking when Cancel is pressed", async () => {
    const fetchMock = show();

    const dialog = await pickTen();
    await userEvent.click(within(dialog).getByRole("button", { name: "Cancel" }));

    expect(screen.queryByRole("dialog", { name: "Request this appointment" })).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === "POST")).toBe(false);
  });
});

describe("Appointments page: sending the request", () => {
  beforeEach(() => {
    localStorage.setItem("authToken", liveToken());
  });

  async function requestIt() {
    const dialog = await pickTen();
    const send = within(dialog).getByRole("button", { name: "Request a booking" });

    await waitFor(() => expect(send).toBeEnabled());
    await userEvent.click(send);
  }

  it("posts the chosen start time", async () => {
    const fetchMock = show();

    await requestIt();
    await screen.findByText("Request sent");

    const post = fetchMock.mock.calls.find(([, init]) => init?.method === "POST")!;
    expect(JSON.parse(String(post[1]?.body))).toEqual({ startsAt: "2026-11-16T10:00:00" });
  });

  it("shows a success message with a link to My Bookings", async () => {
    show();

    await requestIt();

    const status = await screen.findByText("Request sent");
    const box = status.closest("[role=status]")!;

    expect(box).toHaveTextContent(/under My Bookings on your dashboard/);
    expect(within(box as HTMLElement).getByRole("link", { name: "Go to My Bookings" })).toHaveAttribute(
      "href",
      "/client-dashboard"
    );
    expect(screen.queryByRole("dialog", { name: "Request this appointment" })).not.toBeInTheDocument();
  });

  it("loads the list again after a request so the time can no longer be chosen twice", async () => {
    let calls = 0;
    show({
      slots: () => {
        calls += 1;
        return jsonResponse(calls === 1 ? [TEN, ELEVEN] : [ELEVEN]);
      },
    });

    await requestIt();
    await screen.findByText("Request sent");

    await waitFor(() =>
      expect(screen.queryByRole("button", { name: /10:00.*Select this time/ })).not.toBeInTheDocument()
    );
    expect(screen.getByRole("button", { name: /11:00.*Select this time/ })).toBeInTheDocument();
  });

  it("closes the success message with Close", async () => {
    show();

    await requestIt();
    await screen.findByText("Request sent");
    await userEvent.click(screen.getByRole("button", { name: "Close" }));

    expect(screen.queryByText("Request sent")).not.toBeInTheDocument();
  });

  it("says the time is taken when someone else booked it first", async () => {
    let calls = 0;
    show({
      slots: () => {
        calls += 1;
        return jsonResponse(calls === 1 ? [TEN, ELEVEN] : [ELEVEN]);
      },
      book: () =>
        jsonResponse(
          { message: "This appointment time is no longer available. Please select another time." },
          false,
          409
        ),
    });

    await requestIt();

    const alert = await screen.findByRole("alert");

    expect(within(alert).getByText("That time is taken")).toBeInTheDocument();
    expect(alert).toHaveTextContent("This appointment time is no longer available. Please select another time.");
    expect(screen.queryByRole("dialog", { name: "Request this appointment" })).not.toBeInTheDocument();

    await waitFor(() =>
      expect(screen.queryByRole("button", { name: /10:00.*Select this time/ })).not.toBeInTheDocument()
    );
  });

  it("shows the server message for any other refusal and keeps the pop-up open", async () => {
    show({
      book: () => jsonResponse({ message: "Appointment booking is not available right now." }, false, 400),
    });

    await requestIt();

    const alert = await screen.findByText("We could not send your request");

    expect(alert.closest("[role=alert]")).toHaveTextContent("Appointment booking is not available right now.");
  });

  it("sends a client whose session ran out to the login page", async () => {
    show({ book: () => jsonResponse("", false, 401) });

    await requestIt();

    expect(
      await screen.findByText("Landed on /login?sessionExpired=true&returnTo=%2Fappointments")
    ).toBeInTheDocument();
    expect(localStorage.getItem("authToken")).toBeNull();
  });
});
