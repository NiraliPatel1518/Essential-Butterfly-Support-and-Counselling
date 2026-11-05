import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, act } from "@testing-library/react";
import { MemoryRouter, useLocation } from "react-router-dom";
import SessionTimeout from "./SessionTimeout";

function LocationDisplay() {
  const location = useLocation();

  return (
    <p>
      {location.pathname}
      {location.search}
    </p>
  );
}

function renderSessionTimeout() {
  return render(
    <MemoryRouter initialEntries={["/dashboard"]}>
      <SessionTimeout />
      <LocationDisplay />
    </MemoryRouter>
  );
}

describe("SessionTimeout", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    localStorage.clear();
  });

  afterEach(() => {
    vi.clearAllTimers();
    vi.useRealTimers();
    localStorage.clear();
  });

  it("does not start a timer without an authenticated session", () => {
    renderSessionTimeout();

    act(() => {
      vi.advanceTimersByTime(31 * 60 * 1000);
    });

    expect(document.body).toHaveTextContent("/dashboard");
  });

  it("logs out a client after 30 minutes of inactivity", () => {
    localStorage.setItem("authToken", "client-token");

    renderSessionTimeout();

    act(() => {
      vi.advanceTimersByTime(30 * 60 * 1000);
    });

    expect(document.body).toHaveTextContent(
      "/login?sessionExpired=true"
    );

    expect(localStorage.getItem("authToken")).toBeNull();
  });

  it("logs out an admin after 30 minutes of inactivity", () => {
    localStorage.setItem("adminToken", "admin-token");

    renderSessionTimeout();

    act(() => {
      vi.advanceTimersByTime(30 * 60 * 1000);
    });

    expect(document.body).toHaveTextContent(
  "/login?sessionExpired=true"
);

    expect(localStorage.getItem("adminToken")).toBeNull();
  });
});