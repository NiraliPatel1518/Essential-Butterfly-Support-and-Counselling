import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import "./Login.css";

function Login() {
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
        "http://localhost:8080/api/auth/login",
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
        setError(data || "Invalid email or password.");
        return;
      }

      localStorage.setItem("authToken", data);

      navigate("/client-dashboard");

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
              WELCOME BACK
            </div>

            <h1>
              Support starts with
              <br />
              feeling understood.
            </h1>

            <p className="login-intro-text">
              Sign in to access your client dashboard,
              saved resources and support requests.
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
                  Your support, your page.
                </strong>

                <p>
                  Practical tools, reflective resources
                  and gentle guidance to help you
                  move forward.
                </p>
              </div>

            </div>

          </div>


          {/* LOGIN CARD */}

          <div className="login-card">

            <div className="login-card-header">

              <h2>
                Client Login
              </h2>

              <p>
                Enter your details to access your account.
              </p>

            </div>


            {/* NO FORM ELEMENT — LOGIN IS HANDLED BY BUTTON */}

            <div className="login-form">

              {/* EMAIL */}

              <div className="login-field">

                <label htmlFor="login-email">
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
                    id="login-email"
                    name="email"
                    type="email"
                    placeholder="you@example.com"
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

                <label htmlFor="login-password">
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
                    id="login-password"
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

                <Link to="/forgot-password">
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


              {/* VISITOR */}

              <Link
                to="/"
                className="visitor-button"
              >

                <span
                  className="visitor-icon"
                  aria-hidden="true"
                >
                  ♙
                </span>

                <span>
                  Continue as a visitor
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


              {/* SIGN UP */}

              <p className="login-signup">

                New here?{" "}

                <Link to="/signup">
                  Create an account
                </Link>

              </p>

            </div>

          </div>

        </div>

      </section>

    </main>
  );
}

export default Login;