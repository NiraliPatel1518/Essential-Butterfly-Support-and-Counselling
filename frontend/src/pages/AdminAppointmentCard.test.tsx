import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AdminAppointmentCard from "./AdminAppointmentCard";
import { jsonResponse } from "../test/render";
import { ApiError } from "../api/appointmentApi";
import type { AdminAppointment } from "../api/appointmentApi";

function appointment(overrides: Partial<AdminAppointment> = {}): AdminAppointment {
  return {
    id: 5,
    status: "REQUESTED",
    startsAt: "2026-11-16T10:00:00",
    endsAt: "2026-11-16T11:00:00",
    clientName: "Ava Client",
    clientEmail: "ava@test.com",
    clientPhone: "+14165550100",
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

const confirmed = (overrides: Partial<AdminAppointment> = {}) =>
  appointment({ status: "CONFIRMED", ...overrides });

const started = (overrides: Partial<AdminAppointment> = {}) =>
  confirmed({ started: true, canCancel: false, ...overrides });

type Reply = () => Promise<unknown>;

// Answers one admin step (for example "/confirm") with the given reply.
function stubStep(step: string, reply: Reply) {
  const fetchMock = vi.fn((url: string) =>
    String(url).includes(step) ? reply() : jsonResponse({})
  );
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function lastCall(fetchMock: ReturnType<typeof vi.fn>) {
  const [url, init] = fetchMock.mock.calls[fetchMock.mock.calls.length - 1] as [string, RequestInit];
  return { url: String(url), init: init ?? {} };
}

function show(item: AdminAppointment) {
  const onChanged = vi.fn();
  const onFailure = vi.fn();

  render(
    <AdminAppointmentCard appointment={item} scope="test" onChanged={onChanged} onFailure={onFailure} />
  );

  return { onChanged, onFailure };
}

beforeEach(() => {
  localStorage.setItem("adminToken", "admin-token");

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

describe("AdminAppointmentCard: the details", () => {
  it("shows the client, time, status, email, phone and request date", () => {
    show(appointment());

    expect(screen.getByRole("heading", { name: "Ava Client" })).toBeInTheDocument();
    expect(screen.getByText(/Monday, November 16, 2026/)).toBeInTheDocument();
    expect(screen.getByText(/10:00.* to 11:00/)).toBeInTheDocument();
    expect(screen.getAllByText("Requested")).toHaveLength(2);
    expect(screen.getByText("ava@test.com")).toBeInTheDocument();
    expect(screen.getByText("+14165550100")).toBeInTheDocument();
    expect(screen.getByText(/Tuesday, November 10, 2026|Tuesday, November 10/)).toBeInTheDocument();
  });

  it("says when there is no email or phone", () => {
    show(appointment({ clientEmail: null, clientPhone: null }));

    expect(screen.getByText("Not available")).toBeInTheDocument();
    expect(screen.getByText("Not added")).toBeInTheDocument();
  });

  it("shows the reason for a declined request", () => {
    show(appointment({ status: "DECLINED", statusReason: "Fully booked" }));

    expect(screen.getByText("Reason for declining")).toBeInTheDocument();
    expect(screen.getByText("Fully booked")).toBeInTheDocument();
    expect(screen.getByText("Declined")).toBeInTheDocument();
  });

  it("shows the reason for a cancelled appointment", () => {
    show(appointment({ status: "CANCELLED", statusReason: "Therapist is ill" }));

    expect(screen.getByText("Reason for cancelling")).toBeInTheDocument();
    expect(screen.getByText("Therapist is ill")).toBeInTheDocument();
  });

  it("shows no reason row when there is no reason", () => {
    show(appointment());

    expect(screen.queryByText(/Reason for/)).not.toBeInTheDocument();
  });

  it("warns that feedback is needed for a completed appointment", () => {
    show(appointment({ status: "COMPLETED", feedbackDue: true }));

    expect(screen.getByText(/Feedback needed: this appointment is completed\./)).toBeInTheDocument();
  });

  it("does not warn when no feedback is due", () => {
    show(appointment({ status: "COMPLETED", feedbackDue: false }));

    expect(screen.queryByText(/Feedback needed/)).not.toBeInTheDocument();
  });
});

describe("AdminAppointmentCard: which buttons show", () => {
  it("a requested appointment offers Accept and Decline only", () => {
    show(appointment());

    expect(screen.getByRole("button", { name: "Accept request" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Decline request" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Send invitation/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Feedback/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Cancel appointment/ })).not.toBeInTheDocument();
  });

  it("a confirmed appointment offers the invitation, feedback, notes, calendar and cancel", () => {
    show(confirmed());

    expect(screen.queryByRole("button", { name: "Accept request" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Send invitation/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Feedback/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Private notes/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add to Google Calendar" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Cancel appointment" })).toBeInTheDocument();
  });

  it("hides Cancel once the appointment can no longer be cancelled", () => {
    show(started());

    expect(screen.queryByRole("button", { name: "Cancel appointment" })).not.toBeInTheDocument();
  });

  it("shows that the event is on Google Calendar instead of the button", () => {
    show(confirmed({ calendarEventAdded: true }));

    expect(screen.getByText(/On Google Calendar/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Add to Google Calendar" })).not.toBeInTheDocument();
  });

  it("marks the invitation as sent", () => {
    show(confirmed({ invitationSentAt: "2026-11-12T08:00:00" }));

    expect(screen.getByRole("button", { name: /Send invitation\s*\(sent\)/ })).toBeInTheDocument();
  });

  it("a completed appointment offers feedback and notes but no cancel or calendar", () => {
    show(appointment({ status: "COMPLETED", started: true, canCancel: false }));

    expect(screen.getByRole("button", { name: /Feedback/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Private notes/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Send invitation/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Cancel appointment" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Add to Google Calendar" })).not.toBeInTheDocument();
  });

  it("declined and cancelled appointments have no actions at all", () => {
    for (const status of ["DECLINED", "CANCELLED"] as const) {
      const { unmount } = render(
        <AdminAppointmentCard
          appointment={appointment({ status, statusReason: "x" })}
          scope="test"
          onChanged={vi.fn()}
          onFailure={vi.fn()}
        />
      );

      expect(screen.queryAllByRole("button")).toHaveLength(0);
      unmount();
    }
  });

  it("shows whether feedback and notes were added", () => {
    show(started({ feedback: "Well done", privateNotes: "A note" }));

    expect(screen.getByRole("button", { name: /Feedback\s*\(added\)/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Private notes\s*\(added\)/ })).toBeInTheDocument();
  });

  it("shows when feedback and notes are still empty", () => {
    show(started());

    expect(screen.getByRole("button", { name: /Feedback\s*\(empty\)/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Private notes\s*\(empty\)/ })).toBeInTheDocument();
  });
});

describe("AdminAppointmentCard: accept", () => {
  it("accepts the request and reports the result", async () => {
    const updated = confirmed();
    const fetchMock = stubStep("/confirm", () => jsonResponse({ appointment: updated, warning: null }));
    const { onChanged, onFailure } = show(appointment());

    await userEvent.click(screen.getByRole("button", { name: "Accept request" }));

    await waitFor(() => expect(onChanged).toHaveBeenCalledWith({ appointment: updated, warning: null }));
    expect(onFailure).not.toHaveBeenCalled();

    const { url, init } = lastCall(fetchMock);
    expect(url).toMatch(/\/api\/admin\/appointments\/5\/confirm$/);
    expect(init.method).toBe("POST");
  });

  it("passes on a warning from the server", async () => {
    const result = { appointment: confirmed(), warning: "Google Calendar is not connected" };
    stubStep("/confirm", () => jsonResponse(result));
    const { onChanged } = show(appointment());

    await userEvent.click(screen.getByRole("button", { name: "Accept request" }));

    await waitFor(() => expect(onChanged).toHaveBeenCalledWith(result));
  });

  it("shows Working… and blocks other buttons while it runs", async () => {
    let finish: (value: unknown) => void = () => {};
    stubStep("/confirm", () => new Promise((resolve) => { finish = resolve; }));
    show(appointment());

    await userEvent.click(screen.getByRole("button", { name: "Accept request" }));

    expect(screen.getByRole("button", { name: "Working…" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Decline request" })).toBeDisabled();

    finish(await jsonResponse({ appointment: confirmed(), warning: null }));

    await waitFor(() => expect(screen.getByRole("button", { name: "Accept request" })).toBeEnabled());
  });

  it("hands the error to the page when it fails", async () => {
    stubStep("/confirm", () => jsonResponse({ message: "Only a requested appointment can be accepted." }, false, 409));
    const { onChanged, onFailure } = show(appointment());

    await userEvent.click(screen.getByRole("button", { name: "Accept request" }));

    await waitFor(() => expect(onFailure).toHaveBeenCalled());
    expect(onFailure.mock.calls[0][0]).toBeInstanceOf(ApiError);
    expect(onFailure.mock.calls[0][0].message).toBe("Only a requested appointment can be accepted.");
    expect(onChanged).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Accept request" })).toBeEnabled();
  });
});

describe("AdminAppointmentCard: decline", () => {
  async function openDecline() {
    await userEvent.click(screen.getByRole("button", { name: "Decline request" }));
    return screen.findByRole("dialog", { name: "Decline this request" });
  }

  it("opens a pop-up that asks for a required reason", async () => {
    show(appointment());

    const dialog = await openDecline();

    expect(within(dialog).getByLabelText(/Reason for declining \(required, shown to the client\)/)).toBeInTheDocument();
  });

  it("will not decline without a reason", async () => {
    const fetchMock = stubStep("/decline", () => jsonResponse({}));
    show(appointment());

    const dialog = await openDecline();
    await userEvent.type(within(dialog).getByRole("textbox"), "   ");
    await userEvent.click(within(dialog).getByRole("button", { name: "Decline request" }));

    expect(within(dialog).getByRole("alert")).toHaveTextContent(
      "Enter a short reason for declining this request."
    );
    expect(within(dialog).getByRole("textbox")).toHaveAttribute("aria-invalid", "true");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("sends the trimmed reason and closes the pop-up", async () => {
    const updated = appointment({ status: "DECLINED", statusReason: "Fully booked" });
    const fetchMock = stubStep("/decline", () => jsonResponse({ appointment: updated, warning: null }));
    const { onChanged } = show(appointment());

    const dialog = await openDecline();
    await userEvent.type(within(dialog).getByRole("textbox"), "  Fully booked  ");
    await userEvent.click(within(dialog).getByRole("button", { name: "Decline request" }));

    await waitFor(() => expect(onChanged).toHaveBeenCalled());
    expect(JSON.parse(String(lastCall(fetchMock).init.body))).toEqual({ reason: "Fully booked" });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("limits the reason to 500 characters", async () => {
    show(appointment());

    const dialog = await openDecline();

    expect(within(dialog).getByRole("textbox")).toHaveAttribute("maxlength", "500");
  });

  it("discards what was typed when the pop-up is closed", async () => {
    show(appointment());

    let dialog = await openDecline();
    await userEvent.type(within(dialog).getByRole("textbox"), "Half a reason");
    await userEvent.click(within(dialog).getByRole("button", { name: "Close" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    dialog = await openDecline();
    expect(within(dialog).getByRole("textbox")).toHaveValue("");
  });
});

describe("AdminAppointmentCard: cancel", () => {
  async function openCancel() {
    await userEvent.click(screen.getByRole("button", { name: "Cancel appointment" }));
    return screen.findByRole("dialog", { name: "Cancel this appointment" });
  }

  it("asks for an optional reason", async () => {
    show(confirmed());

    const dialog = await openCancel();

    expect(within(dialog).getByLabelText(/Reason for cancelling \(optional, shown to the client\)/)).toBeInTheDocument();
  });

  it("cancels without a reason", async () => {
    const updated = appointment({ status: "CANCELLED" });
    const fetchMock = stubStep("/cancel", () => jsonResponse({ appointment: updated, warning: null }));
    const { onChanged } = show(confirmed());

    const dialog = await openCancel();
    await userEvent.click(within(dialog).getByRole("button", { name: "Cancel appointment" }));

    await waitFor(() => expect(onChanged).toHaveBeenCalled());
    expect(lastCall(fetchMock).url).toMatch(/\/5\/cancel$/);
    expect(JSON.parse(String(lastCall(fetchMock).init.body))).toEqual({ reason: "" });
  });

  it("sends the reason when one is typed", async () => {
    const fetchMock = stubStep("/cancel", () =>
      jsonResponse({ appointment: appointment({ status: "CANCELLED" }), warning: null })
    );
    show(confirmed());

    const dialog = await openCancel();
    await userEvent.type(within(dialog).getByRole("textbox"), "Therapist is ill");
    await userEvent.click(within(dialog).getByRole("button", { name: "Cancel appointment" }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    expect(JSON.parse(String(lastCall(fetchMock).init.body))).toEqual({ reason: "Therapist is ill" });
  });

  it("passes on the calendar warning when the event could not be removed", async () => {
    const result = {
      appointment: appointment({ status: "CANCELLED" }),
      warning: "The appointment is cancelled, but its Google Calendar event could not be removed.",
    };
    stubStep("/cancel", () => jsonResponse(result));
    const { onChanged } = show(confirmed());

    const dialog = await openCancel();
    await userEvent.click(within(dialog).getByRole("button", { name: "Cancel appointment" }));

    await waitFor(() => expect(onChanged).toHaveBeenCalledWith(result));
  });
});

describe("AdminAppointmentCard: invitation", () => {
  async function openInvite() {
    await userEvent.click(screen.getByRole("button", { name: /Send invitation/ }));
    return screen.findByRole("dialog", { name: "Meeting invitation" });
  }

  it("says no invitation has been sent yet", async () => {
    show(confirmed());

    const dialog = await openInvite();

    expect(within(dialog).getByText("No invitation has been sent yet.")).toBeInTheDocument();
    expect(within(dialog).queryByRole("button", { name: "Resend saved link" })).not.toBeInTheDocument();
  });

  it("says a saved link has not been emailed yet and offers to resend it", async () => {
    show(confirmed({ meetingLink: "https://meet.example.org/a" }));

    const dialog = await openInvite();

    expect(within(dialog).getByText("The link is saved but has not been emailed yet.")).toBeInTheDocument();
    expect(within(dialog).getByLabelText(/Meeting link/)).toHaveValue("https://meet.example.org/a");
    expect(within(dialog).getByRole("button", { name: "Resend saved link" })).toBeInTheDocument();
  });

  it("says when the invitation was emailed", async () => {
    show(confirmed({ meetingLink: "https://meet.example.org/a", invitationSentAt: "2026-11-12T08:00:00" }));

    const dialog = await openInvite();

    expect(within(dialog).getByText(/Invitation emailed on .*November 12, 2026/)).toBeInTheDocument();
  });

  it("refuses a link that does not start with https://", async () => {
    const fetchMock = stubStep("/invitation", () => jsonResponse({}));
    show(confirmed());

    const dialog = await openInvite();

    for (const bad of ["http://meet.example.org/a", "meet.example.org", "https://"]) {
      const input = within(dialog).getByLabelText(/Meeting link/);
      await userEvent.clear(input);
      await userEvent.type(input, bad);
      await userEvent.click(within(dialog).getByRole("button", { name: "Send invitation" }));

      expect(within(dialog).getByRole("alert")).toHaveTextContent(
        "Paste a meeting link that starts with https://"
      );
    }

    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("sends a good link, trimmed, and closes the pop-up", async () => {
    const updated = confirmed({ meetingLink: "https://meet.example.org/a", invitationSentAt: "2026-11-13T08:00:00" });
    const fetchMock = stubStep("/invitation", () => jsonResponse({ appointment: updated, warning: null }));
    const { onChanged } = show(confirmed());

    const dialog = await openInvite();
    await userEvent.type(within(dialog).getByLabelText(/Meeting link/), "  https://meet.example.org/a  ");
    await userEvent.click(within(dialog).getByRole("button", { name: "Send invitation" }));

    await waitFor(() => expect(onChanged).toHaveBeenCalledWith({ appointment: updated, warning: null }));

    const { url, init } = lastCall(fetchMock);
    expect(url).toMatch(/\/5\/invitation$/);
    expect(init.method).toBe("PUT");
    expect(JSON.parse(String(init.body))).toEqual({ meetingLink: "https://meet.example.org/a" });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("resends the saved link", async () => {
    const updated = confirmed({ meetingLink: "https://meet.example.org/a", invitationSentAt: "2026-11-13T08:00:00" });
    const fetchMock = stubStep("/invitation/resend", () => jsonResponse({ appointment: updated, warning: null }));
    const { onChanged } = show(confirmed({ meetingLink: "https://meet.example.org/a" }));

    const dialog = await openInvite();
    await userEvent.click(within(dialog).getByRole("button", { name: "Resend saved link" }));

    await waitFor(() => expect(onChanged).toHaveBeenCalled());
    expect(lastCall(fetchMock).url).toMatch(/\/5\/invitation\/resend$/);
    expect(lastCall(fetchMock).init.method).toBe("POST");
  });

  it("keeps the saved link when the email could not be sent and shows the warning", async () => {
    const result = {
      appointment: confirmed({ meetingLink: "https://meet.example.org/a" }),
      warning: "The meeting link is saved, but the email could not be sent.",
    };
    stubStep("/invitation", () => jsonResponse(result));
    const { onChanged } = show(confirmed());

    const dialog = await openInvite();
    await userEvent.type(within(dialog).getByLabelText(/Meeting link/), "https://meet.example.org/a");
    await userEvent.click(within(dialog).getByRole("button", { name: "Send invitation" }));

    await waitFor(() => expect(onChanged).toHaveBeenCalledWith(result));
  });
});

describe("AdminAppointmentCard: calendar", () => {
  it("adds the appointment to Google Calendar", async () => {
    const updated = confirmed({ calendarEventAdded: true });
    const fetchMock = stubStep("/calendar", () => jsonResponse({ appointment: updated, warning: null }));
    const { onChanged } = show(confirmed());

    await userEvent.click(screen.getByRole("button", { name: "Add to Google Calendar" }));

    await waitFor(() => expect(onChanged).toHaveBeenCalledWith({ appointment: updated, warning: null }));
    expect(lastCall(fetchMock).url).toMatch(/\/5\/calendar$/);
  });
});

describe("AdminAppointmentCard: feedback and private notes", () => {
  async function openFeedback() {
    await userEvent.click(screen.getByRole("button", { name: /^Feedback/ }));
    return screen.findByRole("dialog", { name: "Feedback" });
  }

  async function openNotes() {
    await userEvent.click(screen.getByRole("button", { name: /^Private notes/ }));
    return screen.findByRole("dialog", { name: "Private notes" });
  }

  it("shows the saved feedback to edit and when it was last updated", async () => {
    show(started({ feedback: "Well done", feedbackUpdatedAt: "2026-11-16T12:00:00" }));

    const dialog = await openFeedback();

    expect(within(dialog).getByLabelText(/Feedback \(the client can read this\)/)).toHaveValue("Well done");
    expect(within(dialog).getByText(/Last updated .*November 16, 2026.*The client sees this date\./)).toBeInTheDocument();
  });

  it("shows the saved private note to edit", async () => {
    show(started({ privateNotes: "A private worry" }));

    const dialog = await openNotes();

    expect(within(dialog).getByLabelText(/Private notes \(only you can see these\)/)).toHaveValue("A private worry");
  });

  it("explains that nothing can be saved before the appointment starts", async () => {
    show(confirmed());

    const dialog = await openFeedback();

    expect(within(dialog).getByText("This can be saved once the appointment has started.")).toBeInTheDocument();
  });

  it("does not show that note once the appointment has started", async () => {
    show(started());

    const dialog = await openFeedback();

    expect(within(dialog).queryByText(/can be saved once the appointment has started/)).not.toBeInTheDocument();
  });

  it("warns that closing discards what was typed", async () => {
    show(started());

    const dialog = await openFeedback();

    expect(within(dialog).getByText("Closing this window without saving discards what you typed.")).toBeInTheDocument();
  });

  it("saves only the feedback field", async () => {
    const saved = started({ feedback: "Great session" });
    const fetchMock = stubStep("/feedback", () => jsonResponse(saved));
    const { onChanged } = show(started());

    const dialog = await openFeedback();
    await userEvent.type(within(dialog).getByRole("textbox"), "  Great session  ");
    await userEvent.click(within(dialog).getByRole("button", { name: "Save" }));

    await waitFor(() => expect(onChanged).toHaveBeenCalledWith({ appointment: saved, warning: null }));

    const { url, init } = lastCall(fetchMock);
    expect(url).toMatch(/\/5\/feedback$/);
    expect(init.method).toBe("PUT");
    expect(JSON.parse(String(init.body))).toEqual({ feedback: "Great session" });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("saves only the private note field", async () => {
    const fetchMock = stubStep("/feedback", () => jsonResponse(started({ privateNotes: "Follow up" })));
    show(started());

    const dialog = await openNotes();
    await userEvent.type(within(dialog).getByRole("textbox"), "Follow up");
    await userEvent.click(within(dialog).getByRole("button", { name: "Save" }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    expect(JSON.parse(String(lastCall(fetchMock).init.body))).toEqual({ privateNotes: "Follow up" });
  });

  it("will not save empty feedback or an empty note", async () => {
    const fetchMock = stubStep("/feedback", () => jsonResponse({}));
    show(started());

    let dialog = await openFeedback();
    await userEvent.click(within(dialog).getByRole("button", { name: "Save" }));
    expect(within(dialog).getByRole("alert")).toHaveTextContent("Write some feedback before saving.");
    await userEvent.click(within(dialog).getByRole("button", { name: "Close" }));

    dialog = await openNotes();
    await userEvent.click(within(dialog).getByRole("button", { name: "Save" }));
    expect(within(dialog).getByRole("alert")).toHaveTextContent("Write a private note before saving.");

    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("limits the text to 4000 characters", async () => {
    show(started());

    const dialog = await openFeedback();

    expect(within(dialog).getByRole("textbox")).toHaveAttribute("maxlength", "4000");
  });

  it("shows a refusal from the server inside the pop-up and keeps what was typed", async () => {
    stubStep("/feedback", () =>
      jsonResponse({ message: "Feedback can be written once a confirmed appointment has started." }, false, 409)
    );
    const { onFailure } = show(confirmed());

    const dialog = await openFeedback();
    await userEvent.type(within(dialog).getByRole("textbox"), "Too early");
    await userEvent.click(within(dialog).getByRole("button", { name: "Save" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      "Feedback can be written once a confirmed appointment has started."
    );
    expect(within(dialog).getByRole("textbox")).toHaveValue("Too early");
    expect(onFailure).not.toHaveBeenCalled();
  });

  it("hands a server crash to the page instead of the pop-up", async () => {
    stubStep("/feedback", () => jsonResponse("", false, 500));
    const { onFailure } = show(started());

    const dialog = await openFeedback();
    await userEvent.type(within(dialog).getByRole("textbox"), "Some words");
    await userEvent.click(within(dialog).getByRole("button", { name: "Save" }));

    await waitFor(() => expect(onFailure).toHaveBeenCalled());
    expect(within(dialog).queryByRole("alert")).not.toBeInTheDocument();
  });

  it("hands an expired session to the page", async () => {
    stubStep("/feedback", () => jsonResponse("", false, 401));
    const { onFailure } = show(started());

    const dialog = await openFeedback();
    await userEvent.type(within(dialog).getByRole("textbox"), "Some words");
    await userEvent.click(within(dialog).getByRole("button", { name: "Save" }));

    await waitFor(() => expect(onFailure).toHaveBeenCalled());
    expect(onFailure.mock.calls[0][0].status).toBe(401);
  });
});
