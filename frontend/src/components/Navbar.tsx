import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import "./Navbar.css";

function Navbar() {
  const navigate = useNavigate();

  const [isLoggedIn, setIsLoggedIn] = useState(
    !!localStorage.getItem("authToken")
  );

  const handleSignOut = () => {
    localStorage.removeItem("authToken");
    setIsLoggedIn(false);
    navigate("/");
  };

  return (
    <header className="site-navbar">
      <div className="navbar-container">

        {/* BRAND */}
        <Link to="/" className="brand">
          <div className="brand-butterfly">🦋</div>

          <div className="brand-text">
            <span className="brand-name">
              Essential Butterfly
            </span>

            <span className="brand-subtitle">
              Support & Counselling
            </span>
          </div>
        </Link>


        {/* MAIN NAVIGATION */}
        <nav aria-label="Main navigation">

          <Link to="/" className="nav-link">
            Home
          </Link>

          <Link to="/about" className="nav-link">
            About Casey
          </Link>

          <Link to="/services" className="nav-link">
            Services & Supports
          </Link>

          <a href="#resources" className="nav-link">
            Resources
          </a>

          <Link to="/contact" className="nav-link">
            Contact
          </Link>

          <Link to="/intake" className="nav-link">
            Intake
          </Link>

        </nav>


        {/* CLIENT LOGIN / SIGN OUT */}
        {isLoggedIn ? (
          <button
            type="button"
            className="login-button signout-button"
            onClick={handleSignOut}
          >
            Sign Out
          </button>
        ) : (
          <Link to="/login" className="login-button">
            Client Login
          </Link>
        )}

      </div>
    </header>
  );
}

export default Navbar;