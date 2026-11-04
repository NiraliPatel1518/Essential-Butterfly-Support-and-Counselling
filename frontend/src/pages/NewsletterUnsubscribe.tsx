import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { apiUrl } from "../api/api";

export default function NewsletterUnsubscribe() {
  const [params] = useSearchParams();
  const [message, setMessage] = useState("Processing your unsubscribe request...");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = params.get("token");

    if (!token) {
      setMessage("The unsubscribe link is invalid or incomplete.");
      setLoading(false);
      return;
    }

    async function unsubscribe() {
      try {
        const response = await fetch(
          apiUrl(`/api/newsletter/unsubscribe?token=${encodeURIComponent(token!)}`)
        );

        const data = await response.json().catch(() => ({}));

        if (!response.ok) {
          throw new Error(data.message || "Unable to process your request.");
        }

        setMessage(data.message || "You have been unsubscribed successfully.");
      } catch (error) {
        setMessage(
          error instanceof Error
            ? error.message
            : "Unable to process your request."
        );
      } finally {
        setLoading(false);
      }
    }

    void unsubscribe();
  }, [params]);

  return (
    <main className="information-page">
      <section className="information-card" aria-labelledby="unsubscribe-heading">
        <h1 id="unsubscribe-heading">Newsletter preferences</h1>
        <p role="status" aria-live="polite">
          {loading ? "Please wait..." : message}
        </p>
        <Link to="/">Return to Essential Butterfly</Link>
      </section>
    </main>
  );
}