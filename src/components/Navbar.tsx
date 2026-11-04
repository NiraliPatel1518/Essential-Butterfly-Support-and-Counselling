import { useState } from "react";
import { Link, NavLink, useNavigate } from "react-router-dom";
import "./Navbar.css";

const navigation = [
  { to: "/", label: "Home", end: true },
  { to: "/about", label: "About Casey" },
  { to: "/services", label: "Services" },
  { to: "/contact", label: "Contact" },
];

function Navbar() {
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);

  const clientLoggedIn = !!localStorage.getItem("authToken");
  const adminLoggedIn = !!localStorage.getItem("adminToken");
  const isLoggedIn = clientLoggedIn || adminLoggedIn;

  // Admin token takes precedence if both tokens are present.
  const dashboardPath = adminLoggedIn
    ? "/admin-dashboard"
    : "/client-dashboard";

  const dashboardLabel = adminLoggedIn
    ? "Admin Dashboard"
    : "My Dashboard";

  const handleSignOut = () => {
    localStorage.removeItem("authToken");
    localStorage.removeItem("adminToken");
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

        <div
          className={`navigation-panel ${menuOpen ? "open" : ""}`}
        >
          <nav
            id="primary-navigation"
            aria-label="Main navigation"
          >
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