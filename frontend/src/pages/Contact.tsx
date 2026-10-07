import { useState } from "react";
import type { FormEvent } from "react";
import "./Contact.css";

function Contact() {
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    setSubmitted(false);
    setError("");
    setIsSubmitting(true);

    const form = event.currentTarget;
    const formData = new FormData(form);

    const requestData = {
      fullName: String(formData.get("fullName") || ""),
      email: String(formData.get("email") || ""),
      subject: String(formData.get("subject") || ""),
      message: String(formData.get("message") || ""),
    };

    try {
      const response = await fetch("http://localhost:8080/api/contact", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(requestData),
      });

      const responseText = await response.text();

      if (!response.ok) {
        throw new Error(
          responseText || "Unable to send your message."
        );
      }

      setSubmitted(true);
      form.reset();
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Unable to send your message. Please try again."
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <main className="contact-page">
      <div className="contact-decoration contact-decoration-left">
        <img src="/images/img_one.png" alt="" />
      </div>

      <div className="contact-decoration contact-decoration-right">
        <img src="/images/img_two.png" alt="" />
      </div>

      <div className="contact-background-shape contact-shape-one"></div>
      <div className="contact-background-shape contact-shape-two"></div>

      <section className="contact-hero">
        <div className="contact-intro">
          <span className="contact-eyebrow">GET IN TOUCH</span>

          <h1>
            We’d love to hear
            <br />
            from you.
          </h1>

          <p className="contact-intro-text">
            If you have a question, would like more information about
            services, or simply want to connect, please reach out. We’re
            here to support you.
          </p>

          <div className="contact-information">
            <div className="contact-information-item">
              <div className="contact-icon">
                <svg
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <rect x="3" y="5" width="18" height="14" rx="2" />
                  <path d="m3 7 9 6 9-6" />
                </svg>
              </div>

              <div>
                <h2>Email</h2>
                <a href="mailto:casey@essentialbutterfly.com">casey@essentialbutterfly.com</a>
              </div>
            </div>

            <div className="contact-information-item">
              <div className="contact-icon">
                <svg
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <path d="M22 16.92v3a2 2 0 0 1-2.18 2A19.79 19.79 0 0 1 3.08 5.18 2 2 0 0 1 5.08 3h3a2 2 0 0 1 2 1.72c.12.9.33 1.78.62 2.63a2 2 0 0 1-.45 2.11L9 10.73a16 16 0 0 0 4.27 4.27l1.27-1.27a2 2 0 0 1 2.11-.45c.85.29 1.73.5 2.63.62A2 2 0 0 1 22 16.92z" />
                </svg>
              </div>

              <div>
                <h2>Phone</h2>
                <a href="tel:+12092582002">(209) 258-0202</a>
              </div>
            </div>

            <div className="contact-information-item">
              <div className="contact-icon">
                <svg
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <path d="M20 10c0 5-8 12-8 12S4 15 4 10a8 8 0 1 1 16 0Z" />
                  <circle cx="12" cy="10" r="2.5" />
                </svg>
              </div>

              <div>
                <h2>Location</h2>
                <p>Oakville, Ontario</p>
              </div>
            </div>
          </div>
        </div>

        <div className="contact-form-card">
          <div className="contact-form-header">
            <h2>Send a Message</h2>

            <p>
              Fill out the form below and we’ll get back to you as soon as
              possible.
            </p>
          </div>

          <form className="contact-form" onSubmit={handleSubmit}>
            <div className="contact-form-row">
              <div className="contact-field">
                <label htmlFor="contact-full-name">
                  Full name <span>*</span>
                </label>

                <input
                  id="contact-full-name"
                  name="fullName"
                  type="text"
                  placeholder="Your full name"
                  autoComplete="name"
                  required
                />
              </div>

              <div className="contact-field">
                <label htmlFor="contact-email">
                  Email address <span>*</span>
                </label>

                <input
                  id="contact-email"
                  name="email"
                  type="email"
                  placeholder="you@example.com"
                  autoComplete="email"
                  inputMode="email"
                  required
                />
              </div>
            </div>

            <div className="contact-field contact-subject-field">
              <label htmlFor="contact-subject">
                Subject <span>*</span>
              </label>

              <div className="contact-select-wrapper">
                <select
                  id="contact-subject"
                  name="subject"
                  defaultValue=""
                  required
                >
                  <option value="" disabled>
                    Select a subject
                  </option>

                  <option value="general-inquiry">
                    General Inquiry
                  </option>

                  <option value="services">
                    Questions About Services
                  </option>

                  <option value="respite">
                    Respite Support
                  </option>

                  <option value="counselling">
                    Counselling
                  </option>

                  <option value="coaching">
                    Coaching
                  </option>

                  <option value="other">
                    Other
                  </option>
                </select>

                <span
                  className="contact-select-arrow"
                  aria-hidden="true"
                >
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2.5"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  >
                    <path d="m6 9 6 6 6-6" />
                  </svg>
                </span>
              </div>
            </div>

            <div className="contact-field">
              <label htmlFor="contact-message">
                Message <span>*</span>
              </label>

              <textarea
                id="contact-message"
                name="message"
                placeholder="Write your message here..."
                rows={5}
                required
              ></textarea>
            </div>

            {submitted && (
              <p className="contact-success" role="status" aria-live="polite">
                Thank you. Your message has been received.
              </p>
            )}

            {error && (
              <p className="contact-error" role="alert">
                {error}
              </p>
            )}

            <button
              type="submit"
              className="contact-submit-button"
              disabled={isSubmitting}
              aria-disabled={isSubmitting}
            >
              {isSubmitting ? "Sending..." : "Send Message"}
            </button>
          </form>
        </div>
      </section>
    </main>
  );
}

export default Contact;
