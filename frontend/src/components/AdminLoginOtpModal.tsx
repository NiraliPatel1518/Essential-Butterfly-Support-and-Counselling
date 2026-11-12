
import { useEffect, useState } from "react";
import { apiUrl } from "../api/api";
import "./AdminSecurityModals.css";

interface Props {
  challengeToken: string;
  maskedEmail: string;
  onVerified: (jwt: string) => void;
  onCancel: () => void;
}

async function readMessage(response: Response): Promise<string> {

  const body = await response.text();

  if (!body) {
    return `Request failed (${response.status}).`;
  }

  try {

    const parsed = JSON.parse(body) as {
      message?: string;
      error?: string;
    };

    return parsed.message || parsed.error || body;

  } catch {
    return body;
  }
}

export default function AdminLoginOtpModal({
  challengeToken,
  maskedEmail,
  onVerified,
  onCancel,
}: Props) {

  const [code, setCode] = useState("");

  const [busy, setBusy] = useState(false);

  const [error, setError] = useState("");

  const [notice, setNotice] = useState("");

  const [resendCount, setResendCount] = useState(0);

  const [cooldown, setCooldown] = useState(60);

  useEffect(() => {

    if (cooldown <= 0) return;

    const timer = window.setTimeout(
      () => setCooldown(
        (value) => Math.max(0, value - 1)
      ),
      1000
    );

    return () => window.clearTimeout(timer);

  }, [cooldown]);

  // ==========================================
  // VERIFY LOGIN OTP
  // ==========================================

  const verify = async (
    event: React.FormEvent<HTMLFormElement>
  ) => {

    event.preventDefault();

    setError("");
    setNotice("");

    if (!/^\d{6}$/.test(code)) {

      setError(
        "Enter the 6-digit verification code."
      );

      return;
    }

    setBusy(true);

    try {

      const response = await fetch(
        apiUrl("/api/admin-auth/login-otp/verify"),
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            challengeToken,
            code,
          }),
        }
      );

      if (!response.ok) {
        throw new Error(
          await readMessage(response)
        );
      }

      const jwt = (await response.text()).trim();

      if (jwt.split(".").length !== 3) {

        throw new Error(
          "The server returned an invalid sign-in token. Please log in again."
        );
      }

      onVerified(jwt);

    } catch (requestError) {

      setError(
        requestError instanceof Error
          ? requestError.message
          : "Unable to verify the code."
      );

    } finally {
      setBusy(false);
    }
  };

  // ==========================================
  // RESEND LOGIN OTP
  // ==========================================

  const resend = async () => {

    setError("");
    setNotice("");

    setBusy(true);

    try {

      const response = await fetch(
        apiUrl("/api/admin-auth/login-otp/resend"),
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            challengeToken,
          }),
        }
      );

      if (!response.ok) {
        throw new Error(
          await readMessage(response)
        );
      }

      const nextCount = resendCount + 1;

      setResendCount(nextCount);

      setCooldown(
        nextCount === 1 ? 90 : 120
      );

      setNotice(
        "A new verification code has been sent."
      );

    } catch (requestError) {

      const message =
        requestError instanceof Error
          ? requestError.message
          : "Unable to resend the code.";

      setError(message);

      const match = message.match(
        /wait (\d+) second/i
      );

      if (match) {
        setCooldown(Number(match[1]));
      }

      if (
        message.toLowerCase().includes(
          "maximum number of resend"
        )
      ) {
        setResendCount(3);
      }

    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="admin-security-backdrop">

      <section
        className="admin-security-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="admin-login-otp-title"
      >

        <div
          className="admin-security-icon"
          aria-hidden="true"
        >
          ✉
        </div>

        <p className="admin-security-eyebrow">
          ADMINISTRATOR SECURITY
        </p>

        <h2 id="admin-login-otp-title">
          Verify it's you
        </h2>

        <p className="admin-security-description">
          We sent a 6-digit sign-in code to{" "}
          <strong>{maskedEmail}</strong>.
          Enter it below to access the admin portal.
        </p>

        <form
          onSubmit={(event) => void verify(event)}
        >

          <label htmlFor="admin-login-otp">
            6-digit verification code
          </label>

          <input
            id="admin-login-otp"
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
            maxLength={6}
            placeholder="Enter code"
            required
            disabled={busy}
            autoFocus
          />

          {error && (
            <p
              className="admin-security-error"
              role="alert"
            >
              {error}
            </p>
          )}

          {notice && (
            <p
              className="admin-security-success"
              role="status"
            >
              {notice}
            </p>
          )}

          <button
            className="admin-security-primary"
            type="submit"
            disabled={busy}
          >
            {busy
              ? "Verifying..."
              : "Verify and continue"}
          </button>

        </form>

        <div className="admin-security-resend">

          {resendCount >= 3 ? (

            <span>
              Maximum of 3 resend requests reached.
            </span>

          ) : cooldown > 0 ? (

            <span>
              Resend available in {cooldown}s
            </span>

          ) : (

            <button
              type="button"
              onClick={() => void resend()}
              disabled={busy}
            >
              Resend code
            </button>

          )}

        </div>

        <p className="admin-security-footnote">
          The code expires in 10 minutes. Never share it with anyone.
        </p>

        <button
          type="button"
          className="admin-security-cancel"
          onClick={onCancel}
          disabled={busy}
        >
          Cancel and return to login
        </button>

      </section>

    </div>
  );
}
