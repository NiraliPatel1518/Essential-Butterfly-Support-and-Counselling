import { describe, it, expect, vi, beforeEach } from "vitest";
import { act, renderHook } from "@testing-library/react";
import { MemoryRouter, useLocation } from "react-router-dom";
import type { ReactNode } from "react";
import { useAdminSession } from "./useAdminSession";
import { ApiError } from "../api/contentApi";

function setup() {
  let location = { pathname: "", search: "" };

  function Probe() {
    location = useLocation();
    return null;
  }

  const wrapper = ({ children }: { children: ReactNode }) => (
    <MemoryRouter initialEntries={["/admin"]}>
      <Probe />
      {children}
    </MemoryRouter>
  );

  const { result } = renderHook(() => useAdminSession(), { wrapper });

  return { handle: result.current, where: () => location };
}

beforeEach(() => localStorage.setItem("adminToken", "token"));

describe("useAdminSession", () => {
  it("handles an expired session: removes the token, tells the app and goes to login", () => {
    const heard = vi.fn();
    window.addEventListener("auth-changed", heard);
    const { handle, where } = setup();

    let handled = false;
    act(() => {
      handled = handle(new ApiError("Your session has expired.", 401));
    });

    expect(handled).toBe(true);
    expect(localStorage.getItem("adminToken")).toBeNull();
    expect(heard).toHaveBeenCalledTimes(1);
    expect(where().pathname).toBe("/login");
    expect(where().search).toBe("?sessionExpired=true");

    window.removeEventListener("auth-changed", heard);
  });

  it.each([400, 403, 404, 409, 500])("does not treat a %i error as an expired session", (status) => {
    const { handle, where } = setup();

    expect(handle(new ApiError("Something else.", status))).toBe(false);
    expect(localStorage.getItem("adminToken")).toBe("token");
    expect(where().pathname).toBe("/admin");
  });

  it("ignores errors that are not ApiError", () => {
    const { handle } = setup();

    expect(handle(new Error("plain"))).toBe(false);
    expect(handle("text")).toBe(false);
    expect(handle(null)).toBe(false);
    expect(localStorage.getItem("adminToken")).toBe("token");
  });
});
