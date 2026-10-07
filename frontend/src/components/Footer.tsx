import { Link } from "react-router-dom";
import type { ReactNode } from "react";
import "./Footer.css";

type IconName = "email" | "phone" | "location" | "instagram" | "facebook" | "linkedin";

function Icon({ name }: { name: IconName }) {
  const paths: Record<IconName, ReactNode> = {
    email: <><rect x="3" y="5" width="18" height="14" rx="2" /><path d="m3 7 9 6 9-6" /></>,
    phone: <path d="M22 16.92v3a2 2 0 0 1-2.18 2A19.79 19.79 0 0 1 3.08 5.18 2 2 0 0 1 5.08 3h3a2 2 0 0 1 2 1.72c.12.9.33 1.78.62 2.63a2 2 0 0 1-.45 2.11L9 10.73A16 16 0 0 0 13.27 15l1.27-1.27a2 2 0 0 1 2.11-.45c.85.29 1.73.5 2.63.62A2 2 0 0 1 22 16.92Z" />,
    location: <><path d="M20 10c0 5-8 12-8 12S4 15 4 10a8 8 0 1 1 16 0Z" /><circle cx="12" cy="10" r="2.5" /></>,
    instagram: <><rect x="3" y="3" width="18" height="18" rx="5" /><circle cx="12" cy="12" r="4" /><path d="M17.5 6.5h.01" /></>,
    facebook: <path d="M14 8h3V4h-3c-3 0-5 2-5 5v3H6v4h3v6h4v-6h3.2l.8-4h-4V9c0-.7.3-1 1-1Z" />,
    linkedin: <><rect x="3" y="9" width="4" height="12" /><path d="M5 3a2 2 0 1 0 0 4 2 2 0 0 0 0-4ZM11 21V9h4v2c1-1.5 2.3-2.3 4-2.3 2.8 0 4 1.8 4 5.2V21h-4v-6.3c0-1.5-.5-2.3-1.8-2.3-1.4 0-2.2 1-2.2 2.8V21Z" /></>,
  };

  return <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">{paths[name]}</svg>;
}

const instagramUrl = import.meta.env.VITE_INSTAGRAM_URL || "https://www.instagram.com/";
const facebookUrl = import.meta.env.VITE_FACEBOOK_URL || "https://www.facebook.com/";
const linkedinUrl = import.meta.env.VITE_LINKEDIN_URL || "https://www.linkedin.com/";
const locationUrl = import.meta.env.VITE_LOCATION_URL || "https://www.google.com/maps/search/?api=1&query=Oakville%2C%20Ontario";

function Footer() {
  return (
    <footer className="site-footer">
      <div className="footer-main">
        <div className="footer-brand">
          <Link to="/" className="footer-logo" aria-label="Essential Butterfly home">
            <span className="footer-mark-frame"><img className="footer-butterfly" src="/images/butterfly-mark.png" alt="" /></span>
            <span className="footer-brand-text"><strong>Essential Butterfly</strong><small>Support &amp; Counselling</small></span>
          </Link>
          <p>Counselling, coaching, tutoring and respite supports designed to build skills, reduce challenges and grow with confidence.</p>
        </div>

        <div className="footer-column">
          <h2>Explore</h2>
          <nav aria-label="Footer navigation">
            <Link to="/">Home</Link><Link to="/about">About Casey</Link><Link to="/services">Services &amp; Supports</Link><Link to="/contact">Contact</Link><Link to="/intake">Secure Intake</Link>
          </nav>
        </div>

        <div className="footer-column footer-contact">
          <h2>Contact</h2>
          <a className="footer-contact-link" href="mailto:casey@essentialbutterfly.com"><span className="contact-icon"><Icon name="email" /></span><span>casey@essentialbutterfly.com</span></a>
          <a className="footer-contact-link" href="tel:+12092582002"><span className="contact-icon"><Icon name="phone" /></span><span>(209) 258-0202</span></a>
          <a className="footer-contact-link" href={locationUrl} target="_blank" rel="noreferrer"><span className="contact-icon"><Icon name="location" /></span><span>Oakville, Ontario <span className="sr-only">(opens in a new tab)</span></span></a>
          <div className="social-links" aria-label="Social media">
            <a href={instagramUrl} target="_blank" rel="noreferrer" aria-label="Instagram (opens in a new tab)"><Icon name="instagram" /></a>
            <a href={facebookUrl} target="_blank" rel="noreferrer" aria-label="Facebook (opens in a new tab)"><Icon name="facebook" /></a>
            <a href={linkedinUrl} target="_blank" rel="noreferrer" aria-label="LinkedIn (opens in a new tab)"><Icon name="linkedin" /></a>
          </div>
        </div>
      </div>

      <div className="footer-bottom">
        <p>© 2026 Essential Butterfly Support &amp; Counselling. All rights reserved.</p>
        <nav aria-label="Legal navigation"><Link to="/privacy">Privacy Policy</Link><Link to="/accessibility">Accessibility</Link><Link to="/sitemap">Site Map</Link></nav>
      </div>
    </footer>
  );
}

export default Footer;
