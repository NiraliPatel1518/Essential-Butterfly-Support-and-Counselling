import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AdminDashboard from "./AdminDashboard";
import { renderApp, jsonResponse } from "../test/render";

type Route = [RegExp, (init?: RequestInit) => Promise<unknown>];

// The first matching route answers. Anything else gets an empty list.
function stubRoutes(routes: Route[]) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    const route = routes.find(([pattern]) => pattern.test(url));
    return route ? route[1](init) : jsonResponse([]);
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function contact(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    fullName: "Sam Parent",
    email: "sam@mail.com",
    subject: "Question about tutoring",
    message: "Do you have space in January?",
    status: "NEW",
    createdAt: "2026-01-01T10:00:00",
    ...overrides,
  };
}

function intake(overrides: Record<string, unknown> = {}) {
  return {
    id: 7,
    clientFullName: "Riley Client",
    parentGuardianName: "Sam Parent",
    contactInfo: "sam@mail.com",
    preferredContactMethod: "Email",
    overview: "Overview text",
    supportNeeds: "Support needs text",
    respiteGoals: "Respite goals",
    subjectFocus: "Math",
    additionalNotes: "None",
    status: "SUBMITTED",
    createdAt: "2026-01-02T10:00:00",
    ...overrides,
  };
}

beforeEach(() => {
  localStorage.clear();
  localStorage.setItem("adminToken", "admin-token");
});

afterEach(() => {
  vi.unstubAllGlobals();
  localStorage.clear();
});

describe("AdminDashboard", () => {
  it("lists contact messages and counts the new ones", async () => {
    stubRoutes([
      [
        /\/api\/admin\/contact-messages$/,
        () =>
          jsonResponse([
            contact({ id: 1 }),
            contact({ id: 2, fullName: "Alex Other", status: "NEW" }),
            contact({ id: 3, fullName: "Old Message", status: "VIEWED" }),
          ]),
      ],
    ]);
    renderApp("/admin", <AdminDashboard />);

    expect(await screen.findByText("Sam Parent")).toBeInTheDocument();
    expect(screen.getByText("Alex Other")).toBeInTheDocument();
    expect(screen.getByText("3 total")).toBeInTheDocument();
    expect(
      screen.getByText("New Contact Messages").parentElement
    ).toHaveTextContent("2");
  });

  it("says so when there are no contact messages", async () => {
    stubRoutes([[/\/api\/admin\/contact-messages$/, () => jsonResponse([])]]);
    renderApp("/admin", <AdminDashboard />);

    expect(
      await screen.findByText("No contact messages have been received yet.")
    ).toBeInTheDocument();
  });

  it("opens a contact message and marks it as viewed", async () => {
    stubRoutes([
      [
        /\/api\/admin\/contact-messages\/1$/,
        () => jsonResponse(contact({ status: "VIEWED" })),
      ],
      [/\/api\/admin\/contact-messages$/, () => jsonResponse([contact()])],
    ]);
    const user = userEvent.setup();
    renderApp("/admin", <AdminDashboard />);

    await user.click(await screen.findByRole("button", { name: /view message/i }));

    expect(
      await screen.findByRole("heading", { name: "Question about tutoring" })
    ).toBeInTheDocument();
    expect(screen.getByText("Do you have space in January?")).toBeInTheDocument();
    expect(screen.getAllByText("Viewed").length).toBeGreaterThan(0);
  });

  it("shows an error message when contact messages cannot be loaded", async () => {
    stubRoutes([
      [/\/api\/admin\/contact-messages$/, () => jsonResponse("Boom", false, 500)],
    ]);
    renderApp("/admin", <AdminDashboard />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Boom");
  });

  it("opens an intake request and offers accept and reject", async () => {
    stubRoutes([
      [/\/api\/admin\/intake-submissions\/7$/, () => jsonResponse(intake())],
      [/\/api\/admin\/intake-submissions$/, () => jsonResponse([intake()])],
    ]);
    const user = userEvent.setup();
    renderApp("/admin", <AdminDashboard />);

    await user.click(screen.getByRole("button", { name: "Intake Requests" }));
    await user.click(await screen.findByRole("button", { name: /review request/i }));

    expect(
      await screen.findByRole("heading", { name: "Riley Client" })
    ).toBeInTheDocument();
    expect(screen.getByText("Support needs text")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Accept Request" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Reject Request" })).toBeInTheDocument();
  });

  it("accepts an intake request and confirms it", async () => {
    stubRoutes([
      [
        /\/api\/admin\/intake-submissions\/7\/accept$/,
        () => jsonResponse(intake({ status: "ACCEPTED" })),
      ],
      [/\/api\/admin\/intake-submissions\/7$/, () => jsonResponse(intake())],
      [/\/api\/admin\/intake-submissions$/, () => jsonResponse([intake()])],
    ]);
    const user = userEvent.setup();
    renderApp("/admin", <AdminDashboard />);

    await user.click(screen.getByRole("button", { name: "Intake Requests" }));
    await user.click(await screen.findByRole("button", { name: /review request/i }));
    await user.click(await screen.findByRole("button", { name: "Accept Request" }));

    expect(
      await screen.findByText("Intake request accepted successfully.")
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Accept Request" })).not.toBeInTheDocument();
  });

  it("needs a reason before an intake request can be rejected", async () => {
    const fetchMock = stubRoutes([
      [
        /\/api\/admin\/intake-submissions\/7\/reject$/,
        () => jsonResponse(intake({ status: "REJECTED", rejectionReason: "Full" })),
      ],
      [/\/api\/admin\/intake-submissions\/7$/, () => jsonResponse(intake())],
      [/\/api\/admin\/intake-submissions$/, () => jsonResponse([intake()])],
    ]);
    const user = userEvent.setup();
    renderApp("/admin", <AdminDashboard />);

    await user.click(screen.getByRole("button", { name: "Intake Requests" }));
    await user.click(await screen.findByRole("button", { name: /review request/i }));
    await user.click(await screen.findByRole("button", { name: "Reject Request" }));

    const confirmButton = screen.getByRole("button", { name: "Confirm Rejection" });
    expect(confirmButton).toBeDisabled();

    await user.type(screen.getByLabelText(/rejection reason/i), "  Full  ");
    expect(confirmButton).toBeEnabled();
    await user.click(confirmButton);

    expect(
      await screen.findByText("Intake request rejected successfully.")
    ).toBeInTheDocument();

    const rejectCall = fetchMock.mock.calls.find(([url]) => /reject$/.test(url));
    expect(rejectCall).toBeDefined();
    expect(JSON.parse(String(rejectCall![1]?.body))).toEqual({
      rejectionReason: "Full",
    });
  });

  it("switches to the testimonials section", async () => {
    stubRoutes([]);
    const user = userEvent.setup();
    renderApp("/admin", <AdminDashboard />);

    await user.click(screen.getByRole("button", { name: "Testimonials" }));

    expect(await screen.findByText("No pending testimonials.")).toBeInTheDocument();
  });
});