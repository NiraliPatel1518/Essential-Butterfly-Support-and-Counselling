import { Link } from "react-router-dom";
import { usePageContent } from "../hooks/usePageContent";
import type { PageContent } from "../content/pageLayouts";
import "./DetailPages.css";

// "preview" is used by the editor to show unsaved wording inside the real page.
function About({ preview }: { preview?: PageContent }) {
  const { text } = usePageContent("about", preview);
  const Root = preview ? "div" : "main";

  return (
    <Root className="detail-page">
      <section className="detail-hero" aria-labelledby="about-title">
        <div className="detail-shell detail-hero-grid">
          <div>
            <p className="detail-eyebrow">{text("heroEyebrow")}</p>
            <h1 id="about-title">{text("heroTitle")}</h1>
            <p className="detail-lead">{text("heroLead")}</p>
            <Link className="detail-primary" to="/contact">
              {text("heroButton")}
            </Link>
          </div>

          <figure className="detail-photo">
            <img
              src="/images/casey-bio.jpg"
              alt="Casey, founder of Essential Butterfly Support and Counselling"
            />
          </figure>
        </div>
      </section>

      <section className="detail-section" aria-labelledby="casey-story-title">
        <div className="detail-shell detail-two-column">
          <div>
            <p className="detail-eyebrow">{text("storyEyebrow")}</p>
            <h2 id="casey-story-title">{text("storyTitle")}</h2>
          </div>

          <div className="detail-copy">
            <p>{text("storyP1")}</p>
            <p>{text("storyP2")}</p>
            <p>{text("storyP3")}</p>
          </div>
        </div>
      </section>

      <section className="detail-section detail-tint" aria-labelledby="experience-title">
        <div className="detail-shell detail-two-column">
          <div>
            <p className="detail-eyebrow">{text("expEyebrow")}</p>
            <h2 id="experience-title">{text("expTitle")}</h2>
          </div>

          <div className="detail-copy">
            <p>{text("expP1")}</p>
            <p>{text("expP2")}</p>
            <p className="detail-highlight">{text("expQuote")}</p>
          </div>
        </div>
      </section>

      <section className="detail-section" aria-labelledby="values-title">
        <div className="detail-shell">
          <p className="detail-eyebrow">{text("valuesEyebrow")}</p>
          <h2 id="values-title">{text("valuesTitle")}</h2>
          <div className="value-grid">
            <article><h3>{text("value1Title")}</h3><p>{text("value1Text")}</p></article>
            <article><h3>{text("value2Title")}</h3><p>{text("value2Text")}</p></article>
            <article><h3>{text("value3Title")}</h3><p>{text("value3Text")}</p></article>
            <article><h3>{text("value4Title")}</h3><p>{text("value4Text")}</p></article>
          </div>
        </div>
      </section>

      <section className="detail-cta" aria-labelledby="about-next-title">
        <img src="/images/essential-butterfly-logo.png" alt="" />
        <div>
          <h2 id="about-next-title">{text("ctaTitle")}</h2>
          <p>{text("ctaText")}</p>
          <Link className="detail-primary" to="/contact">{text("ctaButton")}</Link>
        </div>
      </section>
    </Root>
  );
}

export default About;
