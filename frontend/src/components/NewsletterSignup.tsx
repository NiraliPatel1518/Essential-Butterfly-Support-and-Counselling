import { useState } from "react";
import { apiUrl } from "../api/api";
import { usePublicFeatures } from "../context/PublicFeatureContext";
import "./NewsletterSignup.css";

export default function NewsletterSignup() {
  const { isEnabled } = usePublicFeatures();

  const [email, setEmail] = useState("");
  const [consent, setConsent] = useState(false);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  if (!isEnabled("NEWSLETTER")) return null;

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setMessage("");

    if (!consent) {
      setMessage("Please provide consent before subscribing.");
      return;
    }

    setBusy(true);

    try {
      const response = await fetch(apiUrl("/api/newsletter/subscribe"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, consent }),
      });

      const data = await response.json().catch(() => ({}));

      if (!response.ok) {
        throw new Error(data.message || "Unable to subscribe. Please try again.");
      }

      setMessage(data.message || "Thank you. Your subscription has been received.");
      setEmail("");
      setConsent(false);
    } catch (error) {
      setMessage(
        error instanceof Error
          ? error.message
          : "Something went wrong. Please try again."
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="newsletter-signup" aria-labelledby="newsletter-heading">
      <div className="newsletter-inner">
        <div>
          <p className="newsletter-eyebrow">Stay connected</p>
          <h2 id="newsletter-heading">News, updates &amp; helpful resources</h2>
          <p>
            Receive occasional updates and resources from Essential Butterfly
            Support &amp; Counselling.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="newsletter-form">
          <label htmlFor="newsletter-email">Email address</label>
          <input
            id="newsletter-email"
            type="email"
            autoComplete="email"
            required
            maxLength={254}
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="you@example.com"
          />

          <label className="newsletter-consent">
            <input
              type="checkbox"
              checked={consent}
              onChange={(event) => setConsent(event.target.checked)}
            />
            <span>
              I agree to receive newsletters by email. I understand I can
              unsubscribe at any time.
            </span>
          </label>

          <button type="submit" disabled={busy}>
            {busy ? "Submitting..." : "Subscribe"}
          </button>

          <p className="newsletter-message" role="status" aria-live="polite">
            {message}
          </p>
        </form>
      </div>
    </section>
  );
}