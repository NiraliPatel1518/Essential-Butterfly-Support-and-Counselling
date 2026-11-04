import { useState } from "react";
import { Link, NavLink, useNavigate } from "react-router-dom";
import "./Navbar.css";

const navigation = [
  { to: "/", label: "Home", end: true },
  { to: "/about", label: "About Casey" },
  { to: "/services", label: "Services" },
  { to: "/contact", label: "Contact" },
  { to: "/intake", label: "Secure Intake" },
];

function Navbar() {
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const isLoggedIn = !!localStorage.getItem("authToken") || !!localStorage.getItem("adminToken");

  const handleSignOut = () => {
    localStorage.removeItem("authToken");
    localStorage.removeItem("adminToken");
    setMenuOpen(false);
    navigate("/");
  };

  return (
    <header className="site-navbar">
      <div className="navbar-container">
        <Link to="/" className="brand" aria-label="Essential Butterfly Support and Counselling, home">
          <img src="/images/butterfly-mark.png" alt="" className="brand-butterfly" />
          <span className="brand-text">
            <span className="brand-name">Essential Butterfly</span>
            <span className="brand-subtitle">Support &amp; Counselling</span>
          </span>
        </Link>

        <button type="button" className="menu-button" aria-expanded={menuOpen} aria-controls="primary-navigation" onClick={() => setMenuOpen((open) => !open)}>
          <span aria-hidden="true">{menuOpen ? "×" : "☰"}</span>
          <span>{menuOpen ? "Close" : "Menu"}</span>
        </button>

        <div className={`navigation-panel ${menuOpen ? "open" : ""}`}>
          <nav id="primary-navigation" aria-label="Main navigation">
            {navigation.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.end} onClick={() => setMenuOpen(false)} className={({ isActive }) => `nav-link${isActive ? " active" : ""}`}>
                {item.label}
              </NavLink>
            ))}
          </nav>

          {isLoggedIn ? (
            <button type="button" className="login-button signout-button" onClick={handleSignOut}>Sign out</button>
          ) : (
            <Link to="/login" className="login-button" onClick={() => setMenuOpen(false)}>Client login</Link>
          )}
        </div>
      </div>
    </header>
  );
}

export default Navbar;
