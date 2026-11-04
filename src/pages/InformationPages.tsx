import { Link } from "react-router-dom";
import "./InformationPages.css";

export function Privacy() {
  return (
    <main className="information-page">
      <h1>Privacy</h1>
      <p>This page is reserved for the client-approved privacy policy. Until that policy is finalized, do not submit sensitive or emergency information through the general contact form.</p>
      <p>Information submitted through secure intake is intended only to help determine appropriate support and next steps.</p>
      <Link to="/contact">Contact Casey with a privacy question</Link>
    </main>
  );
}

export function Accessibility() {
  return (
    <main className="information-page">
      <h1>Accessibility</h1>
      <p>Essential Butterfly aims to provide an inclusive website that can be used with a keyboard, screen reader, text enlargement and reduced-motion preferences.</p>
      <h2>Request another format or report a barrier</h2>
      <p>If you have difficulty accessing content or completing a form, contact Casey. Describe the page and the assistance or format you need.</p>
      <a href="mailto:casey@essentialbutterfly.com?subject=Website%20accessibility%20request">Email an accessibility request</a>
    </main>
  );
}

export function Sitemap() {
  const links = [["Home","/"],["About Casey","/about"],["Services","/services"],["Contact","/contact"],["Secure Intake","/intake"],["Client Login","/login"],["Privacy","/privacy"],["Accessibility","/accessibility"]];
  return (
    <main className="information-page">
      <h1>Site map</h1>
      <nav aria-label="Site map"><ul>{links.map(([label,to]) => <li key={to}><Link to={to}>{label}</Link></li>)}</ul></nav>
    </main>
  );
}
