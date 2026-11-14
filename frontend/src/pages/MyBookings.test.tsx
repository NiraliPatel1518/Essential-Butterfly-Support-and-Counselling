import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MyBookings from "./MyBookings";
import { renderApp, jsonResponse } from "../test/render";
import type { ClientAppointment } from "../api/appointmentApi";

function booking(overrides: Partial<ClientAppointment> = {}): ClientAppointment {
  return {
    id: 1,
    status: "REQUESTED",
    startsAt: "2026-11-16T10:00:00",
    endsAt: "2026-11-16T11:00:00",
    statusReason: null,
    meetingLink: null,
    feedback: null,
    feedbackUpdatedAt: null,
    requestedAt: "2026-11-10T09:00:00",
    ...overrides,
  };
}

function features(appointments: boolean) {
  return [
    { featureKey: "APPOINTMENTS", label: "Appointments", enabled: appointments, displayOrder: 4 },
  ];
}

// Answers the bookings URL and the public features URL.
function stubApi(bookings: () => Promise<unknown>, appointmentsOn = true) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    void init;
    if (String(url).includes("/api/appointments/mine")) {
      return bookings();
    }
    if (String(url).includes("/api/features")) {
      return jsonResponse(features(appointmentsOn));
    }
    return jsonResponse([]);
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function showBookings(list: ClientAppointment[], appointmentsOn = true) {
  stubApi(() => jsonResponse(list), appointmentsOn);
  return renderApp("/my-bookings", <MyBookings />);
}

