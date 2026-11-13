import { Link } from "react-router-dom";
import { usePageContent } from "../hooks/usePageContent";
import type { PageContent } from "../content/pageLayouts";
import "./DetailPages.css";

// "preview" is used by the editor to show unsaved wording inside the real page.
function Services({ preview }: { preview?: PageContent }) {
  const { text, services } = usePageContent("services", preview);
  const Root = preview ? "div" : "main";

  return (
    <Root className="detail-page">
      <section className="detail-hero services-hero" aria-labelledby="services-title">
        <div className="detail-shell detail-hero-grid">
          <div>
            <p className="detail-eyebrow">{text("heroEyebrow")}</p>
            <h1 id="services-title">{text("heroTitle")}</h1>
            <p className="detail-lead">{text("heroLead")}</p>
          </div>
          <img
            className="snapdragon-photo"
            src="/images/services-hero.jpg"
            alt="A support professional having a calm conversation with a client"
          />
        </div>
      </section>

      <section className="detail-section service-area" aria-labelledby="availability-title">
        <div className="detail-shell detail-two-column">
          <div>
            <p className="detail-eyebrow">{text("availEyebrow")}</p>
            <h2 id="availability-title">{text("availTitle")}</h2>
          </div>
          <div className="detail-copy">
            <p>{text("availP1")}</p>
            <p>{text("availP2")}</p>
          </div>
        </div>
      </section>

      <section className="detail-section detail-tint" aria-labelledby="service-list-title">
        <div className="detail-shell">
          <h2 id="service-list-title">{text("listTitle")}</h2>
          <p className="detail-intro">{text("listIntro")}</p>

          <div className="service-card-grid">
            {services.map((service, index) => (
              <article className="service-card" id={service.id} key={service.id}>
                <span className="service-number" aria-hidden="true">
                  {String(index + 1).padStart(2, "0")}
                </span>
                <h3>{service.title}</h3>
                <p>{service.text}</p>
                {service.comingSoon ? (
                  <span className="coming-soon">In development</span>
                ) : (
                  <Link to="/contact" aria-label={`Ask about ${service.title}`}>
                    Ask about this service <span aria-hidden="true">→</span>
                  </Link>
                )}
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="detail-section" aria-labelledby="tools-title">
        <div className="detail-shell detail-two-column">
          <div>
            <p className="detail-eyebrow">{text("toolsEyebrow")}</p>
            <h2 id="tools-title">{text("toolsTitle")}</h2>
          </div>
          <div className="detail-copy">
            <p>{text("toolsText")}</p>
          </div>
        </div>
      </section>

      <section className="detail-section detail-tint" aria-labelledby="process-title">
        <div className="detail-shell">
          <p className="detail-eyebrow">{text("processEyebrow")}</p>
          <h2 id="process-title">{text("processTitle")}</h2>
          <ol className="process-list">
            <li><strong>{text("step1Title")}</strong><span>{text("step1Text")}</span></li>
            <li><strong>{text("step2Title")}</strong><span>{text("step2Text")}</span></li>
            <li><strong>{text("step3Title")}</strong><span>{text("step3Text")}</span></li>
          </ol>
          <div className="detail-actions">
            <Link className="detail-primary" to="/intake">{text("primaryButton")}</Link>
            <Link className="detail-secondary" to="/contact">{text("secondaryButton")}</Link>
          </div>
        </div>
      </section>
    </Root>
  );
}

export default Services;
