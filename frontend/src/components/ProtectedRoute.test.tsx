import { describe, it, expect, afterEach, beforeEach, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import ProtectedRoute from "./ProtectedRoute";
import { jsonResponse } from "../test/render";

// An admin token only counts once the email one-time code was entered ("mfa": true).
function token(
  role: string,
  exp = Math.floor(Date.now() / 1000) + 3600,
  mfa = role === "ADMIN"
) {
  const payload = btoa(JSON.stringify({ role, exp, mfa })).replace(/=/g, "");
  return `header.${payload}.signature`;
}
function view(role: "ADMIN" | "CLIENT") {
  return render(<MemoryRouter initialEntries={["/private"]}><Routes>
    <Route path="/private" element={<ProtectedRoute allowedRole={role}><h1>Private content</h1></ProtectedRoute>} />
    <Route path="/login" element={<h1>Login page</h1>} />
    <Route path="/admin-dashboard" element={<h1>Admin dashboard</h1>} />
    <Route path="/client-dashboard" element={<h1>Client dashboard</h1>} />
  </Routes></MemoryRouter>);
}

beforeEach(() => {
  // Admin pages also show the account settings, which load the admin profile.
  vi.stubGlobal(
    "fetch",
    vi.fn(() => jsonResponse({ fullName: "Admin", email: "admin@mail.com" }))
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
  localStorage.clear();
});

describe("ProtectedRoute", () => {
  it("allows a valid client token", () => { localStorage.setItem("authToken", token("CLIENT")); view("CLIENT"); expect(screen.getByText("Private content")).toBeInTheDocument(); });
  it("allows a valid admin token that completed the email code", () => { localStorage.setItem("adminToken", token("ADMIN")); view("ADMIN"); expect(screen.getByText("Private content")).toBeInTheDocument(); });
  it("rejects an admin token that did not complete the email code", () => {
    localStorage.setItem("adminToken", token("ADMIN", undefined, false));
    view("ADMIN");
    expect(screen.getByText("Login page")).toBeInTheDocument();
    expect(screen.queryByText("Private content")).not.toBeInTheDocument();
    expect(localStorage.getItem("adminToken")).toBeNull();
  });
  it("does not need the email code for a client token", () => {
    localStorage.setItem("authToken", token("CLIENT", undefined, false));
    view("CLIENT");
    expect(screen.getByText("Private content")).toBeInTheDocument();
  });
  it("redirects a visitor without a token to login", () => { view("CLIENT"); expect(screen.getByText("Login page")).toBeInTheDocument(); });
  it("redirects a client attempting an admin route to their dashboard", () => { localStorage.setItem("authToken", token("CLIENT")); view("ADMIN"); expect(screen.getByText("Client dashboard")).toBeInTheDocument(); });
  it("redirects an admin attempting a client route to their dashboard", () => { localStorage.setItem("adminToken", token("ADMIN")); view("CLIENT"); expect(screen.getByText("Admin dashboard")).toBeInTheDocument(); });
  it("clears expired tokens and redirects with sessionExpired", () => { localStorage.setItem("authToken", token("CLIENT", 1)); render(<MemoryRouter initialEntries={["/private"]}><Routes><Route path="/private" element={<ProtectedRoute allowedRole="CLIENT"><h1>Private</h1></ProtectedRoute>} /><Route path="/login" element={<p>Login page</p>} /></Routes></MemoryRouter>); expect(screen.getByText("Login page")).toBeInTheDocument(); expect(localStorage.getItem("authToken")).toBeNull(); });
  it("clears malformed tokens", () => { localStorage.setItem("adminToken", "bad-token"); view("ADMIN"); expect(localStorage.getItem("adminToken")).toBeNull(); });
});