import { Link } from "react-router-dom";
import "./Home.css";

function Home() {
  return (
    <main className="home-page">

      {/* HERO */}

      <section className="hero-section">

        <div className="hero-decoration hero-decoration-one" />
        <div className="hero-decoration hero-decoration-two" />

        <div className="hero-container">

          <div className="hero-content">

            <div className="section-label">
              <span>ESSENTIAL BUTTERFLY SUPPORT &amp; COUNSELLING</span>
              <i />
            </div>

            <h1>
              Support to help you
              <br />
              work smarter,
              <br />
              <span>not harder.</span>
            </h1>

            <p className="hero-description">
              Practical counselling, coaching and assistive technology
              strategies to help you build skills, reduce challenges and
              grow with confidence.
            </p>

            <div className="hero-buttons">

              <Link to="/services" className="purple-button">
                Explore Services
                <span>→</span>
              </Link>

              <Link to="/contact" className="outline-button">
                Get in Touch
              </Link>

            </div>

            <div className="hero-features">

              <HeroFeature
                icon="♧"
                title="Individualized"
                subtitle="Support"
              />

              <HeroFeature
                icon="✥"
                title="Practical"
                subtitle="Strategies"
              />

              <HeroFeature
                icon="♡"
                title="Inclusive"
                subtitle="& Affirming Space"
              />

            </div>

          </div>


          <div className="hero-image-wrapper">

            <div className="hero-image-shape">

              <img
                src="/images/hero-flower.jpg"
                alt="White flowers growing in a warm natural landscape"
              />

            </div>

            <div className="hero-gold-line" />

          </div>

        </div>

      </section>


      {/* INTRODUCTION */}

      <section className="intro-section">

        <div className="section-container intro-container">

          <div className="intro-content">

            <div className="section-label">
              <span>A DIFFERENT PATH CAN STILL LEAD FORWARD</span>
              <i />
            </div>

            <h2>
              Everyone has their own way
              <br />
              of learning, thinking and growing.
            </h2>

            <p>
              Neurodiversity is part of what makes the world such a unique
              place. At Essential Butterfly, we take a strengths-based,
              person-centred approach to supporting individuals and families
              in building skills, confidence and independence. Whether you're
              navigating ADHD, autism, learning differences or other life
              transitions, support is here to help you move forward, one
              step at a time.
            </p>

            <div className="info-box">

              <div className="info-box-icon">
                ♧
              </div>

              <p>
                You're not alone. Support is available for individuals,
                families and caregivers at every stage. Let's find a path
                that feels right for you.
              </p>

            </div>

          </div>


          <div className="intro-image-wrapper">

            <img
              src="/images/support-mountain.jpg"
              alt="Person sitting peacefully in a mountain landscape"
            />

          </div>

        </div>

      </section>


      {/* EXPERIENCE */}

      <section className="experience-section">

        <div className="section-container">

          <div className="section-label">
            <span>EXPERIENCE &amp; PERSPECTIVE</span>
            <i />
          </div>

          <h2>
            Support informed by experience,
            <br />
            curiosity and practical tools.
          </h2>


          <div className="experience-grid">

            <InfoCard
              icon="♧"
              title={
                <>
                  16+ years
                  <br />
                  in the field
                </>
              }
              text="Practical, real-life insight to guide support that works."
            />

            <InfoCard
              icon="▣"
              title={
                <>
                  Neurodiversity and
                  <br />
                  Assistive Technology
                </>
              }
              text="Over 16 years of experience in the neurodiversity community with expertise in assistive technology solutions that make a real difference in daily life, education, employment and independence."
            />

            <InfoCard
              icon="♡"
              title={
                <>
                  Person-centred
                  <br />
                  approach
                </>
              }
              text="Support that looks at the whole person, including strengths, interests and individual needs, because everyone deserves to feel understood, valued and empowered."
            />

            <InfoCard
              icon="☆"
              title={
                <>
                  A meaningful impact
                </>
              }
              text="A long-standing passion for creating supportive environments where individuals can build on their strengths, develop practical skills and move forward with confidence."
            />

          </div>

        </div>

      </section>


      {/* SERVICEs */}

      <section className="services-section">

        <div className="services-decoration services-decoration-left" />
        <div className="services-decoration services-decoration-right" />

        <div className="section-container">

          <div className="section-label">
            <span>SERVICES &amp; COUNSELLING SUPPORTS</span>
            <i />
          </div>

          <h2>
            Support that can be shaped
            <br />
            around your needs.
          </h2>

          <p className="section-description">
            Explore counselling options, observe services and see check-in
            innovations to find the right fit for you.
          </p>


          <div className="services-grid">

            <ServiceItem
              title="Respite"
              icon="♧"
            />

            <ServiceItem
              title="Animal-Based DBT Skills Coaching"
              icon="♣"
            />

            <ServiceItem
              title="Tutoring & Assistive Technology (AT) for Daily Living"
              icon="▣"
            />

            <ServiceItem
              title="Counselling"
              icon="○"
            />

            <ServiceItem
              title="ADHD Education & Executive Coaching"
              icon="✥"
            />

            <ServiceItem
              title="Social Skills Support"
              icon="♧"
            />

          </div>

        </div>

      </section>


      {/* CASEY */}

      <section className="casey-section">

        <div className="casey-background-shape" />

        <div className="section-container casey-container">

          <div className="casey-image-wrapper">

            <img
              src="/images/casey-back-view.jpg"
              alt="Person looking toward a mountain landscape"
            />

          </div>


          <div className="casey-content">

            <div className="section-label">
              <span>ABOUT CASEY</span>
              <i />
            </div>

            <h2>Meet Casey</h2>

            <p>
              With over 16 years in the field and a deep personal connection
              to the neurodiversity community, Casey brings both professional
              expertise and lived experience to her work.
            </p>

            <p>
              As a mother to a neurodiverse child and someone who has
              navigated her own journey, she understands the unique
              challenges and strengths that come with different ways of
              thinking, learning and being.
            </p>

            <p>
              Casey's approach is warm, practical and collaborative. She
              believes in meeting each person where they are, building on
              their strengths and finding strategies that make everyday life
              more manageable and meaningful.
            </p>

            <Link to="/about" className="outline-button">
              More about Casey
              <span>→</span>
            </Link>

          </div>

        </div>

      </section>


      {/* WHY ESSENTIAL BUTTERFLY */}

      <section className="why-section">

        <div className="section-container">

          <div className="section-label">
            <span>WHY ESSENTIAL BUTTERFLY</span>
            <i />
          </div>

          <h2>
            Individual support, practical tools
            <br />
            and room to grow.
          </h2>

          <p className="section-description">
            Essential Butterfly is here to help you build the awareness and
            tools needed to navigate life's challenges and opportunities.
          </p>


          <div className="why-grid">

            <InfoCard
              icon="♧"
              title="Individualized support"
              text="Solutions that are responsive to your unique strengths, goals and circumstances."
            />

            <InfoCard
              icon="⚒"
              title="A practical toolbox"
              text="Strategies and assistive tools to support everyday life at home, in school, at work and in the community."
            />

            <InfoCard
              icon="▥"
              title="Progress with meaning"
              text="Ongoing encouragement and flexible support that evolves with your needs, helping you build confidence and independence over time."
            />

          </div>

        </div>

      </section>


      {/* BOOKING */}

      <section className="booking-section">

        <div className="booking-glow booking-glow-left" />
        <div className="booking-glow booking-glow-right" />

        <div className="section-container booking-container">

          <div className="booking-intro">

            <div className="section-label light">
              <span>READY TO TAKE THE NEXT STEP?</span>
              <i />
            </div>

            <h2>
              Let's check on how I'm available.
            </h2>

            <p>
              Appointments are designed to be flexible and supportive,
              with options to fit your schedule. Whether you're looking for
              ongoing counselling, a one-time consultation or support for
              specific goals, you can easily check current availability and
              request a time that works for you.
            </p>

          </div>


          <div className="booking-options">

            <BookingItem
              icon="▣"
              title="Book a time"
              text="View current appointment availability and request a time."
            />

            <BookingItem
              icon="♧"
              title="For new or existing clients"
              text="Choose an option that fits your needs, whether you're reaching out for the first time or continuing your support."
            />

            <BookingItem
              icon="▣"
              title="Easy and convenient"
              text="The booking process is simple, secure and accessible."
            />

            <BookingItem
              icon="○"
              title="Clear communication"
              text="You'll receive confirmation and next steps."
            />

          </div>

        </div>

      </section>


      {/* FINAL CTA */}

      <section className="final-cta">

        <img
          src="/images/cta-mountains.jpg"
          alt=""
          className="final-cta-image"
        />

        <div className="final-cta-overlay" />

        <div className="final-cta-content">

          <div className="section-label centered">
            <span>SUPPORT TO PLAN FOR A BRIGHTER TOMORROW</span>
            <i />
          </div>

          <h2>
            Explore what support could look like for you.
          </h2>

          <Link to="/intake" className="purple-button">
            Book a Time
            <span>→</span>
          </Link>

        </div>

      </section>

    </main>
  );
}


