import { Link } from "react-router-dom";
import "./DetailPages.css";

function About() {
  return (
    <main className="detail-page">
      <section className="detail-hero" aria-labelledby="about-title">
        <div className="detail-shell detail-hero-grid">
          <div>
            <p className="detail-eyebrow">About Casey</p>
            <h1 id="about-title">Support shaped by experience, curiosity and care.</h1>
            <p className="detail-lead">Casey brings more than 16 years of professional experience and a personal connection to the neurodiversity community.</p>
            <Link className="detail-primary" to="/contact">Start a conversation</Link>
          </div>
          <figure className="detail-photo">
            <img src="/images/casey-back-view.jpg" alt="A person looking across a calm mountain landscape" />
          </figure>
        </div>
      </section>

      <section className="detail-section" aria-labelledby="approach-title">
        <div className="detail-shell detail-two-column">
          <div>
            <p className="detail-eyebrow">A person-centred approach</p>
            <h2 id="approach-title">Meeting each person where they are</h2>
          </div>
          <div className="detail-copy">
            <p>Casey’s work is warm, practical and collaborative. Support begins with listening to the individual and family, recognizing strengths and identifying the everyday barriers that matter most.</p>
            <p>As a mother to a neurodiverse child and through her own lived experience, Casey understands that people can learn, communicate and grow in different ways. Strategies are therefore adapted to the person instead of asking the person to fit one method.</p>
          </div>
        </div>
      </section>

      <section className="detail-section detail-tint" aria-labelledby="values-title">
        <div className="detail-shell">
          <p className="detail-eyebrow">What guides the work</p>
          <h2 id="values-title">Clear support with room to grow</h2>
          <div className="value-grid">
            <article><h3>Strengths first</h3><p>Build from abilities, interests and existing successes.</p></article>
            <article><h3>Practical tools</h3><p>Choose strategies that can be used at home, school, work and in the community.</p></article>
            <article><h3>Collaborative goals</h3><p>Set meaningful next steps with the individual and family, not for them.</p></article>
            <article><h3>Respect and dignity</h3><p>Create an inclusive, affirming environment where people feel heard.</p></article>
          </div>
        </div>
      </section>

      <section className="detail-cta" aria-labelledby="about-next-title">
        <img src="/images/white-snapdragons-close.jpeg" alt="" />
        <div>
          <h2 id="about-next-title">Would you like to see whether the approach fits your needs?</h2>
          <p>Ask a question or share what kind of support you are looking for.</p>
          <Link className="detail-primary" to="/contact">Contact Casey</Link>
        </div>
      </section>
    </main>
  );
}

export default About;
