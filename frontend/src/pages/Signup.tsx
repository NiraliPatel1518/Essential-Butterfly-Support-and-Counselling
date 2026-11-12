import { useEffect, useRef, useState } from "react";
import type { FormEvent, KeyboardEvent as ReactKeyboardEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { apiUrl } from "../api/api";
import "./Signup.css";
import "./EmailVerificationModal.css";

function Signup() {
  const [searchParams] = useSearchParams();

  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState(searchParams.get("email") || "");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const [verificationStep, setVerificationStep] = useState(
    searchParams.get("verify") === "true"
  );

  const [verificationCode, setVerificationCode] = useState("");
  const [verificationLoading, setVerificationLoading] = useState(false);
  const [verificationError, setVerificationError] = useState("");
  const [verificationSuccess, setVerificationSuccess] = useState("");
  const [verificationFailed, setVerificationFailed] = useState(false);

  const [resendCount, setResendCount] = useState(0);
  const [countdown, setCountdown] = useState(
    searchParams.get("verify") === "true" ? 0 : 60
  );

  const verificationInputRef = useRef<HTMLInputElement>(null);
  const modalRef = useRef<HTMLDivElement>(null);

  const navigate = useNavigate();

  const requestedReturnTo = searchParams.get("returnTo");

  const returnTo =
    requestedReturnTo &&
    requestedReturnTo.startsWith("/") &&
    !requestedReturnTo.startsWith("//")
      ? requestedReturnTo
      : null;

  const loginPath = returnTo
    ? `/login?returnTo=${encodeURIComponent(returnTo)}`
    : "/login";

  const getResponseMessage = async (
    response: Response,
    fallback: string
  ): Promise<string> => {
    const responseText = await response.text();

    if (!responseText) {
      return fallback;
    }

    try {
      const parsed = JSON.parse(responseText) as {
        message?: string;
        error?: string;
      };

      return parsed.message || parsed.error || responseText;
    } catch {
      return responseText;
    }
  };

  /*
   * Countdown is a UI convenience only.
   * The backend independently enforces every cooldown.
   */
  useEffect(() => {
    if (!verificationStep || verificationFailed || countdown <= 0) {
      return;
    }

    const timer = window.setTimeout(() => {
      setCountdown((current) => Math.max(0, current - 1));
    }, 1000);

    return () => window.clearTimeout(timer);
  }, [verificationStep, verificationFailed, countdown]);

  /*
   * Lock background scrolling and keep keyboard focus inside the modal.
   * Escape and backdrop clicks do not dismiss this verification modal.
   */
  useEffect(() => {
    if (!verificationStep) {
      return;
    }

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    window.setTimeout(() => {
      verificationInputRef.current?.focus();
    }, 0);

    const handleKeyDown = (event: globalThis.KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        event.stopPropagation();
        return;
      }

      if (event.key !== "Tab" || !modalRef.current) {
        return;
      }

      const focusable = modalRef.current.querySelectorAll<HTMLElement>(
        'button:not([disabled]), input:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])'
      );

      if (focusable.length === 0) {
        event.preventDefault();
        return;
      }

      const first = focusable[0];
      const last = focusable[focusable.length - 1];

      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (
        !event.shiftKey &&
        document.activeElement === last
      ) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener("keydown", handleKeyDown, true);

    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener("keydown", handleKeyDown, true);
    };
  }, [verificationStep, verificationFailed]);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    setError("");

    const emailPattern =
      /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;

    if (!emailPattern.test(email.trim())) {
      setError(
        "Please enter a valid email address such as name@example.com."
      );
      return;
    }

    if (password !== confirmPassword) {
      setError("Passwords do not match.");
      return;
    }

    setLoading(true);

    try {
      const response = await fetch(apiUrl("/api/auth/signup"), {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          fullName,
          email: email.trim().toLowerCase(),
          password,
          confirmPassword,
        }),
      });

      if (!response.ok) {
        const message = await getResponseMessage(
          response,
          "Unable to create your account."
        );

        setError(message);
        return;
      }

      setEmail(email.trim().toLowerCase());
      setVerificationCode("");
      setVerificationError("");
      setVerificationSuccess("");
      setVerificationFailed(false);
      setResendCount(0);
      setCountdown(60);
      setVerificationStep(true);

    } catch (requestError) {
      console.error(requestError);

      setError(
        "Unable to connect to the server. Please make sure the backend is running."
      );
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyEmail = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    setVerificationError("");
    setVerificationSuccess("");

    if (!/^[0-9]{6}$/.test(verificationCode)) {
      setVerificationError("Please enter the 6-digit verification code.");
      return;
    }

    setVerificationLoading(true);

    try {
      const response = await fetch(apiUrl("/api/auth/verify-email"), {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          email: email.trim().toLowerCase(),
          code: verificationCode,
        }),
      });

      if (!response.ok) {
        const message = await getResponseMessage(
          response,
          "Unable to verify your email."
        );

        setVerificationError(message);

        if (
          message.toLowerCase().includes("registration has been removed") ||
          message.toLowerCase().includes("five incorrect attempts")
        ) {
          setVerificationFailed(true);
        }

        return;
      }

      setVerificationSuccess(
        "Your email has been verified. Redirecting you to login..."
      );

      window.setTimeout(() => {
        navigate(loginPath, { replace: true });
      }, 1200);

    } catch (requestError) {
      console.error(requestError);

      setVerificationError(
        "Unable to connect to the server. Please try again."
      );
    } finally {
      setVerificationLoading(false);
    }
  };

  const handleResendCode = async () => {
    if (verificationLoading || countdown > 0 || resendCount >= 3) {
      return;
    }

    setVerificationError("");
    setVerificationSuccess("");
    setVerificationLoading(true);

    try {
      const response = await fetch(
        apiUrl("/api/auth/resend-verification"),
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            email: email.trim().toLowerCase(),
          }),
        }
      );

      if (!response.ok) {
        const message = await getResponseMessage(
          response,
          "Unable to resend the verification code."
        );

        setVerificationError(message);

        if (
          message.toLowerCase().includes("maximum number") ||
          message.toLowerCase().includes("try signing up again")
        ) {
          setVerificationFailed(true);
        }

        return;
      }

      const nextResendCount = resendCount + 1;

      setResendCount(nextResendCount);
      setVerificationCode("");
      setCountdown(
        nextResendCount === 1
          ? 90
          : nextResendCount === 2
            ? 120
            : 0
      );

      setVerificationSuccess(
        "A new verification code has been sent to your email."
      );

    } catch (requestError) {
      console.error(requestError);

      setVerificationError(
        "Unable to connect to the server. Please try again."
      );
    } finally {
      setVerificationLoading(false);
    }
  };

  const handleTryAgain = () => {
    setVerificationStep(false);
    setVerificationFailed(false);
    setVerificationCode("");
    setVerificationError("");
    setVerificationSuccess("");

    setFullName("");
    setEmail("");
    setPassword("");
    setConfirmPassword("");

    setResendCount(0);
    setCountdown(60);
    setError("");

    window.setTimeout(() => {
      document.getElementById("signup-full-name")?.focus();
    }, 0);
  };

  const handleModalSubmitKeyDown = (
    event: ReactKeyboardEvent<HTMLFormElement>
  ) => {
    if (event.key === "Escape") {
      event.preventDefault();
    }
  };

  return (
    <main className="signup-page">
      <div className="signup-bg-shape signup-bg-shape-left" />
      <div className="signup-bg-shape signup-bg-shape-right" />
      <div className="signup-bg-wave" />

      <img
        src="/images/img_one.png"
        alt=""
        aria-hidden="true"
        className="signup-flower signup-flower-left"
      />

      <img
        src="/images/img_two.png"
        alt=""
        aria-hidden="true"
        className="signup-flower signup-flower-right"
      />

      <div className="signup-container">
        <section className="signup-intro">
          <div className="signup-eyebrow">
            GET STARTED
          </div>

          <h1>
            Create your
            <br />
            support space.
          </h1>

          <p className="signup-intro-text">
            Set up an account to keep track of your
            conversations, resources and next steps.
          </p>

          <div className="signup-support-box">
            <div className="signup-support-icon">
              <span />
            </div>

            <p>
              An account gives you access to
              your personalized page, helpful
              tools and resources, and a space
              to continue your journey.
            </p>
          </div>
        </section>

        <section className="signup-card">
          <div className="signup-card-header">
            <h2>Create an Account</h2>

            <p>
              Join to access your personalized support space.
            </p>
          </div>

          <form
            className="signup-form"
            onSubmit={handleSubmit}
          >
            <div className="signup-field">
              <label htmlFor="signup-full-name">
                Full name
              </label>

              <div className="signup-input-wrapper">
                <span
                  className="signup-input-icon"
                  aria-hidden="true"
                >
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.8"
                    aria-hidden="true"
                  >
                    <circle cx="12" cy="8" r="3.5" />
                    <path d="M5 20c.7-3.5 3.1-5.5 7-5.5s6.3 2 7 5.5" />
                  </svg>
                </span>

                <input
                  id="signup-full-name"
                  name="fullName"
                  type="text"
                  placeholder="Your full name"
                  autoComplete="name"
                  value={fullName}
                  onChange={(event) => setFullName(event.target.value)}
                  required
                />
              </div>
            </div>

            <div className="signup-field">
              <label htmlFor="signup-email">
                Email address
              </label>

              <div className="signup-input-wrapper">
                <span
                  className="signup-input-icon"
                  aria-hidden="true"
                >
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.8"
                    aria-hidden="true"
                  >
                    <rect
                      x="3"
                      y="5"
                      width="18"
                      height="14"
                      rx="2"
                    />
                    <path d="m4 7 8 6 8-6" />
                  </svg>
                </span>

                <input
                  id="signup-email"
                  name="email"
                  type="email"
                  placeholder="you@example.com"
                  autoComplete="email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  pattern="[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"
                  title="Please enter an email address such as name@example.com"
                  required
                />
              </div>
            </div>

            <div className="signup-field">
              <label htmlFor="signup-password">
                Create a password
              </label>

              <div className="signup-input-wrapper">
                <span
                  className="signup-input-icon"
                  aria-hidden="true"
                >
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.8"
                    aria-hidden="true"
                  >
                    <rect
                      x="5"
                      y="10"
                      width="14"
                      height="10"
                      rx="2"
                    />
                    <path d="M8 10V7a4 4 0 0 1 8 0v3" />
                    <circle cx="12" cy="15" r="1" />
                  </svg>
                </span>

                <input
                  id="signup-password"
                  name="password"
                  type={showPassword ? "text" : "password"}
                  placeholder="Create a password"
                  autoComplete="new-password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  required
                />

                <button
                  type="button"
                  className="signup-password-toggle"
                  onClick={() => setShowPassword((current) => !current)}
                  aria-label={showPassword ? "Hide password" : "Show password"}
                  aria-pressed={showPassword}
                >
                  {showPassword ? "Hide" : "Show"}
                </button>
              </div>
            </div>

            <div className="signup-field">
              <label htmlFor="signup-confirm-password">
                Confirm password
              </label>

              <div className="signup-input-wrapper">
                <span
                  className="signup-input-icon"
                  aria-hidden="true"
                >
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.8"
                    aria-hidden="true"
                  >
                    <rect
                      x="5"
                      y="10"
                      width="14"
                      height="10"
                      rx="2"
                    />
                    <path d="M8 10V7a4 4 0 0 1 8 0v3" />
                    <circle cx="12" cy="15" r="1" />
                  </svg>
                </span>

                <input
                  id="signup-confirm-password"
                  name="confirmPassword"
                  type={showConfirmPassword ? "text" : "password"}
                  placeholder="Confirm your password"
                  autoComplete="new-password"
                  value={confirmPassword}
                  onChange={(event) => setConfirmPassword(event.target.value)}
                  required
                />

                <button
                  type="button"
                  className="signup-password-toggle"
                  onClick={() =>
                    setShowConfirmPassword((current) => !current)
                  }
                  aria-label={
                    showConfirmPassword ? "Hide password" : "Show password"
                  }
                  aria-pressed={showConfirmPassword}
                >
                  {showConfirmPassword ? "Hide" : "Show"}
                </button>
              </div>
            </div>

            <label className="signup-terms">
              <input
                type="checkbox"
                name="terms"
                required
              />

              <span className="signup-checkbox" />

              <span className="signup-terms-text">
                I agree to the{" "}
                <a href="#privacy">
                  Privacy Policy
                </a>{" "}
                and{" "}
                <a href="#terms">
                  Terms of Use
                </a>
              </span>
            </label>

            {error && (
              <p
                role="alert"
                style={{
                  color: "#b42318",
                  margin: "0 0 12px",
                }}
              >
                {error}
              </p>
            )}

            <button
              type="submit"
              className="signup-submit"
              disabled={loading}
            >
              {loading ? "Creating Account..." : "Create Account"}
            </button>
          </form>

          <div className="signup-divider">
            <span />

            <div className="signup-divider-butterfly">
              <svg
                viewBox="0 0 40 40"
                fill="none"
                aria-hidden="true"
              >
                <path
                  d="M19.5 19.5C14 8 5 7 5 13.5c0 5.5 5.5 8 11.5 8"
                  stroke="currentColor"
                  strokeWidth="1.7"
                  strokeLinecap="round"
                />

                <path
                  d="M20.5 19.5C26 8 35 7 35 13.5c0 5.5-5.5 8-11.5 8"
                  stroke="currentColor"
                  strokeWidth="1.7"
                  strokeLinecap="round"
                />

                <path
                  d="M19.5 20.5C14 32 5 33 5 26.5c0-5.5 5.5-8 11.5-8"
                  stroke="currentColor"
                  strokeWidth="1.7"
                  strokeLinecap="round"
                />

                <path
                  d="M20.5 20.5C26 32 35 33 35 26.5c0-5.5-5-8-11.5-8"
                  stroke="currentColor"
                  strokeWidth="1.7"
                  strokeLinecap="round"
                />

                <path
                  d="M20 10v20"
                  stroke="currentColor"
                  strokeWidth="1.7"
                  strokeLinecap="round"
                />
              </svg>
            </div>

            <span />
          </div>

          <p className="signup-login-prompt">
            Already have an account?{" "}
            <Link to={loginPath}>
              Log in
            </Link>
          </p>
        </section>
      </div>

      {verificationStep && (
        <div className="verification-modal-backdrop">
          <div
            ref={modalRef}
            className="verification-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="verification-modal-title"
            aria-describedby="verification-modal-description"
            tabIndex={-1}
          >
            {!verificationFailed ? (
              <>
                <div className="verification-modal-icon" aria-hidden="true">
                  <svg viewBox="0 0 48 48" fill="none">
                    <rect
                      x="7"
                      y="12"
                      width="34"
                      height="25"
                      rx="5"
                      stroke="currentColor"
                      strokeWidth="2"
                    />
                    <path
                      d="m9 15 15 12 15-12"
                      stroke="currentColor"
                      strokeWidth="2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />
                    <path
                      d="m18 33 4 4 9-10"
                      stroke="currentColor"
                      strokeWidth="2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />
                  </svg>
                </div>

                <h2 id="verification-modal-title">
                  Verify your email
                </h2>

                <p
                  id="verification-modal-description"
                  className="verification-modal-description"
                >
                  We sent a 6-digit verification code to
                  <strong> {email}</strong>. Enter the code below to activate
                  your account. The code expires in 10 minutes.
                </p>

                <form
                  className="verification-modal-form"
                  onSubmit={handleVerifyEmail}
                  onKeyDown={handleModalSubmitKeyDown}
                >
                  <label htmlFor="verification-modal-code">
                    Verification code
                  </label>

                  <input
                    ref={verificationInputRef}
                    id="verification-modal-code"
                    name="verificationCode"
                    type="text"
                    inputMode="numeric"
                    autoComplete="one-time-code"
                    pattern="[0-9]{6}"
                    maxLength={6}
                    placeholder="000000"
                    value={verificationCode}
                    onChange={(event) =>
                      setVerificationCode(
                        event.target.value.replace(/[^0-9]/g, "").slice(0, 6)
                      )
                    }
                    required
                  />

                  <p className="verification-modal-help">
                    Check your inbox and spam folder.
                  </p>

                  {verificationError && (
                    <p className="verification-modal-error" role="alert">
                      {verificationError}
                    </p>
                  )}

                  {verificationSuccess && (
                    <p className="verification-modal-success" role="status">
                      {verificationSuccess}
                    </p>
                  )}

                  <button
                    type="submit"
                    className="verification-modal-submit"
                    disabled={verificationLoading}
                  >
                    {verificationLoading ? "Verifying..." : "Verify Email"}
                  </button>
                </form>

                <div className="verification-modal-resend">
                  {resendCount >= 3 ? (
                    <p>
                      You have used all 3 resend requests.
                    </p>
                  ) : countdown > 0 ? (
                    <p>
                      Resend code available in{" "}
                      <strong>{countdown}s</strong>
                    </p>
                  ) : (
                    <button
                      type="button"
                      onClick={() => void handleResendCode()}
                      disabled={verificationLoading}
                    >
                      {verificationLoading
                        ? "Sending..."
                        : "Resend verification code"}
                    </button>
                  )}
                </div>
              </>
            ) : (
              <div className="verification-modal-failed">
                <div className="verification-modal-failed-icon" aria-hidden="true">
                  !
                </div>

                <h2 id="verification-modal-title">
                  Verification failed
                </h2>

                <p id="verification-modal-description">
                  {verificationError ||
                    "Your registration could not be verified. Please try again."}
                </p>

                <button
                  type="button"
                  className="verification-modal-submit"
                  onClick={handleTryAgain}
                >
                  Try Again
                </button>
              </div>
            )}
          </div>
        </div>
      )}
    </main>
  );
}

export default Signup;