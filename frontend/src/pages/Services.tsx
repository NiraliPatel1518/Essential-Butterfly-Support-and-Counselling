import { Link } from "react-router-dom";
import "./DetailPages.css";

const services = [
  { title: "Respite", text: "Individual or small-group support in the home or community, shaped around assessed needs and goals." },
  { title: "Social Skills and Life Skills Coaching", text: "Support with friendships, daily-life navigation, independence, employment preparation and community participation." },
  { title: "Tutoring and Assistive Technology Coaching", text: "Support with academic learning, Google and Microsoft tools, communication apps and selected accessibility technology." },
  { title: "Counselling", text: "Individual counselling built around the person’s goals through an empathetic and neurodiversity-informed approach." },
  { title: "ADHD and Executive Function Coaching", text: "Tools for task management, emotional regulation, self-advocacy, organization and hands-on check-ins." },
  { title: "Animal-Based DBT Skills Coaching", text: "A future service focused on emotional-regulation skills, confidence and meaningful connection." },
];

function Services() {
  return (
    <main className="detail-page">
      <section className="detail-hero services-hero" aria-labelledby="services-title">
        <div className="detail-shell detail-hero-grid">
          <div>
            <p className="detail-eyebrow">Services and counselling supports</p>
            <h1 id="services-title">Support shaped around your needs, strengths and goals.</h1>
            <p className="detail-lead">Explore practical, individualized support for counselling, coaching, tutoring, respite and everyday goals.</p>
          </div>
          <img className="snapdragon-photo" src="/images/white-snapdragons.jpeg" alt="White snapdragon flowers" />
        </div>
      </section>

      <section className="detail-section" aria-labelledby="service-list-title">
        <div className="detail-shell">
          <h2 id="service-list-title">Ways we may be able to help</h2>
          <p className="detail-intro">Each service begins with a conversation. Availability, suitability and the next step can be confirmed with Casey.</p>
          <div className="service-card-grid">
            {services.map((service, index) => (
              <article className="service-card" key={service.title}>
                <span className="service-number" aria-hidden="true">{String(index + 1).padStart(2, "0")}</span>
                <h3>{service.title}</h3>
                <p>{service.text}</p>
                {service.title === "Animal-Based DBT Skills Coaching" ? (
                  <span className="coming-soon">Coming soon</span>
                ) : (
                  <Link to="/contact" aria-label={`Ask about ${service.title}`}>Ask about this service <span aria-hidden="true">→</span></Link>
                )}
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="detail-section detail-tint" aria-labelledby="process-title">
        <div className="detail-shell">
          <p className="detail-eyebrow">What happens next</p>
          <h2 id="process-title">A simple and respectful first step</h2>
          <ol className="process-list">
            <li><strong>Connect.</strong><span>Send a general question or begin the secure intake process.</span></li>
            <li><strong>Discuss.</strong><span>Talk about needs, strengths, preferences and practical goals.</span></li>
            <li><strong>Plan.</strong><span>Confirm whether a service is suitable and agree on the next step.</span></li>
          </ol>
          <div className="detail-actions">
            <Link className="detail-primary" to="/intake">Begin secure intake</Link>
            <Link className="detail-secondary" to="/contact">Ask a question</Link>
          </div>
        </div>
      </section>
    </main>
  );
}

export default Services;
