import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import "./Login.css";

function AdminLogin() {
  const navigate = useNavigate();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);

  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleLogin = async () => {
    setError("");
    setLoading(true);

    try {
      const response = await fetch(
        "http://localhost:8080/api/admin/login",
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            email,
            password,
          }),
        }
      );

      const data = await response.text();

      if (!response.ok) {
        setError(data || "Invalid administrator email or password.");
        return;
      }

      localStorage.setItem("adminToken", data);

      navigate("/admin-dashboard");

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
    <main className="login-page">

      <section className="login-section">

        <div className="login-shape login-shape-left" />
        <div className="login-shape login-shape-right" />
        <div className="login-shape login-shape-bottom" />

        <div className="login-flower login-flower-left">
          <img
            src="/images/hero-flower.jpg"
            alt=""
            aria-hidden="true"
          />
        </div>

        <div className="login-flower login-flower-right">
          <img
            src="/images/hero-flower.jpg"
            alt=""
            aria-hidden="true"
          />
        </div>

        <div className="login-container">

          {/* LEFT SIDE */}

          <div className="login-introduction">

            <div className="login-eyebrow">
              ADMINISTRATOR ACCESS
            </div>

            <h1>
              Manage support with
              <br />
              care and confidence.
            </h1>

            <p className="login-intro-text">
              Sign in to access the administration dashboard,
              manage client requests, website content and
              essential practice settings.
            </p>

            <div className="login-support-card">

              <div
                className="login-support-icon"
                aria-hidden="true"
              >
                <span />
                <span />
              </div>

              <div>
                <strong>
                  Your administration, your control.
                </strong>

                <p>
                  Manage submissions, website content
                  and important settings from one
                  secure place.
                </p>
              </div>

            </div>

          </div>


          {/* ADMIN LOGIN CARD */}

          <div className="login-card">

            <div className="login-card-header">

              <h2>
                Admin Login
              </h2>

              <p>
                Enter your administrator credentials
                to access the dashboard.
              </p>

            </div>


            <div className="login-form">

              {/* EMAIL */}

              <div className="login-field">

                <label htmlFor="admin-login-email">
                  Email address
                </label>

                <div className="login-input-wrapper">

                  <span
                    className="login-input-icon"
                    aria-hidden="true"
                  >
                    ✉
                  </span>

                  <input
                    id="admin-login-email"
                    name="email"
                    type="email"
                    placeholder="admin@example.com"
                    autoComplete="email"
                    value={email}
                    onChange={(event) =>
                      setEmail(event.target.value)
                    }
                  />

                </div>

              </div>


              {/* PASSWORD */}

              <div className="login-field">

                <label htmlFor="admin-login-password">
                  Password
                </label>

                <div className="login-input-wrapper">

                  <span
                    className="login-input-icon"
                    aria-hidden="true"
                  >
                    🔒
                  </span>

                  <input
                    id="admin-login-password"
                    name="password"
                    type={
                      showPassword
                        ? "text"
                        : "password"
                    }
                    placeholder="Enter your password"
                    autoComplete="current-password"
                    value={password}
                    onChange={(event) =>
                      setPassword(event.target.value)
                    }
                    onKeyDown={(event) => {
                      if (event.key === "Enter") {
                        handleLogin();
                      }
                    }}
                  />

                  <button
                    type="button"
                    className="password-toggle"
                    aria-label={
                      showPassword
                        ? "Hide password"
                        : "Show password"
                    }
                    onClick={() =>
                      setShowPassword(!showPassword)
                    }
                  >
                    ◉
                  </button>

                </div>

              </div>


              {/* FORGOT PASSWORD */}

              <div className="forgot-password-row">

                <Link to="/admin/forgot-password">
                  Forgot password?
                </Link>

              </div>


              {/* ERROR */}

              {error && (
                <p
                  role="alert"
                  className="login-error"
                >
                  {error}
                </p>
              )}


              {/* LOGIN BUTTON */}

              <button
                type="button"
                className="login-submit"
                onClick={handleLogin}
                disabled={loading}
              >
                {loading
                  ? "Logging in..."
                  : "Log In"}
              </button>


              {/* OR */}

              <div className="login-divider">

                <span />
                <strong>OR</strong>
                <span />

              </div>


              {/* RETURN TO WEBSITE */}

              <Link
                to="/"
                className="visitor-button"
              >

                <span
                  className="visitor-icon"
                  aria-hidden="true"
                >
                  ♧
                </span>

                <span>
                  Return to website
                </span>

              </Link>


              {/* BUTTERFLY DIVIDER */}

              <div className="login-butterfly-divider">

                <span />

                <div aria-hidden="true">
                  ♡
                </div>

                <span />

              </div>


              {/* CLIENT LOGIN */}

              <p className="login-signup">

                Client access?{" "}

                <Link to="/login">
                  Go to Client Login
                </Link>

              </p>

            </div>

          </div>

        </div>

      </section>

    </main>
  );
}

export default AdminLogin;