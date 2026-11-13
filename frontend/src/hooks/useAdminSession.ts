import { useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { ApiError } from "../api/contentApi";

/*
 * When the administrator's sign-in has expired, nothing may be saved or
 * published. This sends them to the login page with the usual "session
 * expired" notice. Typed text is kept separately (utils/draftStorage.ts).
 *
 * Returns true when the error was an expired session and has been handled.
 */
export function useAdminSession() {
  const navigate = useNavigate();

  return useCallback(
    (error: unknown): boolean => {
      if (error instanceof ApiError && error.status === 401) {
        localStorage.removeItem("adminToken");
        window.dispatchEvent(new Event("auth-changed"));
        navigate("/login?sessionExpired=true", { replace: true });
        return true;
      }

      return false;
    },
    [navigate]
  );
}
