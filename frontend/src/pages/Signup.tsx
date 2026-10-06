import { useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import "./Signup.css";

function Signup() {
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [loading, setLoading] = useState(false);

  const navigate = useNavigate();

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    setError("");
    setSuccess("");

    if (password !== confirmPassword) {
      setError("Passwords do not match.");
      return;
    }

    setLoading(true);

    try {
      const response = await fetch(
        "http://localhost:8080/api/auth/signup",
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            fullName,
            email,
            password,
            confirmPassword,
          }),
        }
      );

      const data = await response.text();

      if (!response.ok) {
        setError(data || "Unable to create your account.");
        return;
      }

      setSuccess("Account created successfully. Redirecting to login...");

      setTimeout(() => {
        navigate("/login");
      }, 1000);
    } catch (error) {
      console.error(error);
      setError(
        "Unable to connect to the server. Please make sure the backend is running."
      );
    } finally {
      setLoading(false);
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

            <h2>
              Create an Account
            </h2>

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
                  onClick={() =>
                    setShowPassword((current) => !current)
                  }
                  aria-label={
                    showPassword
                      ? "Hide password"
                      : "Show password"
                  }
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
                  type={
                    showConfirmPassword
                      ? "text"
                      : "password"
                  }
                  placeholder="Confirm your password"
                  autoComplete="new-password"
                  value={confirmPassword}
                  onChange={(event) =>
                    setConfirmPassword(event.target.value)
                  }
                  required
                />

                <button
                  type="button"
                  className="signup-password-toggle"
                  onClick={() =>
                    setShowConfirmPassword((current) => !current)
                  }
                  aria-label={
                    showConfirmPassword
                      ? "Hide password"
                      : "Show password"
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

            {success && (
              <p
                role="status"
                style={{
                  color: "#247a45",
                  margin: "0 0 12px",
                }}
              >
                {success}
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
                  d="M20.5 20.5C26 32 35 33 35 26.5c0-5.5-5.5-8-11.5-8"
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
            <Link to="/login">
              Log in
            </Link>
          </p>

        </section>

      </div>

    </main>
  );
}

export default Signup;