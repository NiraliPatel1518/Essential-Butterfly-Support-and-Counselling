
import { useEffect, useState } from "react";
import { apiUrl } from "../api/api";
import "./PhoneVerificationModal.css";

interface PhoneVerificationModalProps {
  isOpen: boolean;
  phoneVerified: boolean;
  currentPhoneNumber: string | null;
  pendingPhoneNumber: string | null;
  requiredForIntake?: boolean;
  onClose: () => void;
  onVerified: (phoneNumber: string) => void;
}

type VerificationStage = "phone" | "code";

function responseMessage(text: string, fallback: string): string {
  if (!text) return fallback;

  try {
    const parsed = JSON.parse(text) as {
      message?: string;
      error?: string;
    };

    return parsed.message || parsed.error || text;
  } catch {
    return text;
  }
}

export default function PhoneVerificationModal({
  isOpen,
  phoneVerified,
  currentPhoneNumber,
  pendingPhoneNumber,
  requiredForIntake = false,
  onClose,
  onVerified,
}: PhoneVerificationModalProps) {

  const [phoneNumber, setPhoneNumber] = useState("");
  const [code, setCode] = useState("");

  const [stage, setStage] =
    useState<VerificationStage>("phone");

  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const [busy, setBusy] = useState(false);
  const [resendCount, setResendCount] = useState(0);
  const [cooldown, setCooldown] = useState(0);

  useEffect(() => {
    if (!isOpen) return;

    setPhoneNumber(
      pendingPhoneNumber || currentPhoneNumber || ""
    );

    setCode("");

    setStage(pendingPhoneNumber ? "code" : "phone");

    setMessage(
      pendingPhoneNumber
        ? "Continue with the verification code sent to your pending phone number."
        : ""
    );

    setError("");
    setResendCount(0);
    setCooldown(0);

  }, [isOpen, currentPhoneNumber, pendingPhoneNumber]);

  useEffect(() => {
    if (!isOpen || cooldown <= 0) return;

    const timer = window.setTimeout(() => {
      setCooldown((seconds) => Math.max(0, seconds - 1));
    }, 1000);

    return () => window.clearTimeout(timer);

  }, [isOpen, cooldown]);

  if (!isOpen) return null;

  // STEP 1: Send verification code.
  const handleSendCode = async (
    event: React.FormEvent<HTMLFormElement>
  ) => {

    event.preventDefault();

    setError("");
    setMessage("");

    const normalizedPhone =
      phoneNumber.trim().replace(/[\s()-]/g, "");

    if (!/^\+[1-9]\d{7,14}$/.test(normalizedPhone)) {

      setError(
        "Enter a valid phone number in international format, including the country code. Example: +14165551234."
      );

      return;
    }

    setBusy(true);

    try {

      const token = localStorage.getItem("authToken");

      const response = await fetch(
        apiUrl("/api/phone-verification/send"),
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${token || ""}`,
          },
          body: JSON.stringify({
            phoneNumber: normalizedPhone,
          }),
        }
      );

      const text = await response.text();

      if (!response.ok) {
        throw new Error(
          responseMessage(
            text,
            "Unable to send the verification code."
          )
        );
      }

      setPhoneNumber(normalizedPhone);
      setStage("code");
      setCooldown(60);
      setResendCount(0);

      setMessage(
        "A 6-digit verification code has been sent to your phone."
      );

    } catch (requestError) {

      setError(
        requestError instanceof Error
          ? requestError.message
          : "Unable to send the verification code."
      );

    } finally {
      setBusy(false);
    }
  };

  // STEP 2: Verify OTP.
  const handleVerifyCode = async (
    event: React.FormEvent<HTMLFormElement>
  ) => {

    event.preventDefault();

    setError("");
    setMessage("");

    if (!/^\d{6}$/.test(code.trim())) {

      setError(
        "Enter the 6-digit verification code."
      );

      return;
    }

    setBusy(true);

    try {

      const token = localStorage.getItem("authToken");

      const response = await fetch(
        apiUrl("/api/phone-verification/verify"),
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${token || ""}`,
          },
          body: JSON.stringify({
            code: code.trim(),
          }),
        }
      );

      const text = await response.text();

      if (!response.ok) {
        throw new Error(
          responseMessage(
            text,
            "Unable to verify your phone number."
          )
        );
      }

      setMessage(
        "Your phone number has been verified successfully."
      );

      onVerified(phoneNumber);

    } catch (requestError) {

      setError(
        requestError instanceof Error
          ? requestError.message
          : "Unable to verify your phone number."
      );

    } finally {
      setBusy(false);
    }
  };

  // STEP 3: Resend OTP.
  const handleResend = async () => {

    setError("");
    setMessage("");
    setBusy(true);

    try {

      const token = localStorage.getItem("authToken");

      const response = await fetch(
        apiUrl("/api/phone-verification/resend"),
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${token || ""}`,
          },
        }
      );

      const text = await response.text();

      if (!response.ok) {
        throw new Error(
          responseMessage(
            text,
            "Unable to resend the verification code."
          )
        );
      }

      const nextResendCount = resendCount + 1;

      setResendCount(nextResendCount);

      setCooldown(
        nextResendCount === 1 ? 90 : 120
      );

      setMessage(
        "A new verification code has been sent."
      );

    } catch (requestError) {

      const errorMessage =
        requestError instanceof Error
          ? requestError.message
          : "Unable to resend the verification code.";

      const waitMatch =
        errorMessage.match(/wait (\d+) second/);

      if (waitMatch) {
        setCooldown(Number(waitMatch[1]));
      }

      if (
        errorMessage
          .toLowerCase()
          .includes("maximum number of resend")
      ) {
        setResendCount(3);
      }

      setError(errorMessage);

    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="phone-verification-backdrop">

      <section
        className="phone-verification-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="phone-verification-title"
        aria-describedby="phone-verification-description"
      >

        <button
          type="button"
          className="phone-verification-close"
          onClick={onClose}
          aria-label="Close phone verification"
          disabled={busy}
        >
          ×
        </button>

        <div
          className="phone-verification-symbol"
          aria-hidden="true"
        >
          {phoneVerified ? "✓" : "✉"}
        </div>

        <p className="phone-verification-eyebrow">
          ESSENTIAL BUTTERFLY CLIENT PORTAL
        </p>

        <h2 id="phone-verification-title">

          {requiredForIntake && !phoneVerified
            ? "Verify your phone number to proceed"
            : phoneVerified
              ? "Update your phone number"
              : "Verify your phone number"}

        </h2>

        <p
          id="phone-verification-description"
          className="phone-verification-description"
        >

          {phoneVerified
            ? "Enter the new number you want associated with your account. Your current verified number will remain active unless the new number is successfully verified."
            : "Add your phone number so we can confirm it belongs to you. We will send a 6-digit verification code by text message."}

        </p>

        {stage === "phone" ? (

          <form onSubmit={handleSendCode}>

            <label htmlFor="phone-verification-number">
              Phone number
            </label>

            <input
              id="phone-verification-number"
              type="tel"
              autoComplete="tel"
              inputMode="tel"
              value={phoneNumber}
              onChange={(event) =>
                setPhoneNumber(event.target.value)
              }
              placeholder="+14165551234"
              maxLength={16}
              required
              disabled={busy}
            />

            <p className="phone-verification-hint">
              Include the country code. For example, +1 for Canada or the United States.
            </p>

            {error && (
              <p
                className="phone-verification-error"
                role="alert"
              >
                {error}
              </p>
            )}

            {message && (
              <p
                className="phone-verification-success"
                role="status"
              >
                {message}
              </p>
            )}

            <button
              type="submit"
              className="phone-verification-primary"
              disabled={busy}
            >
              {busy
                ? "Sending code..."
                : "Send verification code"}
            </button>

          </form>

        ) : (

          <form onSubmit={handleVerifyCode}>

            <p className="phone-verification-destination">
              Code sent to <strong>{phoneNumber}</strong>
            </p>

            <label htmlFor="phone-verification-code">
              6-digit verification code
            </label>

            <input
              id="phone-verification-code"
              type="text"
              inputMode="numeric"
              autoComplete="one-time-code"
              value={code}
              onChange={(event) =>
                setCode(
                  event.target.value
                    .replace(/\D/g, "")
                    .slice(0, 6)
                )
              }
              placeholder="Enter code"
              maxLength={6}
              pattern="[0-9]{6}"
              required
              disabled={busy}
            />

            {error && (
              <p
                className="phone-verification-error"
                role="alert"
              >
                {error}
              </p>
            )}

            {message && (
              <p
                className="phone-verification-success"
                role="status"
              >
                {message}
              </p>
            )}

            <button
              type="submit"
              className="phone-verification-primary"
              disabled={busy}
            >
              {busy
                ? "Verifying..."
                : "Verify phone number"}
            </button>

            <div className="phone-verification-resend">

              {resendCount >= 3 ? (

                <span>
                  You have reached the maximum of 3 resend requests.
                </span>

              ) : cooldown > 0 ? (

                <span>
                  Resend code in {cooldown}s
                </span>

              ) : (

                <button
                  type="button"
                  onClick={() => void handleResend()}
                  disabled={busy}
                >
                  Resend code
                </button>

              )}

              <button
                type="button"
                className="phone-verification-change"
                onClick={() => {
                  setStage("phone");
                  setCode("");
                  setError("");
                  setMessage("");
                  setCooldown(0);
                }}
                disabled={busy}
              >
                Change number
              </button>

            </div>

          </form>

        )}

        <p className="phone-verification-footnote">
          Your verification code expires after 10 minutes. Never share it with anyone.
        </p>

      </section>

    </div>
  );
}
