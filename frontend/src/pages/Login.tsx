import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { apiUrl } from "../api/api";
import "./Login.css";

function Login() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const sessionExpired = searchParams.get("sessionExpired") === "true";

  const requestedReturnTo = searchParams.get("returnTo");

  const returnTo =
    requestedReturnTo &&
    requestedReturnTo.startsWith("/") &&
    !requestedReturnTo.startsWith("//")
      ? requestedReturnTo
      : null;

  const emailPattern =
    /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;

  const handleLogin = async () => {
    setError("");

    const normalizedEmail = email.trim().toLowerCase();

    if (!emailPattern.test(normalizedEmail)) {
      setError(
        "Please enter a valid email address such as name@example.com."
      );
      return;
    }

    if (!password) {
      setError("Please enter your password.");
      return;
    }

    setLoading(true);

    try {
      const response = await fetch(apiUrl("/api/auth/login"), {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          email: normalizedEmail,
          password,
        }),
      });

      const responseText = await response.text();

      if (!response.ok) {
        let errorMessage = responseText;

        try {
          const errorData = JSON.parse(responseText) as {
            message?: string;
          };

          errorMessage = errorData.message || responseText;
        } catch {
          // The backend may return a plain-text error.
        }

        setError(
          errorMessage || "Unable to sign in. Please check your credentials."
        );
        return;
      }

      const token = responseText.trim();
      const tokenParts = token.split(".");

      if (tokenParts.length !== 3) {
        setError("Unable to complete sign-in. Please try again.");
        return;
      }

      let payload: { role?: string };

      try {
        const base64Payload = tokenParts[1]
          .replace(/-/g, "+")
          .replace(/_/g, "/");

        const paddedPayload = base64Payload.padEnd(
          Math.ceil(base64Payload.length / 4) * 4,
          "="
        );

        payload = JSON.parse(atob(paddedPayload)) as { role?: string };
      } catch {
        setError("Unable to verify your account. Please try again.");
        return;
      }

      const role = payload.role?.toUpperCase();

      if (role !== "CLIENT" && role !== "ADMIN") {
        setError("Unable to verify your account. Please contact support.");
        return;
      }

      // Clear previous login state to avoid retaining another account's token.
      localStorage.removeItem("authToken");
      localStorage.removeItem("adminToken");

      if (role === "ADMIN") {
        localStorage.setItem("adminToken", token);
      } else {
        localStorage.setItem("authToken", token);
      }

      window.dispatchEvent(new Event("auth-changed"));

      navigate(
        returnTo ||
          (role === "ADMIN" ? "/admin-dashboard" : "/client-dashboard"),
        { replace: true }
      );
    } catch (requestError) {
      console.error("Login request failed:", requestError);

      setError(
        "Unable to connect to the server. Please make sure the backend is running."
      );
    } finally {
      setLoading(false);
    }
  };

  const signupPath = returnTo
    ? `/signup?returnTo=${encodeURIComponent(returnTo)}`
    : "/signup";

  return (
    <main className="login-page">
      <section className="login-section">
        <div className="login-shape login-shape-left" />
        <div className="login-shape login-shape-right" />
        <div className="login-shape login-shape-bottom" />

        <div className="login-flower login-flower-left">
          <img src="/images/hero-flower.jpg" alt="" aria-hidden="true" />
        </div>

        <div className="login-flower login-flower-right">
          <img src="/images/hero-flower.jpg" alt="" aria-hidden="true" />
        </div>

        <div className="login-container">
          <div className="login-introduction">
            <div className="login-eyebrow">WELCOME BACK</div>

            <h1>
              Support starts with
              <br />
              feeling understood.
            </h1>

            <p className="login-intro-text">
              Sign in to access your client dashboard, saved resources and
              support requests.
            </p>

            <div className="login-support-card">
              <div className="login-support-icon" aria-hidden="true">
                <span />
                <span />
              </div>

              <div>
                <strong>Your support, your page.</strong>

                <p>
                  Practical tools, reflective resources and gentle guidance
                  to help you move forward.
                </p>
              </div>
            </div>
          </div>

          <div className="login-card">
            <div className="login-card-header">
              <h2>Client Login</h2>

              <p>Enter your details to access your account.</p>
            </div>

            <form
              className="login-form"
              onSubmit={(event) => {
                event.preventDefault();
                void handleLogin();
              }}
            >
              {sessionExpired && (
                <p role="status" className="login-session-expired">
                  Your session has expired. Please log in again.
                </p>
              )}

              {returnTo && (
                <p role="status" className="login-session-expired">
                  Please log in to read the full update.
                </p>
              )}

              <div className="login-field">
                <label htmlFor="login-email">Email address</label>

                <div className="login-input-wrapper">
                  <span className="login-input-icon" aria-hidden="true">
                    ✉
                  </span>

                  <input
                    id="login-email"
                    name="email"
                    type="email"
                    placeholder="you@example.com"
                    autoComplete="username"
                    pattern="[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"
                    title="Please enter an email address such as name@example.com"
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    required
                  />
                </div>
              </div>

              <div className="login-field">
                <label htmlFor="login-password">Password</label>

                <div className="login-input-wrapper">
                  <span className="login-input-icon" aria-hidden="true">
                    🔒
                  </span>

                  <input
                    id="login-password"
                    name="password"
                    type={showPassword ? "text" : "password"}
                    placeholder="Enter your password"
                    autoComplete="current-password"
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    required
                  />

                  <button
                    type="button"
                    className="password-toggle"
                    aria-label={showPassword ? "Hide password" : "Show password"}
                    aria-pressed={showPassword}
                    onClick={() => setShowPassword((previous) => !previous)}
                  >
                    ◉
                  </button>
                </div>
              </div>

              <div className="forgot-password-row">
                <Link to="/forgot-password">Forgot password?</Link>
              </div>

              {error && (
                <p role="alert" className="login-error">
                  {error}
                </p>
              )}

              <button
                type="submit"
                className="login-submit"
                disabled={loading}
              >
                {loading ? "Logging in..." : "Log In"}
              </button>

              <div className="login-divider">
                <span />
                <strong>OR</strong>
                <span />
              </div>

              <Link to="/" className="visitor-button">
                <span className="visitor-icon" aria-hidden="true">
                  ♙
                </span>

                <span>Continue as a visitor</span>
              </Link>

              <div className="login-butterfly-divider">
                <span />
                <div aria-hidden="true">♡</div>
                <span />
              </div>

              <p className="login-signup">
                New here? <Link to={signupPath}>Create an account</Link>
              </p>
            </form>
          </div>
        </div>
      </section>
    </main>
  );
}

export default Login;