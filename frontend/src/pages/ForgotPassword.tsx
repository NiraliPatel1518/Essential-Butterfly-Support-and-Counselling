import { useState } from "react";
import { Link } from "react-router-dom";
import "./ForgotPassword.css";

function ForgotPassword() {
  const [email, setEmail] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async () => {
    setMessage("");
    setError("");

    if (!email.trim()) {
      setError("Please enter your email address.");
      return;
    }

    setLoading(true);

    try {
      const normalizedEmail = email.trim().toLowerCase();

      const response = await fetch(
        `http://localhost:8080/api/password/forgot?type=CLIENT&email=${encodeURIComponent(normalizedEmail)}`,
        {
          method: "POST",
        }
      );

      const data = await response.text();

      if (!response.ok) {
        setError(data || "Unable to process your request.");
        return;
      }

      setMessage(
        "If an account exists with this email, a password reset link has been generated."
      );

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
    <main className="forgot-password-page">
      <section className="forgot-password-card">
        <div className="forgot-password-content">

          <span className="forgot-password-eyebrow">
            ACCOUNT RECOVERY
          </span>

          <h1>Forgot your password?</h1>

          <p className="forgot-password-description">
            Enter the email address associated with your account and
            we'll help you reset your password.
          </p>

          <form
            onSubmit={(event) => {
              event.preventDefault();
              handleSubmit();
            }}
          >
            <div className="forgot-password-field">
              <label htmlFor="email">
                Email Address
              </label>

              <input
                id="email"
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                placeholder="Enter your email"
                autoComplete="email"
                disabled={loading}
              />
            </div>

            {error && (
              <p className="forgot-password-error">
                {error}
              </p>
            )}

            {message && (
              <p className="forgot-password-success">
                {message}
              </p>
            )}

            <button
              type="submit"
              className="forgot-password-button"
              disabled={loading}
            >
              {loading
                ? "Sending..."
                : "Send Reset Link"}
            </button>
          </form>

          <Link
            to="/login"
            className="forgot-password-back"
          >
            ← Back to Client Login
          </Link>

        </div>
      </section>
    </main>
  );
}

export default ForgotPassword;