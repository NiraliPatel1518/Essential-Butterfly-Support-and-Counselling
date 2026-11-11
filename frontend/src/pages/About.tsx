import { Link } from "react-router-dom";
import "./DetailPages.css";

function About() {
  return (
    <main className="detail-page">
      <section className="detail-hero" aria-labelledby="about-title">
        <div className="detail-shell detail-hero-grid">
          <div>
            <p className="detail-eyebrow">About Casey</p>
            <h1 id="about-title">
              Lived experience, professional knowledge and practical support.
            </h1>
            <p className="detail-lead">
              Casey is a BSW social worker with more than 16 years of
              experience supporting neurodiverse people and individuals with
              developmental disabilities.
            </p>
            <Link className="detail-primary" to="/contact">
              Start a conversation
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
            <p className="detail-eyebrow">Casey’s story</p>
            <h2 id="casey-story-title">Finding the tools that help us grow</h2>
          </div>

          <div className="detail-copy">
            <p>
              At a young age, Casey felt that she did not fit into the typical
              academic box and that the standard path was not working. She
              worked hard at school, but her marks did not reflect her effort.
              At age 13, she was diagnosed with a learning disability and ADHD.
            </p>
            <p>
              Casey firmly believes that we all learn differently and need to
              find the right tools to help us reach our goals. Assistive
              technology opened a world of possibilities for her. Her mother
              was a constant advocate and helped pave the way for their school
              district to use more technology to support students.
            </p>
            <p>
              No matter the diagnosis or profile, Casey believes there is a
              tool or accessibility support that can help each person reach
              their potential.
            </p>
          </div>
        </div>
      </section>

      <section className="detail-section detail-tint" aria-labelledby="experience-title">
        <div className="detail-shell detail-two-column">
          <div>
            <p className="detail-eyebrow">Professional experience</p>
            <h2 id="experience-title">Support across ages, needs and settings</h2>
          </div>

          <div className="detail-copy">
            <p>
              Casey has worked in the developmental-disabilities field for
              more than 16 years in a variety of roles. She has supported
              clients and students with special needs, neurodiversities, dual
              diagnoses and developmental disabilities, from age three to
              adults over 60.
            </p>
            <p>
              She especially values the “aha!” moment when a client or student
              discovers a tool or skill that works for them, feels proud of
              what they achieved and begins to believe more strongly in their
              abilities.
            </p>
            <p className="detail-highlight">
              “I want to help you build your toolbox to work smarter, not
              harder. I offer tutoring, coaching, respite and counselling.”
            </p>
          </div>
        </div>
      </section>

      <section className="detail-section" aria-labelledby="values-title">
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
        <img src="/images/essential-butterfly-logo.png" alt="" />
        <div>
          <h2 id="about-next-title">Would you like to see whether this approach fits your needs?</h2>
          <p>Ask a question or share what kind of support you are looking for.</p>
          <Link className="detail-primary" to="/contact">Contact Casey</Link>
        </div>
      </section>
    </main>
  );
}

export default About;