/* HERO FEATURE */

function HeroFeature({
  icon,
  title,
  subtitle,
}: {
  icon: string;
  title: string;
  subtitle: string;
}) {
  return (
    <div className="hero-feature">

      <span className="hero-feature-icon">
        {icon}
      </span>

      <span>
        {title}
        <br />
        {subtitle}
      </span>

    </div>
  );
}


/* INFO CARD */

function InfoCard({
  icon,
  title,
  text,
}: {
  icon: string;
  title: React.ReactNode;
  text: string;
}) {
  return (
    <article className="info-card">

      <div className="card-icon">
        {icon}
      </div>

      <h3>{title}</h3>

      <p>{text}</p>

    </article>
  );
}


/* SERVICE ITEM */

function ServiceItem({
  title,
  icon,
}: {
  title: string;
  icon: string;
}) {
  return (
    <Link to="/services" className="service-item">

      <span className="service-icon">
        {icon}
      </span>

      <span className="service-title">
        {title}
      </span>

      <span className="service-arrow">
        →
      </span>

    </Link>
  );
}


/* BOOKING ITEM */

function BookingItem({
  title,
  text,
  icon,
}: {
  title: string;
  text: string;
  icon: string;
}) {
  return (
    <div className="booking-item">

      <span className="booking-icon">
        {icon}
      </span>

      <div>
        <h3>{title}</h3>
        <p>{text}</p>
      </div>

    </div>
  );
}


export default Home;