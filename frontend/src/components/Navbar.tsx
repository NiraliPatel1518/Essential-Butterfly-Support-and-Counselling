import { useState, useEffect } from "react";
import {
  Link,
  NavLink,
  useNavigate,
  useLocation,
} from "react-router-dom";

import { usePublicFeatures } from "../context/PublicFeatureContext";

import "./Navbar.css";

const navigation = [
  { to: "/", label: "Home", end: true },
  { to: "/about", label: "About Casey" },
  { to: "/services", label: "Services" },
  { to: "/contact", label: "Contact" },
];

function Navbar() {
  const navigate = useNavigate();
  const location = useLocation();
  const { isEnabled } = usePublicFeatures();

  const [menuOpen, setMenuOpen] = useState(false);
  const [authVersion, setAuthVersion] = useState(0);

  useEffect(() => {
    setAuthVersion((version) => version + 1);
  }, [location.pathname]);

  useEffect(() => {
    const refreshAuth = () => {
      setAuthVersion((version) => version + 1);
    };

    window.addEventListener("storage", refreshAuth);
    window.addEventListener("auth-changed", refreshAuth);

    return () => {
      window.removeEventListener("storage", refreshAuth);
      window.removeEventListener("auth-changed", refreshAuth);
    };
  }, []);

  const clientLoggedIn = !!localStorage.getItem("authToken");
  const adminLoggedIn = !!localStorage.getItem("adminToken");
  const isLoggedIn = clientLoggedIn || adminLoggedIn;

  const dashboardPath = adminLoggedIn
    ? "/admin-dashboard"
    : "/client-dashboard";

  const dashboardLabel = adminLoggedIn
    ? "Admin Dashboard"
    : "My Dashboard";

  const handleSignOut = () => {
    localStorage.removeItem("authToken");
    localStorage.removeItem("adminToken");

    window.dispatchEvent(new Event("auth-changed"));

    setMenuOpen(false);
    navigate("/");
  };

  return (
    <header className="site-navbar">
      <div className="navbar-container">
        <Link
          to="/"
          className="brand"
          aria-label="Essential Butterfly Support and Counselling, home"
        >
          <img
            src="/images/butterfly-mark.png"
            alt=""
            className="brand-butterfly"
          />

          <span className="brand-text">
            <span className="brand-name">Essential Butterfly</span>
            <span className="brand-subtitle">
              Support &amp; Counselling
            </span>
          </span>
        </Link>

        <button
          type="button"
          className="menu-button"
          aria-expanded={menuOpen}
          aria-controls="primary-navigation"
          onClick={() => setMenuOpen((open) => !open)}
        >
          <span aria-hidden="true">
            {menuOpen ? "×" : "☰"}
          </span>
          <span>{menuOpen ? "Close" : "Menu"}</span>
        </button>

        <div className={`navigation-panel ${menuOpen ? "open" : ""}`}>
          <nav id="primary-navigation" aria-label="Main navigation">
            {navigation.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                onClick={() => setMenuOpen(false)}
                className={({ isActive }) =>
                  `nav-link${isActive ? " active" : ""}`
                }
              >
                {item.label}
              </NavLink>
            ))}

            {isEnabled("UPDATES") && (
              <NavLink
                to="/updates"
                onClick={() => setMenuOpen(false)}
                className={({ isActive }) =>
                  `nav-link${isActive ? " active" : ""}`
                }
              >
                Updates
              </NavLink>
            )}
          </nav>

          <div className="navbar-account-actions">
            {isLoggedIn ? (
              <>
                <Link
                  to={dashboardPath}
                  className="login-button navbar-dashboard-button"
                  onClick={() => setMenuOpen(false)}
                >
                  {dashboardLabel}
                  <span aria-hidden="true"> →</span>
                </Link>

                <button
                  type="button"
                  className="login-button signout-button"
                  onClick={handleSignOut}
                >
                  Sign out
                </button>
              </>
            ) : (
              <Link
                to="/login"
                className="login-button"
                onClick={() => setMenuOpen(false)}
              >
                Client Login
              </Link>
            )}
          </div>
        </div>
      </div>
    </header>
  );
}

export default Navbar;