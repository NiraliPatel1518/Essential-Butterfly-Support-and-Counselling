import { Link } from "react-router-dom";
import "./Footer.css";

function Footer() {
  return (
    <footer className="site-footer">

      <div className="footer-main">

        {/* BRAND */}
        <div className="footer-brand">

          <Link to="/" className="footer-logo">
            <img className="footer-butterfly" src="/images/butterfly-mark.png" alt="" />

            <span className="footer-brand-text">
              <strong>Essential Butterfly</strong>
              <small>Support & Counselling</small>
            </span>
          </Link>

          <p>
            Counselling, coaching, tutoring and respite supports
            designed to build skills, reduce challenges and grow
            with confidence.
          </p>

        </div>


        {/* EXPLORE */}
        <div className="footer-column">

          <h3>EXPLORE</h3>

          <nav aria-label="Footer navigation">

            <Link to="/">
              Home
            </Link>

            <Link to="/about">
              About Casey
            </Link>

            <Link to="/services">
              Services & Supports
            </Link>

            <Link to="/contact">
              Contact
            </Link>

            <Link to="/intake">
              Secure Intake
            </Link>

          </nav>

        </div>


        {/* CONTACT */}
        <div className="footer-column footer-contact">

          <h3>CONTACT</h3>

          <a href="mailto:casey@essentialbutterfly.com">
            <span className="contact-icon">✉</span>
            casey@essentialbutterfly.com
          </a>

          <a href="tel:+12092582002">
            <span className="contact-icon">●</span>
            (209) 258-0202
          </a>

          <div className="footer-location">
            <span className="contact-icon">●</span>
            Oakville, Ontario
          </div>


        </div>

      </div>


      {/* BOTTOM BAR */}

      <div className="footer-bottom">

        <p>
          © 2026 Essential Butterfly Support & Counselling.
          All rights reserved.
        </p>

        <nav aria-label="Legal navigation">

          <Link to="/privacy">
            Privacy Policy
          </Link>

          <Link to="/accessibility">
            Accessibility
          </Link>

          <Link to="/sitemap">
            Site Map
          </Link>

        </nav>

      </div>

    </footer>
  );
}

export default Footer;
