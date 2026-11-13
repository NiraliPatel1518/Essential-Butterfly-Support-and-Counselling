
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import { apiUrl } from "../api/api";
import PhoneVerificationModal from "./PhoneVerificationModal";
import Intake from "../pages/Intake";

type PhoneVerificationStatus = {
  phoneVerified: boolean;
  phoneNumber: string | null;
  pendingPhoneNumber: string | null;
};

type AccessState =
  | "loading"
  | "unverified"
  | "verified"
  | "error";

export default function IntakeAccessGuard() {
  const navigate = useNavigate();

  const [accessState, setAccessState] =
    useState<AccessState>("loading");

  const [phoneStatus, setPhoneStatus] =
    useState<PhoneVerificationStatus | null>(null);

  const [error, setError] = useState("");

  useEffect(() => {
    const controller = new AbortController();

    const checkPhoneVerification = async () => {
      const token = localStorage.getItem("authToken");

      if (!token) {
        navigate("/login", { replace: true });
        return;
      }

      try {
        const response = await fetch(
          apiUrl("/api/phone-verification/status"),
          {
            method: "GET",
            headers: {
              Authorization: `Bearer ${token}`,
            },
            signal: controller.signal,
          }
        );

        if (response.status === 401 ||
            response.status === 403) {
          localStorage.removeItem("authToken");

          navigate("/login?sessionExpired=true", {
            replace: true,
          });

          return;
        }

        if (!response.ok) {
          throw new Error(
            "Unable to verify your phone verification status. Please try again."
          );
        }

        const data =
          (await response.json()) as PhoneVerificationStatus;

        if (controller.signal.aborted) return;

        setPhoneStatus(data);

        setAccessState(
          data.phoneVerified ? "verified" : "unverified"
        );

      } catch (requestError) {
        if (controller.signal.aborted) return;

        setError(
          requestError instanceof Error
            ? requestError.message
            : "Unable to check phone verification status."
        );

        setAccessState("error");
      }
    };

    void checkPhoneVerification();

    return () => controller.abort();
  }, [navigate]);

  if (accessState === "loading") {
    return (
      <main className="intake-page">
        <p role="status">
          Checking your phone verification status...
        </p>
      </main>
    );
  }

  if (accessState === "error") {
    return (
      <main className="intake-page">
        <div role="alert">
          <h1>Unable to access Intake</h1>
          <p>{error}</p>

          <button
            type="button"
            onClick={() => window.location.reload()}
          >
            Try again
          </button>
        </div>
      </main>
    );
  }

  if (accessState === "unverified") {
    return (
      <>
        <main className="intake-page">
          <div className="intake-shell">
            <h1>Phone verification required</h1>
            <p>
              Please verify your phone number to proceed.
            </p>
          </div>
        </main>

        <PhoneVerificationModal
          isOpen={true}
          phoneVerified={false}
          currentPhoneNumber={phoneStatus?.phoneNumber ?? null}
          pendingPhoneNumber={
            phoneStatus?.pendingPhoneNumber ?? null
          }
          requiredForIntake={true}
          onClose={() => {
            navigate("/client-dashboard", {
              replace: true,
            });
          }}
          onVerified={(verifiedNumber) => {
            setPhoneStatus({
              phoneVerified: true,
              phoneNumber: verifiedNumber,
              pendingPhoneNumber: null,
            });

            setAccessState("verified");
          }}
        />
      </>
    );
  }

  return <Intake />;
}
