import { useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";

const INACTIVITY_LIMIT = 30 * 60 * 1000;

function SessionTimeout() {
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    const isAdminSession = Boolean(localStorage.getItem("adminToken"));
    const isClientSession = Boolean(localStorage.getItem("authToken"));

    if (!isAdminSession && !isClientSession) {
      return;
    }

    const tokenKey = isAdminSession ? "adminToken" : "authToken";
    const loginPath = isAdminSession
      ? "/admin-login?sessionExpired=true"
      : "/login?sessionExpired=true";

    let timeoutId: number;

    const logout = () => {
      localStorage.removeItem(tokenKey);
      navigate(loginPath, { replace: true });
    };

    const resetTimer = () => {
      window.clearTimeout(timeoutId);

      timeoutId = window.setTimeout(() => {
        logout();
      }, INACTIVITY_LIMIT);
    };

    const activityEvents = [
      "mousedown",
      "mousemove",
      "keydown",
      "scroll",
      "touchstart",
      "click",
    ];

    activityEvents.forEach((event) => {
      window.addEventListener(event, resetTimer);
    });

    resetTimer();

    return () => {
      window.clearTimeout(timeoutId);

      activityEvents.forEach((event) => {
        window.removeEventListener(event, resetTimer);
      });
    };
  }, [location.pathname, navigate]);

  return null;
}

export default SessionTimeout;