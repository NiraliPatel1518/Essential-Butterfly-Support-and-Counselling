
import { useState } from "react";
import { Link } from "react-router-dom";
import { apiUrl } from "../api/api";
import "./ForgotPassword.css";

function ForgotPassword() {
  const [email, setEmail] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const readError = async (response: Response): Promise<string> => {
    const responseText = (await response.text()).trim();

    if (!responseText) {
      return "Unable to process your request. Please try again.";
    }

    try {
      const responseBody = JSON.parse(responseText) as {
        message?: string;
        error?: string;
      };

      return (
        responseBody.message ||
        responseBody.error ||
        "Unable to process your request. Please try again."
      );
    } catch {
      return responseText;
    }
  };

  const handleSubmit = async () => {
    setMessage("");
    setError("");

    const normalizedEmail = email.trim().toLowerCase();
    const emailPattern = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;

    if (!normalizedEmail) {
      setError("Please enter your email address.");
      return;
    }

    if (!emailPattern.test(normalizedEmail)) {
      setError("Please enter a valid email address such as name@example.com.");
      return;
    }

    setLoading(true);

    try {
      const response = await fetch(
        apiUrl(
          `/api/password/forgot?email=${encodeURIComponent(
            normalizedEmail
          )}`
        ),
        { method: "POST" }
      );

      if (!response.ok) {
        setError(await readError(response));
        return;
      }

      setMessage(
        "If an account exists with this email, a password reset link has been generated. Please check the instructions provided by the application."
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
          <p className="forgot-password-eyebrow">
            ACCOUNT RECOVERY
          </p>

          <h1>Forgot your password?</h1>

          <p className="forgot-password-description">
            Enter the email address associated with your account
            and we'll help you reset your password.
          </p>

          <form
            noValidate
            onSubmit={(event) => {
              event.preventDefault();
              void handleSubmit();
            }}
          >
            <div className="forgot-password-field">
              <label htmlFor="email">Email Address</label>

              <input
                id="email"
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                placeholder="Enter your email"
                autoComplete="email"
                inputMode="email"
                aria-describedby={
                  error
                    ? "forgot-password-help forgot-password-error"
                    : "forgot-password-help"
                }
                aria-invalid={Boolean(error)}
                required
                disabled={loading}
              />

              <p
                id="forgot-password-help"
                className="forgot-password-help"
              >
                Use the email address connected to your account.
              </p>
            </div>

            {error && (
              <p
                className="forgot-password-error"
                id="forgot-password-error"
                role="alert"
              >
                {error}
              </p>
            )}

            {message && (
              <p
                className="forgot-password-success"
                role="status"
              >
                {message}
              </p>
            )}

            <button
              type="submit"
              className="forgot-password-button"
              disabled={loading}
            >
              {loading ? "Processing..." : "Send Reset Link"}
            </button>
          </form>

          <Link
            to="/login"
            className="forgot-password-back"
          >
            ← Back to Login
          </Link>
        </div>
      </section>
    </main>
  );
}

export default ForgotPassword;