beforeEach(() => {
  localStorage.setItem("authToken", "client-token");

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

describe("MyBookings page", () => {
  it("shows the heading and a link back to the dashboard", async () => {
    showBookings([]);

    expect(screen.getByRole("heading", { name: "My Bookings" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /back to my dashboard/i })).toHaveAttribute(
      "href",
      "/client-dashboard"
    );
    await screen.findByText("You have no bookings yet.");
  });

  it("shows a loading message first", async () => {
    showBookings([]);

    expect(screen.getByRole("status")).toHaveTextContent("Loading your bookings…");
    await screen.findByText("You have no bookings yet.");
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("says so when there are no bookings", async () => {
    showBookings([]);

    expect(await screen.findByText("You have no bookings yet.")).toBeInTheDocument();
  });

  it("shows the date, time range and status of a booking", async () => {
    showBookings([booking()]);

    const item = (await screen.findByText(/Monday, November 16, 2026/)).closest("li")!;

    expect(within(item).getByText(/10:00.* to 11:00/)).toBeInTheDocument();
    expect(within(item).getByText("Requested")).toBeInTheDocument();
    expect(within(item).getByText("Status:")).toBeInTheDocument();
  });

  it("tells the client a requested booking is waiting for a reply", async () => {
    showBookings([booking({ status: "REQUESTED" })]);

    expect(await screen.findByText("Your request is waiting for a reply.")).toBeInTheDocument();
    expect(screen.queryByText(/Meeting link/)).not.toBeInTheDocument();
  });

  it("shows the meeting link of a confirmed booking", async () => {
    showBookings([booking({ status: "CONFIRMED", meetingLink: "https://meet.example.org/room" })]);

    const link = await screen.findByRole("link", { name: /open the meeting link/i });

    expect(link).toHaveAttribute("href", "https://meet.example.org/room");
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
  });

  it("says the link is not shared yet when a confirmed booking has none", async () => {
    showBookings([booking({ status: "CONFIRMED", meetingLink: null })]);

    expect(
      await screen.findByText(/Not shared yet\. It will be emailed to you\./)
    ).toBeInTheDocument();
  });

  it("also shows the meeting link on a completed booking", async () => {
    showBookings([booking({ status: "COMPLETED", meetingLink: "https://meet.example.org/old" })]);

    expect(await screen.findByRole("link", { name: /open the meeting link/i })).toBeInTheDocument();
  });

  it("does not show a meeting link on declined or cancelled bookings", async () => {
    showBookings([
      booking({ id: 1, status: "DECLINED", statusReason: "Fully booked" }),
      booking({ id: 2, status: "CANCELLED", statusReason: "Therapist is ill" }),
    ]);

    await screen.findByText("Fully booked");

    expect(screen.queryByText(/Meeting link/)).not.toBeInTheDocument();
  });

  it("shows the reason for a declined booking", async () => {
    showBookings([booking({ status: "DECLINED", statusReason: "Fully booked that day" })]);

    expect(await screen.findByText("Reason for declining:")).toBeInTheDocument();
    expect(screen.getByText("Fully booked that day")).toBeInTheDocument();
    expect(screen.getByText("Declined")).toBeInTheDocument();
  });

  it("shows the reason for a cancelled booking", async () => {
    showBookings([booking({ status: "CANCELLED", statusReason: "Therapist is ill" })]);

    expect(await screen.findByText("Reason for cancelling:")).toBeInTheDocument();
    expect(screen.getByText("Therapist is ill")).toBeInTheDocument();
    expect(screen.getByText("Cancelled")).toBeInTheDocument();
  });

  it("shows a word for every status", async () => {
    showBookings([
      booking({ id: 1, status: "REQUESTED" }),
      booking({ id: 2, status: "CONFIRMED" }),
      booking({ id: 3, status: "DECLINED", statusReason: "x" }),
      booking({ id: 4, status: "CANCELLED", statusReason: "y" }),
      booking({ id: 5, status: "COMPLETED" }),
    ]);

    for (const word of ["Requested", "Confirmed", "Declined", "Cancelled", "Completed"]) {
      expect(await screen.findByText(word)).toBeInTheDocument();
    }
  });

  it("shows several bookings in the order the server sent them", async () => {
    showBookings([
      booking({ id: 1, startsAt: "2026-11-23T10:00:00", endsAt: "2026-11-23T11:00:00" }),
      booking({ id: 2, startsAt: "2026-11-16T10:00:00", endsAt: "2026-11-16T11:00:00" }),
    ]);

    await screen.findByText(/November 23, 2026/);
    const items = screen.getAllByRole("listitem");

    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent("November 23");
    expect(items[1]).toHaveTextContent("November 16");
  });

  it("says feedback is not available yet when there is none", async () => {
    showBookings([booking()]);

    expect(await screen.findByText("Not available yet")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "View feedback" })).not.toBeInTheDocument();
  });

  it("opens the feedback in a dialog and closes it again", async () => {
    showBookings([
      booking({
        status: "COMPLETED",
        feedback: "Great first session",
        feedbackUpdatedAt: "2026-11-16T12:00:00",
      }),
    ]);

    await userEvent.click(await screen.findByRole("button", { name: "View feedback" }));

    const dialog = await screen.findByRole("dialog", { name: "Feedback" });

    expect(within(dialog).getByText("Great first session")).toBeInTheDocument();
    expect(within(dialog).getByText(/Last updated/)).toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole("button", { name: "Close" }));

    expect(screen.queryByText("Great first session")).not.toBeInTheDocument();
  });

  it("shows when the feedback was last updated next to the button", async () => {
    showBookings([
      booking({
        status: "COMPLETED",
        feedback: "Well done",
        feedbackUpdatedAt: "2026-11-16T12:00:00",
      }),
    ]);

    expect(await screen.findByText(/Updated Monday, November 16, 2026/)).toBeInTheDocument();
  });

  it("links to the available appointments while the feature is on", async () => {
    showBookings([], true);

    expect(
      await screen.findByRole("link", { name: /view available appointments/i })
    ).toHaveAttribute("href", "/appointments");
  });

  it("hides the link to available appointments while the feature is off", async () => {
    showBookings([], false);

    await screen.findByText("You have no bookings yet.");

    expect(screen.queryByRole("link", { name: /view available appointments/i })).not.toBeInTheDocument();
  });

  it("shows the server message and a Try again button when loading fails", async () => {
    stubApi(() => jsonResponse({ message: "Bookings are unavailable right now." }, false, 409));
    renderApp("/my-bookings", <MyBookings />);

    const alert = await screen.findByRole("alert");

    expect(alert).toHaveTextContent("Bookings are unavailable right now.");
    expect(within(alert).getByRole("button", { name: "Try again" })).toBeInTheDocument();
    expect(screen.queryByText("You have no bookings yet.")).not.toBeInTheDocument();
  });

  it("loads again when Try again is pressed", async () => {
    let attempts = 0;
    stubApi(() => {
      attempts += 1;
      return attempts === 1
        ? jsonResponse("", false, 500)
        : jsonResponse([booking({ status: "CONFIRMED" })]);
    });
    renderApp("/my-bookings", <MyBookings />);

    await userEvent.click(await screen.findByRole("button", { name: "Try again" }));

    expect(await screen.findByText("Confirmed")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("asks for the bookings with the client token", async () => {
    const fetchMock = stubApi(() => jsonResponse([]));
    renderApp("/my-bookings", <MyBookings />);

    await screen.findByText("You have no bookings yet.");

    const call = fetchMock.mock.calls.find(([url]) => String(url).includes("/api/appointments/mine"))!;
    expect(call[1]?.headers).toMatchObject({
      Authorization: "Bearer client-token",
    });
  });
});
