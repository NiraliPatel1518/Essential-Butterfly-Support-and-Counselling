import { Link } from "react-router-dom";
import { usePageContent } from "../hooks/usePageContent";
import type { PageContent } from "../content/pageLayouts";
import "./InformationPages.css";

export function Privacy({ preview }: { preview?: PageContent }) {
  // Wording published from Manage Website Content; the layout never changes.
  const { text } = usePageContent("privacy", preview);
  const Root = preview ? "div" : "main";

  return (
    <Root className="information-page">
      <h1>{text("title")}</h1>
      <p>{text("p1")}</p>
      <p>{text("p2")}</p>
      <Link to="/contact">{text("linkText")}</Link>
    </Root>
  );
}

export function Accessibility() {
  return (
    <main className="information-page">
      <h1>Accessibility</h1>
      <p>Essential Butterfly aims to meet the Accessibility for Ontarians with Disabilities Act (AODA) requirements and WCAG 2.1 Level AA. The website supports keyboard navigation, screen readers, text enlargement, visible focus indicators, sufficient colour contrast and reduced-motion preferences.</p>
      <h2>Request another format or report a barrier</h2>
      <p>If you have difficulty accessing content or completing a form, contact Casey. Describe the page and the assistance or format you need.</p>
      <a href="mailto:casey@essentialbutterfly.com?subject=Website%20accessibility%20request">Email an accessibility request</a>
    </main>
  );
}

export function Sitemap() {
  const links = [["Home","/"],["About Casey","/about"],["Services","/services"],["Contact","/contact"],["Resource Library","/resources"],["Secure Intake","/intake"],["Sign In","/login"],["Privacy","/privacy"],["Accessibility","/accessibility"]];
  return (
    <main className="information-page">
      <h1>Site map</h1>
      <nav aria-label="Site map"><ul>{links.map(([label,to]) => <li key={to}><Link to={to}>{label}</Link></li>)}</ul></nav>
    </main>
  );
}
