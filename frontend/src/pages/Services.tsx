import { Link } from "react-router-dom";
import "./DetailPages.css";

const services = [
  {
    id: "respite",
    title: "Respite",
    text: "One-to-one or small-group respite for children or adults, at home or in the community. Support can focus on life skills, financial literacy, art, new hobbies and goals chosen with the client and family.",
  },
  {
    id: "social-life-skills",
    title: "Social Skills and Life Skills Coaching",
    text: "Individual or small-group support for friendships, independent-living safety, relationships, employment or volunteer preparation and other goals based on the person’s needs.",
  },
  {
    id: "tutoring-technology",
    title: "Tutoring and Assistive Tech / Tech Coaching",
    text: "Tutoring and technology coaching informed by more than 18 years of experience with assistive technology, SEA equipment, Google, Microsoft, Apple and communication applications.",
  },
  {
    id: "counselling",
    title: "Counselling",
    text: "Individual counselling shaped around your needs and goals through an empathetic, strengths-based and neurodiversity-informed approach.",
  },
  {
    id: "adhd-coaching",
    title: "ADHD Coaching and Executive Function Coaching",
    text: "Practical tools for procrastination, task management, organization, emotional regulation, self-advocacy, manageable goals and supportive check-ins.",
  },
  {
    id: "animal-dbt",
    title: "Animal-Based DBT (Dialectical Behaviour Therapy)",
    text: "A developing service focused on emotional-regulation skills, confidence and meaningful connection. Contact Casey for current availability and details.",
    comingSoon: true,
  },
];

function Services() {
  return (
    <main className="detail-page">
      <section className="detail-hero services-hero" aria-labelledby="services-title">
        <div className="detail-shell detail-hero-grid">
          <div>
            <p className="detail-eyebrow">Services and counselling supports</p>
            <h1 id="services-title">Support shaped around your needs, strengths and goals.</h1>
            <p className="detail-lead">
              Explore individualized counselling, coaching, tutoring, respite
              and technology supports for children, adults and families.
            </p>
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
            <p className="detail-eyebrow">Availability and service area</p>
            <h2 id="availability-title">Flexible support outside regular daytime hours</h2>
          </div>
          <div className="detail-copy">
            <p>
              Availability is currently limited because Casey works full time
              during the day and supports families in the community after
              hours. When time slots are open, weekday appointments may be
              available Monday to Friday from 5:00 p.m. to 9:00 p.m.
            </p>
            <p>
              Services may be available in Oakville, Burlington and Waterdown.
              Rates are reviewed according to support needs. If transportation
              is required for respite, mileage or an adjusted hourly rate may
              apply. A résumé and police check can be provided when appropriate.
            </p>
          </div>
        </div>
      </section>

      <section className="detail-section detail-tint" aria-labelledby="service-list-title">
        <div className="detail-shell">
          <h2 id="service-list-title">What I offer</h2>
          <p className="detail-intro">
            Every service begins with a conversation about needs, strengths,
            preferences and practical goals.
          </p>

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
            <p className="detail-eyebrow">Technology toolbox</p>
            <h2 id="tools-title">Assistive technology and learning tools</h2>
          </div>
          <div className="detail-copy">
            <p>
              Coaching may include Kurzweil, Read&amp;Write, Mindomo, Google
              Workspace for Education, Microsoft 365, speech-to-text,
              text-to-speech, Google Keep, OneNote, Equatio and accessibility
              features built into commonly used applications and devices.
            </p>
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
