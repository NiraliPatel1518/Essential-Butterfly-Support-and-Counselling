
import { useEffect, useState } from "react";
import { apiUrl } from "../api/api";
import "./AdminSecurityModals.css";

interface Profile {
  fullName: string;
  email: string;
}

type Stage = "email" | "otp";

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

export default function AdminAccountSettings() {

  const [profile, setProfile] =
    useState<Profile | null>(null);

  const [loading, setLoading] = useState(true);

  const [modalOpen, setModalOpen] = useState(false);

  const [stage, setStage] = useState<Stage>("email");

  const [newEmail, setNewEmail] = useState("");

  const [code, setCode] = useState("");

  const [busy, setBusy] = useState(false);

  const [error, setError] = useState("");

  const [notice, setNotice] = useState("");

  const [resendCount, setResendCount] = useState(0);

  const [cooldown, setCooldown] = useState(0);

  const token = () =>
    localStorage.getItem("adminToken") || "";

  // ==========================================
  // LOAD CURRENT ADMIN PROFILE
  // ==========================================

  const loadProfile = async () => {

    setLoading(true);

    try {

      const response = await fetch(
        apiUrl("/api/admin-account/profile"),
        {
          headers: {
            Authorization: `Bearer ${token()}`,
          },
        }
      );

      if (!response.ok) {
        throw new Error(
          await readMessage(response)
        );
      }

      setProfile(
        (await response.json()) as Profile
      );

    } catch (requestError) {

      setError(
        requestError instanceof Error
          ? requestError.message
          : "Unable to load admin account."
      );

    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadProfile();
  }, []);

  // ==========================================
  // RESEND COUNTDOWN
  // ==========================================

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
  // OPEN EMAIL CHANGE POPUP
  // ==========================================

  const openModal = () => {

    if (stage === "email") {

      setNewEmail(profile?.email || "");
      setCode("");
      setResendCount(0);
      setCooldown(0);
    }

    setError("");
    setNotice("");

    setModalOpen(true);
  };

  // ==========================================
  // REQUEST EMAIL CHANGE OTP
  // ==========================================

  const requestCode = async (
    event: React.FormEvent<HTMLFormElement>
  ) => {

    event.preventDefault();

    setError("");
    setNotice("");

    const normalized =
      newEmail.trim().toLowerCase();

    if (
      !/^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(
        normalized
      )
    ) {

      setError("Enter a valid email address.");

      return;
    }

    setBusy(true);

    try {

      const response = await fetch(
        apiUrl("/api/admin-account/email-change/request"),
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${token()}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            email: normalized,
          }),
        }
      );

      if (!response.ok) {
        throw new Error(
          await readMessage(response)
        );
      }

      setNewEmail(normalized);

      setStage("otp");

      setCooldown(60);
      setResendCount(0);

      setNotice(
        `A verification code has been sent to ${normalized}.`
      );

    } catch (requestError) {

      setError(
        requestError instanceof Error
          ? requestError.message
          : "Unable to send verification code."
      );

    } finally {
      setBusy(false);
    }
  };

  // ==========================================
  // VERIFY NEW EMAIL
  // ==========================================

  const verifyCode = async (
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
        apiUrl("/api/admin-account/email-change/verify"),
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${token()}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            code,
          }),
        }
      );

      if (!response.ok) {
        throw new Error(
          await readMessage(response)
        );
      }

      const newJwt =
        (await response.text()).trim();

      if (newJwt.split(".").length !== 3) {

        throw new Error(
          "The server returned an invalid session token."
        );
      }

      /*
       * The backend has successfully updated the admin email.
       * Replace the old JWT with the newly issued JWT.
       */
      localStorage.setItem(
        "adminToken",
        newJwt
      );

      window.dispatchEvent(
        new Event("auth-changed")
      );

      setProfile((previous) =>
        previous
          ? {
              ...previous,
              email: newEmail,
            }
          : previous
      );

      setModalOpen(false);

      setNotice(
        "Your admin email and login email have been updated successfully."
      );

    } catch (requestError) {

      setError(
        requestError instanceof Error
          ? requestError.message
          : "Unable to verify the new email."
      );

    } finally {
      setBusy(false);
    }
  };

  // ==========================================
  // RESEND EMAIL CHANGE OTP
  // ==========================================

  const resendCode = async () => {

    setError("");
    setNotice("");

    setBusy(true);

    try {

      const response = await fetch(
        apiUrl("/api/admin-account/email-change/resend"),
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${token()}`,
          },
        }
      );

      if (!response.ok) {
        throw new Error(
          await readMessage(response)
        );
      }

      const next = resendCount + 1;

      setResendCount(next);

      setCooldown(
        next === 1 ? 90 : 120
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

  // ==========================================
  // UI
  // ==========================================

  return (
    <>

      <section
        className="admin-account-card"
        aria-labelledby="admin-account-title"
      >

        <div>

          <p className="dashboard-eyebrow">
            ADMINISTRATOR PROFILE
          </p>

          <h2 id="admin-account-title">
            Admin account
          </h2>

          <p className="admin-account-description">
            The email address used for administrator sign-in and security verification.
          </p>

        </div>

        <div className="admin-account-current">

          <span>Current login email</span>

          <strong>
            {loading
              ? "Loading..."
              : profile?.email || "Unavailable"}
          </strong>

          <button
            type="button"
            className="dashboard-secondary-button"
            onClick={openModal}
            disabled={loading || !profile}
          >
            Change email
          </button>

        </div>

        {error && !modalOpen && (
          <p
            className="admin-security-error"
            role="alert"
          >
            {error}
          </p>
        )}

        {notice && !modalOpen && (
          <p
            className="admin-security-success"
            role="status"
          >
            {notice}
          </p>
        )}

      </section>

      {modalOpen && (

        <div className="admin-security-backdrop">

          <section
            className="admin-security-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="admin-email-change-title"
          >

            <button
              type="button"
              className="admin-security-close"
              onClick={() => setModalOpen(false)}
              disabled={busy}
              aria-label="Close"
            >
              ×
            </button>

            <div
              className="admin-security-icon"
              aria-hidden="true"
            >
              ✉
            </div>

            <p className="admin-security-eyebrow">
              ADMINISTRATOR PROFILE
            </p>

            <h2 id="admin-email-change-title">
              {stage === "email"
                ? "Change admin email"
                : "Verify your new email"}
            </h2>

            <p className="admin-security-description">

              {stage === "email"
                ? "Your current email is shown below. Edit it to the new address you want to use. Your login email will not change until the new address is verified."
                : `Enter the 6-digit code sent to ${newEmail}. Your current login email remains unchanged until verification succeeds.`}

            </p>

            {stage === "email" ? (

              <form
                onSubmit={(event) =>
                  void requestCode(event)
                }
              >

                <label htmlFor="admin-new-email">
                  Email address
                </label>

                <input
                  id="admin-new-email"
                  type="email"
                  autoComplete="email"
                  value={newEmail}
                  onChange={(event) =>
                    setNewEmail(event.target.value)
                  }
                  maxLength={254}
                  required
                  disabled={busy}
                />

                {error && (
                  <p
                    className="admin-security-error"
                    role="alert"
                  >
                    {error}
                  </p>
                )}

                <button
                  type="submit"
                  className="admin-security-primary"
                  disabled={busy}
                >
                  {busy
                    ? "Sending code..."
                    : "Send verification code"}
                </button>

              </form>

            ) : (

              <form
                onSubmit={(event) =>
                  void verifyCode(event)
                }
              >

                <label htmlFor="admin-email-change-otp">
                  6-digit verification code
                </label>

                <input
                  id="admin-email-change-otp"
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
                  type="submit"
                  className="admin-security-primary"
                  disabled={busy}
                >
                  {busy
                    ? "Verifying..."
                    : "Verify and update email"}
                </button>

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
                      onClick={() => void resendCode()}
                      disabled={busy}
                    >
                      Resend code
                    </button>

                  )}

                  <button
                    type="button"
                    onClick={() => {
                      setStage("email");
                      setCode("");
                      setError("");
                      setNotice("");
                      setCooldown(0);
                    }}
                    disabled={busy}
                  >
                    Edit email
                  </button>

                </div>

              </form>

            )}

            <p className="admin-security-footnote">
              The code expires in 10 minutes. Five incorrect attempts are allowed, with up to three resend requests.
            </p>

          </section>

        </div>

      )}

    </>
  );
}
