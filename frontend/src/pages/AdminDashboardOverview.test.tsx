import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { screen } from "@testing-library/react";
import AdminDashboard from "./AdminDashboard";
import { renderApp, jsonResponse } from "../test/render";

/*
 * Admin dashboard: the summary cards at the top.
 *
 * The "Pending Intake Requests" number is now loaded as soon as the
 * dashboard opens, even though the admin starts on "Contact Messages".
 */

type Route = [RegExp, () => Promise<unknown>];

function stubRoutes(routes: Route[]) {
  vi.stubGlobal(
    "fetch",
    vi.fn((url: string) => {
      const route = routes.find(([pattern]) => pattern.test(url));
      return route ? route[1]() : jsonResponse([]);
    })
  );
}

function intake(id: number, status: string) {
  return {
    id,
    clientFullName: `Client ${id}`,
    parentGuardianName: "Sam Parent",
    contactInfo: "sam@mail.com",
    preferredContactMethod: "Email",
    overview: "Overview",
    supportNeeds: "Needs",
    status,
    createdAt: "2026-01-02T10:00:00",
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

describe("AdminDashboard overview", () => {
  it("counts pending intake requests when the dashboard opens", async () => {
    stubRoutes([
      [
        /\/api\/admin\/intake-submissions$/,
        () =>
          jsonResponse([
            intake(1, "SUBMITTED"),
            intake(2, "IN_PROGRESS"),
            intake(3, "ACCEPTED"),
            intake(4, "REJECTED"),
          ]),
      ],
    ]);
    renderApp("/admin", <AdminDashboard />);

    // Only the first two are waiting for a decision.
    await vi.waitFor(() =>
      expect(
        screen.getByText("Pending Intake Requests").parentElement
      ).toHaveTextContent("2")
    );
  });

  it("shows 0 pending intake requests when there are none", async () => {
    stubRoutes([[/\/api\/admin\/intake-submissions$/, () => jsonResponse([])]]);
    renderApp("/admin", <AdminDashboard />);

    expect(
      await screen.findByText("No contact messages have been received yet.")
    ).toBeInTheDocument();
    expect(
      screen.getByText("Pending Intake Requests").parentElement
    ).toHaveTextContent("0");
  });

  it("shows an error when the intake count cannot be loaded", async () => {
    stubRoutes([
      [
        /\/api\/admin\/intake-submissions$/,
        () => jsonResponse("Intake service is down", false, 500),
      ],
    ]);
    renderApp("/admin", <AdminDashboard />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Intake service is down"
    );
  });
});
