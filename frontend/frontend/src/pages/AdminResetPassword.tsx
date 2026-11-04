import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import "./ResetPassword.css";

function AdminResetPassword() {
  const [searchParams] = useSearchParams();

  const token = searchParams.get("token") || "";

  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async () => {
    setMessage("");
    setError("");

    if (!token) {
      setError("This password reset link is invalid.");
      return;
    }

    if (!newPassword || !confirmPassword) {
      setError("Please enter and confirm your new password.");
      return;
    }

    if (newPassword.length < 8) {
      setError("Password must be at least 8 characters.");
      return;
    }

    if (newPassword !== confirmPassword) {
      setError("Passwords do not match.");
      return;
    }

    setLoading(true);

    try {
      const response = await fetch(
        "http://localhost:8080/api/password/reset",
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            token,
            newPassword,
            confirmPassword,
          }),
        }
      );

      const data = await response.text();

      if (!response.ok) {
        setError(data || "Unable to reset your password.");
        return;
      }

      setMessage(
        "Your administrator password has been reset successfully. You can now log in with your new password."
      );

      setNewPassword("");
      setConfirmPassword("");
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
    <main className="reset-password-page">
      <section className="reset-password-card">
        <div className="reset-password-content">
          <span className="reset-password-eyebrow">
            ADMIN ACCOUNT RECOVERY
          </span>

          <h1>Reset your password</h1>

          <p className="reset-password-description">
            Create a new password for your administrator account.
          </p>

          <form
            onSubmit={(event) => {
              event.preventDefault();
              handleSubmit();
            }}
          >
            <div className="reset-password-field">
              <label htmlFor="admin-new-password">
                New Password
              </label>

              <input
                id="admin-new-password"
                type="password"
                value={newPassword}
                onChange={(event) =>
                  setNewPassword(event.target.value)
                }
                placeholder="Enter your new password"
                autoComplete="new-password"
                disabled={loading}
              />
            </div>

            <div className="reset-password-field">
              <label htmlFor="admin-confirm-password">
                Confirm Password
              </label>

              <input
                id="admin-confirm-password"
                type="password"
                value={confirmPassword}
                onChange={(event) =>
                  setConfirmPassword(event.target.value)
                }
                placeholder="Confirm your new password"
                autoComplete="new-password"
                disabled={loading}
              />
            </div>

            {error && (
              <p className="reset-password-error">
                {error}
              </p>
            )}

            {message && (
              <p className="reset-password-success">
                {message}
              </p>
            )}

            <button
              type="submit"
              className="reset-password-button"
              disabled={loading}
            >
              {loading ? "Resetting..." : "Reset Password"}
            </button>
          </form>

          <Link
            to="/admin-login"
            className="reset-password-back"
          >
            ← Back to Admin Login
          </Link>
        </div>
      </section>
    </main>
  );
}

export default AdminResetPassword;