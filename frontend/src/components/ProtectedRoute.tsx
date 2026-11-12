
import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";

import AdminAccountSettings from "./AdminAccountSettings";

type UserRole = "ADMIN" | "CLIENT";

interface ProtectedRouteProps {
  allowedRole: UserRole;
  children: ReactNode;
}

interface TokenPayload {
  role?: string;
  exp?: number;
  mfa?: boolean;
}

function readValidToken(
  token: string | null
): TokenPayload | null {

  if (!token) return null;

  try {

    const parts = token.split(".");

    if (parts.length !== 3) return null;

    const base64 = parts[1]
      .replace(/-/g, "+")
      .replace(/_/g, "/");

    const payload = JSON.parse(
      atob(
        base64.padEnd(
          Math.ceil(base64.length / 4) * 4,
          "="
        )
      )
    ) as TokenPayload;

    if (
      !payload.role ||
      typeof payload.exp !== "number" ||
      payload.exp * 1000 <= Date.now()
    ) {
      return null;
    }

    const role = payload.role
      .replace(/^ROLE_/, "")
      .toUpperCase();

    if (role !== "ADMIN" && role !== "CLIENT") {
      return null;
    }

    // An administrator must have completed email OTP verification.
    if (role === "ADMIN" && payload.mfa !== true) {
      return null;
    }

    return {
      ...payload,
      role,
    };

  } catch {
    return null;
  }
}

export default function ProtectedRoute({
  allowedRole,
  children,
}: ProtectedRouteProps) {

  const adminToken =
    localStorage.getItem("adminToken");

  const clientToken =
    localStorage.getItem("authToken");

  const adminPayload =
    readValidToken(adminToken);

  const clientPayload =
    readValidToken(clientToken);

  if (adminToken && !adminPayload) {
    localStorage.removeItem("adminToken");
  }

  if (clientToken && !clientPayload) {
    localStorage.removeItem("authToken");
  }

  const validAdmin =
    adminPayload?.role === "ADMIN";

  const validClient =
    clientPayload?.role === "CLIENT";

  if (allowedRole === "ADMIN" && validAdmin) {

    return (
      <>
        <AdminAccountSettings />
        {children}
      </>
    );
  }

  if (allowedRole === "CLIENT" && validClient) {
    return <>{children}</>;
  }

  if (validAdmin) {
    return (
      <Navigate
        to="/admin-dashboard"
        replace
      />
    );
  }

  if (validClient) {
    return (
      <Navigate
        to="/client-dashboard"
        replace
      />
    );
  }

  const hadInvalidToken =
    (adminToken !== null && !adminPayload) ||
    (clientToken !== null && !clientPayload);

  return (
    <Navigate
      to={
        hadInvalidToken
          ? "/login?sessionExpired=true"
          : "/login"
      }
      replace
    />
  );
}
